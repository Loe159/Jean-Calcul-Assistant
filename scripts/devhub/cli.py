#!/usr/bin/env python3
"""Publish Jean-Calcul Preview builds and collect owner feedback."""

import argparse
import base64
from collections import deque
from datetime import UTC, datetime
import http.client
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.parse import quote, urlencode, urlsplit

# The wrapper uses Python's isolated mode, which omits this directory from sys.path.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from devhub import (
    APK_LIMIT, APIError, IMAGE_LIMIT, PNG_SIGNATURE, PORT, atomic_json, atomic_write,
    data_dir, format_age, hash_stream, iso_utc, read_json, safe_name,
)


SERVER_SCRIPT = Path(__file__).resolve().with_name("devhub.py")
UNIT = "jean-calcul-devhub.service"
START_HINT = "Run scripts/jc-preview service install or scripts/jc-preview serve."


class CLIError(Exception):
    pass


class HubUnavailable(CLIError):
    pass


class Client:
    def __init__(self):
        self.url = urlsplit(os.environ.get("JC_DEVHUB_URL") or f"http://127.0.0.1:{PORT}")
        if self.url.scheme not in ("http", "https") or not self.url.hostname:
            raise CLIError("JC_DEVHUB_URL must be an HTTP or HTTPS URL")
        self.token = None

    def request(self, method, path, *, payload=None, upload=None, wait=0, authenticated=True):
        headers = {}
        if authenticated:
            if self.token is None:
                try:
                    self.token = (data_dir() / "token").read_text(encoding="ascii").strip()
                except FileNotFoundError:
                    raise HubUnavailable(f"Devhub token is missing. {START_HINT}") from None
            headers["Authorization"] = f"Bearer {self.token}"
        body = None
        if payload is not None:
            body = json.dumps(payload).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if upload is not None:
            body, size = upload
            headers.update({"Content-Type": "application/octet-stream", "Content-Length": str(size)})
        cls = http.client.HTTPSConnection if self.url.scheme == "https" else http.client.HTTPConnection
        connection = cls(self.url.hostname, self.url.port, timeout=max(30, wait + 10))
        try:
            connection.request(method, self.url.path.rstrip("/") + "/api/v1" + path,
                               body=body, headers=headers)
            response = connection.getresponse()
            contents = response.read()
        except (OSError, http.client.HTTPException) as error:
            raise HubUnavailable(f"Devhub is unreachable ({error}). {START_HINT}") from None
        finally:
            connection.close()
        try:
            result = json.loads(contents)
        except ValueError:
            raise CLIError(f"Devhub returned HTTP {response.status} without JSON") from None
        if not isinstance(result, dict):
            raise CLIError(f"Devhub returned an invalid response (HTTP {response.status})")
        if not 200 <= response.status < 300:
            message = result.get("error", "Request failed")
            raise CLIError(f"Devhub returned HTTP {response.status}: {message}")
        return result


def git(*args, cwd=None):
    result = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True,
                            encoding="utf-8", errors="replace")
    if result.returncode:
        raise CLIError(result.stderr.strip() or f"git {args[0]} failed")
    return result.stdout.rstrip("\n")


def current_branch(cwd=None, commit=None):
    branch = git("branch", "--show-current", cwd=cwd)
    if branch:
        return branch
    commit = commit or git("rev-parse", "HEAD", cwd=cwd)
    return f"detached-{commit[:7]}"


def public_url():
    configured = os.environ.get("JC_DEVHUB_PUBLIC_URL")
    if configured:
        return configured
    try:
        output = subprocess.run(["tailscale", "status", "--json"], capture_output=True,
                                check=True, timeout=5)
        name = json.loads(output.stdout)["Self"]["DNSName"].strip().rstrip(".")
        if name:
            return f"https://{name}:{PORT}"
    except (OSError, subprocess.SubprocessError, ValueError, KeyError, TypeError, AttributeError):
        pass
    print("Warning: no public devhub URL. Set JC_DEVHUB_PUBLIC_URL or configure Tailscale.",
          file=sys.stderr)
    return ""


def run_gradle(root, hub_url):
    command = [
        # Both modes run :app:assembleDebug; separate task history stops Gradle from
        # deleting the other mode's outputs when switching.
        "./gradlew", "--project-cache-dir", ".gradle/dev-preview",
        "-Pjeancalcul.devPreview=true", f"-Pjeancalcul.devHubUrl={hub_url}",
        ":app:assembleDebug",
    ]
    with subprocess.Popen(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          text=True, encoding="utf-8", errors="replace") as process:
        tail = deque(maxlen=40)
        for line in process.stdout:
            print(line, end="", flush=True)
            tail.append(line)
        if process.wait():
            print("\nLast Gradle output:", file=sys.stderr)
            print("".join(tail), end="", file=sys.stderr)
            raise CLIError(f"Gradle failed with exit code {process.returncode}")


def version_name(folder):
    try:
        value = read_json(folder / "output-metadata.json")["elements"][0]["versionName"]
    except (OSError, ValueError, TypeError, KeyError, IndexError):
        return ""
    return value if isinstance(value, str) else ""


def push(args):
    started = time.monotonic()
    root = Path(git("rev-parse", "--show-toplevel"))
    commit = git("rev-parse", "HEAD", cwd=root)
    branch = current_branch(root, commit)
    now = datetime.now(UTC)
    build_id = f"{now:%Y%m%d-%H%M%S}-{commit[:6]}"
    message = args.message if args.message is not None else git("log", "-1", "--format=%s", cwd=root)
    client = Client()
    client.request("GET", "/state")
    hub_url = public_url()
    metadata = {
        "buildId": build_id, "branch": branch, "worktree": root.name, "commit": commit,
        "dirty": bool(git("status", "--porcelain", cwd=root)), "message": message,
        "builtAt": iso_utc(now), "hubUrl": hub_url,
    }
    folder = root / "app" / "build-devpreview"
    asset_dir = folder / "dev-build-assets"
    asset_dir.mkdir(parents=True, exist_ok=True)
    atomic_json(asset_dir / "dev-build.json", metadata)
    if not args.skip_build:
        run_gradle(root, hub_url)
    apk_dir = folder / "outputs" / "apk" / "debug"
    apk_path = apk_dir / "JeanCalculPreview.apk"
    if not apk_path.is_file():
        raise CLIError(f"APK missing: {apk_path}")
    with apk_path.open("rb") as apk:
        if os.fstat(apk.fileno()).st_size > APK_LIMIT:
            raise CLIError("APK exceeds the 250 MB upload limit")
        sha256, size = hash_stream(apk)
        apk.seek(0)
        client.request("PUT", f"/builds/{build_id}/apk", upload=(apk, size))
    client.request("POST", "/builds", payload={
        "id": build_id, "branch": branch, "worktree": root.name, "commit": commit,
        "dirty": metadata["dirty"], "message": message, "versionName": version_name(apk_dir),
        "sha256": sha256, "createdAt": metadata["builtAt"],
    })
    print(f"Published {build_id} on {branch} in {time.monotonic() - started:.1f}s"
          " — open Preview Hub on the phone", flush=True)
    if args.ask is not None:
        return submit_review(client, args, args.ask, branch, build_id)
    return 0


def parse_option(value):
    option_id, equals, rest = value.partition("=")
    if not equals:
        raise argparse.ArgumentTypeError("Expected ID=LABEL[:image.png][@FQN]")
    try:
        safe_name(option_id, "option id")
    except APIError as error:
        raise argparse.ArgumentTypeError(str(error)) from None
    label, at, preview = rest.rpartition("@")
    if not at:
        label, preview = rest, None
    elif not preview.strip():
        raise argparse.ArgumentTypeError("Expected a preview FQN after @")
    image = None
    if label.lower().endswith(".png") and ":" in label:
        label, image = label.rsplit(":", 1)
    if not label.strip() or (image is not None and not image.strip()):
        raise argparse.ArgumentTypeError("Expected an option label and optional PNG path")
    return option_id, label, image, preview


def review_payload(args, title, branch, build_id):
    attachments = {}
    names = set()

    def attach(path):
        path = Path(path).resolve()
        if path in attachments:
            return attachments[path]
        with path.open("rb") as stream:
            data = stream.read(IMAGE_LIMIT + 1)
        if len(data) > IMAGE_LIMIT:
            raise CLIError(f"Image exceeds the 8 MB upload limit: {path}")
        if not data.startswith(PNG_SIGNATURE):
            raise CLIError(f"Image must be a PNG: {path}")
        stem = re.sub(r"[^A-Za-z0-9._-]+", "-", path.stem).strip("._-") or "image"
        name = stem[:92] + ".png"
        index = 2
        while name in names:
            suffix = f"-{index}.png"
            name = stem[:96 - len(suffix)] + suffix
            index += 1
        names.add(name)
        encoded = {"name": name, "data": base64.b64encode(data).decode("ascii")}
        attachments[path] = encoded
        return encoded

    images = [attach(path) for path in args.image]
    options = [
        {"id": option_id, "label": label, "image": attach(path) if path else None,
         "previewFqn": preview}
        for option_id, label, path, preview in args.option
    ]
    return {
        "title": title, "branch": branch, "buildId": build_id, "body": args.body,
        "previewFqn": args.preview, "images": images, "options": options,
    }


def submit_review(client, args, title, branch, build_id):
    review = client.request("POST", "/reviews", payload=review_payload(args, title, branch, build_id))
    print(f'Review {review["id"]} — open Preview Hub on the phone', flush=True)
    return wait_for_review(client, review["id"], args.timeout) if args.wait else 0


def ask(args):
    client = Client()
    branch = args.branch if args.branch is not None else current_branch()
    build_id = args.build
    if build_id is None and not args.no_build:
        state = client.request("GET", "/state")
        for entry in state["branches"]:
            if entry["branch"] == branch and entry["builds"]:
                build_id = entry["builds"][0]["id"]
                break
    return submit_review(client, args, args.title, branch, build_id)


def wait_for_review(client, review_id, timeout):
    deadline = time.monotonic() + timeout
    path = f"/reviews/{quote(review_id, safe='')}"
    while True:
        wait = max(0, min(60, deadline - time.monotonic()))
        review = client.request("GET", f"{path}?{urlencode({'wait': wait})}", wait=wait)
        if review["status"] == "cancelled":
            print("CANCELLED")
            return 5
        if review["status"] == "answered":
            answer = review["answer"]
            verdict = answer["verdict"]
            if verdict == "choice":
                selected = next(option for option in review["options"] if option["id"] == answer["choice"])
                print(f'CHOICE {selected["id"]}: {selected["label"]}')
            elif verdict == "changes":
                print("CHANGES REQUESTED")
            else:
                print("APPROVED")
            if answer["comment"]:
                print(answer["comment"])
            return 3 if verdict == "changes" else 0
        if time.monotonic() >= deadline:
            print("TIMEOUT")
            return 4


def status():
    client = Client()
    try:
        health = client.request("GET", "/health", authenticated=False)
    except HubUnavailable as error:
        print(f"Devhub down — {error}")
        return 1
    print(f'Devhub up (seq {health["seq"]})', flush=True)
    state = client.request("GET", "/state")
    print("Latest builds")
    for branch in state["branches"]:
        build = branch["builds"][0]
        working_tree = "dirty" if build["dirty"] else "clean"
        print(f'  {branch["branch"]}  {build["id"]}  {format_age(build["createdAt"])} ago'
              f'  {working_tree}  {build["message"]}')
    print("Open reviews")
    for review in state["reviews"]:
        if review["status"] == "open":
            print(f'  {review["id"]}  {review["branch"]}  {review["title"]}')
    return 0


def systemd_arg(value):
    # Escape for systemd's single-argument syntax and its ExecStart expansion.
    value = str(value).replace("%", "%%").replace("$", "$$")
    if re.fullmatch(r"[-A-Za-z0-9._/%$#]+", value):
        return value
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n") + '"'


def service(action):
    systemctl = [os.environ.get("JC_DEVHUB_SYSTEMCTL", "systemctl"), "--user"]
    unit_dir = Path.home() / ".config" / "systemd" / "user"
    unit_path = unit_dir / UNIT
    if action == "install":
        data = data_dir().absolute()
        script = data / "bin" / "devhub.py"
        script.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        atomic_write(script, SERVER_SCRIPT.read_bytes())
        nix = systemd_arg(os.environ.get("JC_DEVHUB_NIX", "/run/current-system/sw/bin/nix"))
        if os.environ.get("XDG_DATA_HOME"):
            script_args = f"{systemd_arg(script)} serve --data {systemd_arg(data)}"
        else:
            script_args = f"%h/.local/share/{data.name}/bin/devhub.py serve"
        unit = (
            "[Unit]\nDescription=Jean-Calcul Preview devhub\n\n"
            "[Service]\n"
            f"ExecStart={nix} shell nixpkgs#python3 --command python3 {script_args}\n"
            "Restart=on-failure\n\n[Install]\nWantedBy=default.target\n"
        )
        unit_dir.mkdir(parents=True, exist_ok=True)
        atomic_write(unit_path, unit.encode("utf-8"))
        subprocess.run([*systemctl, "daemon-reload"], check=True)
        running = subprocess.run([*systemctl, "is-active", "--quiet", UNIT]).returncode == 0
        activate = ["restart", UNIT] if running else ["enable", "--now", UNIT]
        subprocess.run([*systemctl, *activate], check=True)
        print("Installed devhub. Run loginctl enable-linger $USER to start it without logging in.")
        print("Enable the jean-calcul-devhub Tailscale Serve entry on HTTPS port 4120.")
        return 0
    if action == "logs":
        journalctl = os.environ.get("JC_DEVHUB_JOURNALCTL", "journalctl")
        return subprocess.run([journalctl, "--user", "-u", UNIT, "-n", "100",
                               "--no-pager"]).returncode
    if action == "uninstall":
        if unit_path.exists():
            subprocess.run([*systemctl, "disable", "--now", UNIT], check=True)
            unit_path.unlink()
            subprocess.run([*systemctl, "daemon-reload"], check=True)
        return 0
    return subprocess.run([*systemctl, action, UNIT]).returncode


def timeout_value(value):
    try:
        seconds = float(value)
    except ValueError:
        raise argparse.ArgumentTypeError("Expected a timeout in seconds") from None
    if not math.isfinite(seconds) or seconds < 0:
        raise argparse.ArgumentTypeError("Expected a finite, nonnegative timeout")
    return seconds


def cli_id(value):
    try:
        return safe_name(value, "id")
    except APIError as error:
        raise argparse.ArgumentTypeError(str(error)) from None


def review_flags(parser):
    parser.add_argument("--body", default="")
    parser.add_argument("--image", action="append", default=[])
    parser.add_argument("--option", action="append", type=parse_option, default=[])
    parser.add_argument("--preview", metavar="FQN")
    parser.add_argument("--wait", action="store_true")
    parser.add_argument("--timeout", type=timeout_value, default=1800)


def main(argv=None):
    parser = argparse.ArgumentParser(prog="jc-preview", description=__doc__, allow_abbrev=False)
    commands = parser.add_subparsers(dest="command", required=True)
    serve_command = commands.add_parser("serve", allow_abbrev=False)
    serve_command.add_argument("--port", type=int)
    serve_command.add_argument("--data")
    service_command = commands.add_parser("service", allow_abbrev=False)
    service_command.add_argument("action", choices=("install", "status", "logs", "restart", "uninstall"))
    push_command = commands.add_parser("push", allow_abbrev=False)
    push_command.add_argument("-m", "--message")
    push_command.add_argument("--skip-build", action="store_true")
    push_command.add_argument("--ask", metavar="TITLE")
    review_flags(push_command)
    ask_command = commands.add_parser("ask", allow_abbrev=False)
    ask_command.add_argument("title", metavar="TITLE")
    review_flags(ask_command)
    build = ask_command.add_mutually_exclusive_group()
    build.add_argument("--build", type=cli_id, metavar="ID")
    build.add_argument("--latest", action="store_true", help="use the latest build on the branch (default)")
    build.add_argument("--no-build", action="store_true")
    ask_command.add_argument("--branch")
    wait_command = commands.add_parser("wait", allow_abbrev=False)
    wait_command.add_argument("id", type=cli_id)
    wait_command.add_argument("--timeout", type=timeout_value, default=1800)
    commands.add_parser("status", allow_abbrev=False)
    commands.add_parser("cancel", allow_abbrev=False).add_argument("id", type=cli_id)
    args = parser.parse_args(argv)
    try:
        match args.command:
            case "serve":
                command = [sys.executable, "-I", str(SERVER_SCRIPT), "serve"]
                if args.port is not None:
                    command.extend(["--port", str(args.port)])
                if args.data is not None:
                    command.extend(["--data", args.data])
                os.execv(sys.executable, command)
            case "service":
                return service(args.action)
            case "push":
                if args.ask is None and (
                    args.wait or args.body or args.image or args.option or args.preview
                    or args.timeout != 1800
                ):
                    push_command.error("review flags require --ask TITLE")
                return push(args)
            case "ask":
                return ask(args)
            case "wait":
                return wait_for_review(Client(), args.id, args.timeout)
            case "status":
                return status()
            case "cancel":
                Client().request("POST", f"/reviews/{quote(args.id, safe='')}/cancel")
                print(f"Cancelled {args.id}")
                return 0
    except (CLIError, OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"jc-preview: {error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
