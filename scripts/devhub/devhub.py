#!/usr/bin/env python3
"""Loopback HTTP server for Jean-Calcul Preview."""

import argparse
import base64
import binascii
from collections import deque
from datetime import UTC, datetime, timedelta
import hashlib
from html import escape
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import math
import os
from pathlib import Path
import re
import secrets
import shutil
import sys
import tempfile
import threading
import time
from urllib.parse import parse_qs, unquote


PORT = 4120
DATA_NAME = "jean-calcul-devhub"
APK_LIMIT = 250 * 1024 * 1024
IMAGE_LIMIT = 8 * 1024 * 1024
JSON_LIMIT = 30 * 1024 * 1024
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
SAFE_NAME = re.compile(r"[A-Za-z0-9._-]{1,96}")
APK_TYPE = "application/vnd.android.package-archive"


def data_dir():
    base = os.environ.get("XDG_DATA_HOME") or Path.home() / ".local" / "share"
    return Path(base).expanduser() / DATA_NAME


def iso_utc(value):
    return value.isoformat().replace("+00:00", "Z")


def parse_time(value):
    when = datetime.fromisoformat(value)
    if when.tzinfo is None:
        raise ValueError("missing timezone")
    return when.astimezone(UTC)


def format_age(value, now=None):
    seconds = max(0, int(((now or datetime.now(UTC)) - parse_time(value)).total_seconds()))
    for unit, duration in (("d", 86400), ("h", 3600), ("m", 60), ("s", 1)):
        if seconds >= duration:
            return f"{seconds // duration}{unit}"
    return "0s"


def atomic_write(path, data):
    fd, temporary = tempfile.mkstemp(dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(data)
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def atomic_json(path, value):
    atomic_write(path, (json.dumps(value, ensure_ascii=True) + "\n").encode("utf-8"))


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def load_token(root):
    path = root / "token"
    if not path.exists():
        fd, temporary = tempfile.mkstemp(dir=root)
        try:
            with os.fdopen(fd, "w", encoding="ascii") as stream:
                os.fchmod(stream.fileno(), 0o600)
                stream.write(secrets.token_urlsafe(32) + "\n")
            try:
                os.link(temporary, path)
            except FileExistsError:
                pass
        finally:
            Path(temporary).unlink()
    token = path.read_text(encoding="ascii").strip()
    if not token:
        raise ValueError("Devhub token file is empty")
    return token


class APIError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status


def safe_name(value, field):
    if not isinstance(value, str) or not SAFE_NAME.fullmatch(value) or value in (".", ".."):
        raise APIError(400, f"Invalid {field}")
    return value


def optional_id(value, field):
    return None if value is None else safe_name(value, field)


def string_field(payload, key, default=None, *, nonempty=False):
    value = payload.get(key, default)
    if not isinstance(value, str) or (nonempty and not value.strip()):
        raise APIError(400, f"Invalid {key}")
    return value


def optional_string(payload, key):
    return None if payload.get(key) is None else string_field(payload, key)


def list_field(payload, key):
    value = payload.get(key, [])
    if not isinstance(value, list):
        raise APIError(400, f"Invalid {key}")
    return value


def build_metadata(payload):
    build = {
        "id": safe_name(payload.get("id"), "build id"),
        "branch": string_field(payload, "branch", nonempty=True),
        "worktree": string_field(payload, "worktree", nonempty=True),
        "commit": string_field(payload, "commit"),
        "dirty": payload.get("dirty"),
        "message": string_field(payload, "message"),
        "versionName": string_field(payload, "versionName"),
        "sha256": string_field(payload, "sha256").lower(),
        "createdAt": string_field(payload, "createdAt"),
    }
    if not isinstance(build["dirty"], bool):
        raise APIError(400, "Invalid dirty")
    if not re.fullmatch(r"[0-9A-Fa-f]{40}", build["commit"]):
        raise APIError(400, "Invalid commit")
    if not re.fullmatch(r"[0-9a-f]{64}", build["sha256"]):
        raise APIError(400, "Invalid sha256")
    try:
        parse_time(build["createdAt"])
    except (ValueError, OverflowError):
        raise APIError(400, "Invalid createdAt") from None
    return build


def read_image(payload, images):
    if not isinstance(payload, dict):
        raise APIError(400, "Invalid image")
    name = safe_name(payload.get("name"), "image name")
    if name == "review.json":
        raise APIError(400, "Reserved image name")
    encoded = string_field(payload, "data")
    if len(encoded) > 4 * ((IMAGE_LIMIT + 2) // 3):
        raise APIError(413, "Image exceeds size limit")
    try:
        data = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error):
        raise APIError(400, "Invalid image base64") from None
    if len(data) > IMAGE_LIMIT:
        raise APIError(413, "Image exceeds size limit")
    if not data.startswith(PNG_SIGNATURE):
        raise APIError(400, "Image must be a PNG")
    if name in images and images[name] != data:
        raise APIError(400, "Conflicting image name")
    images[name] = data
    return name


def review_metadata(payload):
    review = {
        "branch": string_field(payload, "branch", nonempty=True),
        "buildId": optional_id(payload.get("buildId"), "build id"),
        "title": string_field(payload, "title", nonempty=True),
        "body": string_field(payload, "body", ""),
        "previewFqn": optional_string(payload, "previewFqn"),
        "status": "open",
        "answer": None,
    }
    images = {}
    review["images"] = [read_image(image, images) for image in list_field(payload, "images")]
    options = {}
    for option in list_field(payload, "options"):
        if not isinstance(option, dict):
            raise APIError(400, "Invalid option")
        option_id = safe_name(option.get("id"), "option id")
        if option_id in options:
            raise APIError(400, "Duplicate option id")
        image = option.get("image")
        options[option_id] = {
            "id": option_id,
            "label": string_field(option, "label", nonempty=True),
            "image": read_image(image, images) if image is not None else None,
            "previewFqn": optional_string(option, "previewFqn"),
        }
    review["options"] = list(options.values())
    return review, images


def hash_stream(source, destination=None, length=None):
    digest = hashlib.sha256()
    size = 0
    while length is None or size < length:
        chunk_size = 1024 * 1024 if length is None else min(1024 * 1024, length - size)
        chunk = source.read(chunk_size)
        if not chunk:
            if length is not None:
                raise APIError(400, "Incomplete upload")
            break
        digest.update(chunk)
        size += len(chunk)
        if destination is not None:
            destination.write(chunk)
    return digest.hexdigest(), size


class Store:
    def __init__(self, root):
        self.root = Path(root)
        self.root.mkdir(mode=0o700, parents=True, exist_ok=True)
        self.token = load_token(self.root)
        self.builds_dir = self.root / "builds"
        self.reviews_dir = self.root / "reviews"
        self.builds_dir.mkdir(exist_ok=True)
        self.reviews_dir.mkdir(exist_ok=True)
        self.state_path = self.root / "state.json"
        if not self.state_path.exists():
            atomic_json(self.state_path, {"seq": 0})
        self.seq = read_json(self.state_path)["seq"]
        self.condition = threading.Condition(threading.Lock())
        self.events = deque(maxlen=500)
        self.pending = {}
        self.builds = self.load_records(self.builds_dir, "build.json")
        self.reviews = self.load_records(self.reviews_dir, "review.json")
        self.prune()

    @staticmethod
    def load_records(directory, filename):
        records = {}
        for folder in directory.iterdir():
            if not folder.is_dir() or not SAFE_NAME.fullmatch(folder.name):
                continue
            metadata = folder / filename
            if metadata.is_file():
                records[folder.name] = read_json(metadata)
        return records

    def branches(self):
        by_branch = {}
        ordered = sorted(
            self.builds.values(), key=lambda build: (parse_time(build["createdAt"]), build["id"]),
            reverse=True,
        )
        for build in ordered:
            by_branch.setdefault(build["branch"], []).append(build)
        return by_branch

    def prune(self):
        now = datetime.now(UTC)
        for builds in self.branches().values():
            expired = parse_time(builds[0]["createdAt"]) < now - timedelta(days=14)
            for build in (builds if expired else builds[5:]):
                shutil.rmtree(self.builds_dir / build["id"])
                del self.builds[build["id"]]
        for review_id, review in list(self.reviews.items()):
            if review["status"] == "open":
                continue
            terminal_at = (
                review["answer"]["answeredAt"] if review["status"] == "answered"
                else review.get("cancelledAt", review["createdAt"])
            )
            if parse_time(terminal_at) < now - timedelta(days=30):
                shutil.rmtree(self.reviews_dir / review_id)
                del self.reviews[review_id]

    @staticmethod
    def public_review(review):
        return {key: value for key, value in review.items() if key != "cancelledAt"}

    def reserve_seq(self):
        # Persist the cursor before committing so a restart can resync even after a crash.
        next_seq = self.seq + 1
        atomic_json(self.state_path, {"seq": next_seq})
        self.seq = next_seq

    def emit(self, kind, record):
        self.events.append({
            "seq": self.seq, "type": kind, "id": record["id"], "branch": record["branch"],
        })
        self.condition.notify_all()

    def current_seq(self):
        with self.condition:
            return self.seq

    def state(self):
        with self.condition:
            branches = [
                {"branch": name, "worktree": builds[0]["worktree"], "builds": builds[:5]}
                for name, builds in self.branches().items()
            ]
            reviews = sorted(
                self.reviews.values(),
                key=lambda review: (review["status"] == "open", parse_time(review["createdAt"]),
                                    review["id"]),
                reverse=True,
            )
            return {
                "seq": self.seq, "branches": branches,
                "reviews": [self.public_review(review) for review in reviews[:30]],
            }

    def poll_events(self, since, wait):
        deadline = time.monotonic() + wait
        with self.condition:
            while True:
                oldest_cursor = self.events[0]["seq"] - 1 if self.events else self.seq
                if since > self.seq or since < oldest_cursor:
                    return {"seq": self.seq, "events": [], "resync": True}
                events = [event for event in self.events if event["seq"] > since]
                remaining = deadline - time.monotonic()
                if events or remaining <= 0:
                    return {"seq": self.seq, "events": events}
                self.condition.wait(remaining)

    def upload(self, build_id, source, length):
        with self.condition:
            if build_id in self.builds:
                raise APIError(409, "Build already published")
        fd, temporary = tempfile.mkstemp(dir=self.root)
        try:
            with os.fdopen(fd, "wb") as stream:
                digest = hash_stream(source, stream, length)
            with self.condition:
                if build_id in self.builds:
                    raise APIError(409, "Build already published")
                folder = self.builds_dir / build_id
                folder.mkdir(exist_ok=True)
                os.replace(temporary, folder / "pending.apk")
                self.pending[build_id] = digest
        finally:
            Path(temporary).unlink(missing_ok=True)

    def publish(self, payload):
        build = build_metadata(payload)
        build_id = build["id"]
        with self.condition:
            if build_id in self.builds:
                raise APIError(409, "Build already published")
            folder = self.builds_dir / build_id
            pending = folder / "pending.apk"
            apk = folder / "app.apk"
            if not pending.is_file():
                if not apk.is_file():
                    raise APIError(409, "Upload the pending APK first")
                # Recover a publish interrupted between the APK rename and metadata write.
                os.replace(apk, pending)
            digest = self.pending.get(build_id)
            if digest is None:
                with pending.open("rb") as stream:
                    digest = hash_stream(stream)
                self.pending[build_id] = digest
            sha256, build["size"] = digest
            if not secrets.compare_digest(sha256, build["sha256"]):
                raise APIError(409, "APK SHA-256 mismatch")
            self.reserve_seq()
            os.replace(pending, apk)
            try:
                atomic_json(folder / "build.json", build)
            except OSError:
                os.replace(apk, pending)
                raise
            self.pending.pop(build_id)
            self.builds[build_id] = build
            self.emit("build", build)
            self.prune()
            return build

    def open_apk(self, build_id):
        with self.condition:
            build = self.builds.get(build_id)
            if build is None:
                raise APIError(404, "Build not found")
            return (self.builds_dir / build_id / "app.apk").open("rb"), build

    def create_review(self, payload):
        review, images = review_metadata(payload)
        with self.condition:
            now = datetime.now(UTC)
            while True:
                review_id = f"r-{now:%Y%m%d-%H%M%S}-{secrets.token_hex(2)}"
                folder = self.reviews_dir / review_id
                try:
                    folder.mkdir()
                    break
                except FileExistsError:
                    continue
            review.update(id=review_id, createdAt=iso_utc(now))
            try:
                for name, data in images.items():
                    (folder / name).write_bytes(data)
                self.reserve_seq()
                atomic_json(folder / "review.json", review)
            except OSError:
                shutil.rmtree(folder)
                raise
            self.reviews[review_id] = review
            self.emit("review", review)
            return dict(review)

    def find_review(self, review_id):
        review = self.reviews.get(review_id)
        if review is None:
            raise APIError(404, "Review not found")
        return review

    def get_review(self, review_id, wait):
        def finished():
            return self.find_review(review_id)["status"] != "open"

        with self.condition:
            self.condition.wait_for(finished, wait)
            return self.public_review(self.find_review(review_id))

    def open_image(self, review_id, name):
        with self.condition:
            review = self.find_review(review_id)
            if name not in review["images"] and not any(
                option["image"] == name for option in review["options"]
            ):
                raise APIError(404, "Image not found")
            return (self.reviews_dir / review_id / name).open("rb")

    def close_review(self, review_id, kind, payload=None):
        with self.condition:
            review = self.find_review(review_id)
            if review["status"] != "open":
                raise APIError(409, "Review is already closed")
            now = iso_utc(datetime.now(UTC))
            updated = dict(review)
            if kind == "answer":
                verdict = payload.get("verdict")
                if verdict not in ("approved", "changes", "choice"):
                    raise APIError(400, "Invalid verdict")
                choice = optional_id(payload.get("choice"), "choice")
                if verdict == "choice":
                    if not any(option["id"] == choice for option in review["options"]):
                        raise APIError(400, "Choice must be an existing option id")
                elif choice is not None:
                    raise APIError(400, "Choice is only allowed with the choice verdict")
                comment = string_field(payload, "comment", "")
                if len(comment) > 4000:
                    raise APIError(400, "Comment exceeds 4000 characters")
                updated.update(status="answered", answer={
                    "verdict": verdict, "choice": choice, "comment": comment, "answeredAt": now,
                    "installedBuildId": optional_id(payload.get("installedBuildId"), "build id"),
                })
            else:
                updated.update(status="cancelled", cancelledAt=now)
            self.reserve_seq()
            atomic_json(self.reviews_dir / review_id / "review.json", updated)
            self.reviews[review_id] = updated
            self.emit(kind, updated)
            return self.public_review(updated)


def slug(branch):
    return re.sub(r"[^A-Za-z0-9._-]+", "-", branch).strip("._-")[:96] or "branch"


def landing_page(branches):
    rows = []
    now = datetime.now(UTC)
    for branch in branches:
        build = branch["builds"][0]
        rows.append(
            f'<li><h2>{escape(build["branch"])}</h2><p>{escape(build["message"])}</p>'
            f'<p><code>{escape(build["commit"][:7])}</code> · '
            f'{format_age(build["createdAt"], now)} · {build["size"] / (1024 * 1024):.1f} Mio</p>'
            f'<p><a href="/api/v1/builds/{escape(build["id"])}/apk">Télécharger l’APK</a></p></li>'
        )
    contents = f'<ul>{"".join(rows)}</ul>' if rows else "<p>Aucun build disponible.</p>"
    return (
        '<!doctype html><html lang="fr"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width, initial-scale=1">'
        '<title>Jean-Calcul Preview</title><style>'
        'html{color-scheme:light dark}body{font:1rem/1.5 system-ui;margin:2rem auto;'
        'max-width:44rem;padding:0 1rem;overflow-wrap:anywhere}ul{list-style:none;padding:0}'
        'li{border-block-start:1px solid GrayText;padding:.75rem 0}h2,p{margin:.5rem 0}'
        'a{display:inline-block;padding:.65rem 0}</style></head><body>'
        f'<main><h1>Jean-Calcul Preview</h1>{contents}</main></body></html>'
    ).encode("utf-8")


class Handler(BaseHTTPRequestHandler):
    def handle_one_request(self):
        start = time.monotonic()
        self.command = self.path = None
        self.status = None
        try:
            super().handle_one_request()
        except (ConnectionError, TimeoutError):
            self.close_connection = True
        finally:
            if self.status is not None:
                method = (self.command or "-").replace(self.server.store.token, "[redacted]")
                path = unquote((self.path or "-").partition("?")[0])
                path = path.replace(self.server.store.token, "[redacted]")
                elapsed = round((time.monotonic() - start) * 1000)
                print(f"{ascii(method)[1:-1]} {ascii(path)[1:-1]} {self.status} {elapsed}ms",
                      file=sys.stderr, flush=True)

    def log_message(self, format, *args):
        # The request wrapper logs one sanitized line, with no queries, headers or bodies.
        pass

    def send_response(self, code, message=None):
        self.status = code
        super().send_response(code, message)

    def send_error(self, code, message=None, explain=None):
        if code == 501:
            code = 403
        self.close_connection = True
        self.send_json({"error": self.responses.get(code, ("Request failed",))[0]}, code)

    def send_bytes(self, data, content_type, status=200):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def send_json(self, value, status=200):
        self.send_bytes(json.dumps(value).encode("utf-8"), "application/json", status)

    def send_file(self, stream, size, content_type, filename=None):
        with stream:
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(size))
            if filename is not None:
                self.send_header("Content-Disposition", f'attachment; filename="{filename}"')
            self.end_headers()
            shutil.copyfileobj(stream, self.wfile, length=1024 * 1024)

    def content_length(self, limit):
        lengths = self.headers.get_all("Content-Length", [])
        if not lengths:
            raise APIError(411, "Content-Length required")
        if len(lengths) != 1 or not re.fullmatch(r"[0-9]+", lengths[0]):
            raise APIError(400, "Invalid Content-Length")
        if self.headers.get("Transfer-Encoding") is not None:
            raise APIError(400, "Transfer-Encoding is unsupported")
        digits = lengths[0].lstrip("0")
        if len(digits) > len(str(limit)):
            raise APIError(413, "Body exceeds size limit")
        length = int(digits or "0")
        if length > limit:
            raise APIError(413, "Body exceeds size limit")
        return length

    def json_body(self):
        length = self.content_length(JSON_LIMIT)
        try:
            data = self.rfile.read(length)
            if len(data) != length:
                raise ValueError("Incomplete body")
            payload = json.loads(data)
        except (ValueError, RecursionError):
            raise APIError(400, "Invalid JSON body") from None
        if not isinstance(payload, dict):
            raise APIError(400, "Expected a JSON object")
        return payload

    def authorize(self, phone=False):
        auth = self.headers.get_all("Authorization", [])
        expected = f"Bearer {self.server.store.token}".encode("ascii")
        if len(auth) == 1 and secrets.compare_digest(auth[0].encode("latin-1"), expected):
            return
        if phone and self.headers.get("Tailscale-User-Login", "").strip():
            return
        raise APIError(403, "Forbidden")

    @staticmethod
    def wait_parameter(query, maximum):
        try:
            wait = float(query.get("wait", ["0"])[0])
        except ValueError:
            raise APIError(400, "Invalid wait") from None
        if not math.isfinite(wait) or wait < 0:
            raise APIError(400, "Invalid wait")
        return min(wait, maximum)

    def dispatch(self):
        store = self.server.store
        raw_path, _, query_string = self.path.partition("?")
        query = parse_qs(query_string, keep_blank_values=True)
        method = self.command
        if (method, raw_path) == ("GET", "/api/v1/health"):
            self.send_json({"ok": True, "seq": store.current_seq()})
            return
        if (method, raw_path) in (
            ("GET", "/"), ("GET", "/api/v1/state"), ("GET", "/api/v1/events")
        ):
            self.authorize(phone=True)
            if raw_path == "/":
                self.send_bytes(landing_page(store.state()["branches"]), "text/html; charset=utf-8")
            elif raw_path.endswith("/state"):
                self.send_json(store.state())
            else:
                try:
                    since = int(query.get("since", ["0"])[0])
                    if since < 0:
                        raise ValueError("Negative cursor")
                except ValueError:
                    raise APIError(400, "Invalid since") from None
                self.send_json(store.poll_events(since, self.wait_parameter(query, 30)))
            return
        if method == "POST" and raw_path in ("/api/v1/builds", "/api/v1/reviews"):
            self.authorize()
            create = store.publish if raw_path.endswith("/builds") else store.create_review
            self.send_json(create(self.json_body()), 201)
            return
        match = re.fullmatch(r"/api/v1/builds/([^/]*)/apk", raw_path)
        if match and method in ("GET", "PUT"):
            self.authorize(phone=method == "GET")
            build_id = safe_name(unquote(match[1]), "build id")
            if method == "PUT":
                store.upload(build_id, self.rfile, self.content_length(APK_LIMIT))
                self.send_json({"ok": True})
            else:
                stream, build = store.open_apk(build_id)
                filename = f'JeanCalculPreview-{slug(build["branch"])}.apk'
                self.send_file(stream, build["size"], APK_TYPE, filename)
            return
        match = re.fullmatch(r"/api/v1/reviews/([^/]*)(?:/(.*))?", raw_path)
        if match:
            action = match[2]
            if method == "GET" and (action is None or action.startswith("images/")):
                self.authorize(phone=True)
                review_id = safe_name(unquote(match[1]), "review id")
                if action is None:
                    self.send_json(store.get_review(review_id, self.wait_parameter(query, 60)))
                else:
                    name = safe_name(unquote(action.removeprefix("images/")), "image name")
                    stream = store.open_image(review_id, name)
                    self.send_file(stream, os.fstat(stream.fileno()).st_size, "image/png")
                return
            if method == "POST" and action in ("answer", "cancel"):
                self.authorize(phone=action == "answer")
                review_id = safe_name(unquote(match[1]), "review id")
                payload = self.json_body() if action == "answer" else None
                self.send_json(store.close_review(review_id, action, payload))
                return
        raise APIError(403, "Forbidden")

    def handle_api(self):
        try:
            self.dispatch()
        except APIError as error:
            self.send_json({"error": str(error)}, error.status)
        except (ConnectionError, TimeoutError):
            raise
        except OSError:
            if self.status is None:
                self.send_json({"error": "Storage error"}, 500)
            else:
                self.close_connection = True

    do_GET = do_PUT = do_POST = handle_api


class Server(ThreadingHTTPServer):
    def __init__(self, port, root):
        super().__init__(("127.0.0.1", port), Handler, bind_and_activate=False)
        try:
            self.server_bind()
            self.store = Store(root)
            self.server_activate()
        except BaseException:
            self.server_close()
            raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    serve = commands.add_parser("serve")
    serve.add_argument("--port", type=int, default=PORT)
    serve.add_argument("--data", type=Path, default=data_dir())
    args = parser.parse_args()
    try:
        with Server(args.port, args.data) as server:
            print(f"devhub listening on http://127.0.0.1:{server.server_port}", file=sys.stderr)
            server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
