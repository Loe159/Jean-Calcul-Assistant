"""Devhub integration tests using synthetic data and a loopback HTTP server."""

import base64
from concurrent.futures import ThreadPoolExecutor
from contextlib import redirect_stderr, redirect_stdout
from datetime import UTC, datetime, timedelta
import hashlib
import http.client
import io
import json
import os
from pathlib import Path
import socket
import stat
import subprocess
import sys
from tempfile import TemporaryDirectory
import threading
import unittest
from unittest import mock

import cli
import devhub


APK = b"synthetic APK"
PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jP1sAAAAASUVORK5CYII="
)
PHONE = {"Tailscale-User-Login": "owner@example.invalid"}
CLI = Path(__file__).resolve().with_name("cli.py")


def image(name="sheet.png", content=PNG):
    return {"name": name, "data": base64.b64encode(content).decode("ascii")}


class DevhubTests(unittest.TestCase):
    def setUp(self):
        temporary = TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.data = self.directory / devhub.DATA_NAME
        self.logs = io.StringIO()
        self.enterContext(redirect_stderr(self.logs))
        self.start_server()

    def start_server(self):
        self.server = devhub.Server(0, self.data)
        self.server.daemon_threads = False
        self.port = self.server.server_port
        self.token = self.server.store.token
        self.thread = threading.Thread(target=self.server.serve_forever, kwargs={"poll_interval": 0.01})
        self.thread.start()
        self.addCleanup(self.stop_server, self.server, self.thread)

    @staticmethod
    def stop_server(server, thread):
        if thread.is_alive():
            server.shutdown()
            thread.join()
        server.server_close()

    def restart(self):
        self.stop_server(self.server, self.thread)
        self.start_server()

    def request(self, method, path, *, body=None, headers=None, auth=True, no_length=False):
        headers = dict(headers or {})
        if auth:
            headers.setdefault("Authorization", f"Bearer {self.token}")
        if isinstance(body, dict):
            body = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=7)
        try:
            if no_length:
                conn.putrequest(method, path)
                for key, value in headers.items():
                    conn.putheader(key, value)
                conn.endheaders()
            else:
                conn.request(method, path, body=body, headers=headers)
            response = conn.getresponse()
            content = response.read()
            if response.headers.get_content_type() == "application/json":
                content = json.loads(content)
            return response.status, content, response.headers
        finally:
            conn.close()

    def build(self, build_id="build-1", branch="feat/sample", when=None, **overrides):
        payload = {
            "id": build_id, "branch": branch, "worktree": "synthetic-tree", "commit": "a" * 40,
            "dirty": False, "message": "Preview", "versionName": "2.0.0-dev",
            "sha256": hashlib.sha256(APK).hexdigest(),
            "createdAt": devhub.iso_utc(when or datetime.now(UTC)),
        }
        payload.update(overrides)
        return payload

    def publish(self, build_id="build-1", **kwargs):
        self.assertEqual(self.request("PUT", f"/api/v1/builds/{build_id}/apk", body=APK)[0], 200)
        code, build, _ = self.request("POST", "/api/v1/builds", body=self.build(build_id, **kwargs))
        self.assertEqual(code, 201, build)
        return build

    def review(self, **fields):
        payload = {"branch": "feat/sample", "title": "Which layout?", **fields}
        code, review, _ = self.request("POST", "/api/v1/reviews", body=payload)
        self.assertEqual(code, 201, review)
        return review

    def during_poll(self, path, action):
        waiting = threading.Event()
        condition = self.server.store.condition
        original = condition.wait

        def wait(seconds):
            waiting.set()
            return original(seconds)

        with mock.patch.object(condition, "wait", side_effect=wait):
            with ThreadPoolExecutor(max_workers=1) as pool:
                result = pool.submit(self.request, "GET", path)
                self.assertTrue(waiting.wait(3), "The request never entered its long poll")
                action()
                return result.result(timeout=3)

    def run_cli(self, *args, cwd=None, env=None):
        return subprocess.run(
            [sys.executable, "-I", str(CLI), *args], cwd=cwd, text=True, capture_output=True,
            timeout=12, env={
                **os.environ, "XDG_DATA_HOME": str(self.directory),
                "JC_DEVHUB_URL": f"http://127.0.0.1:{self.port}", **(env or {}),
            },
        )

    def test_token_permissions_and_persistence(self):
        path = self.data / "token"
        self.assertRegex(self.token, r"^[A-Za-z0-9_-]{43}$")
        self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
        self.assertEqual(path.read_text(encoding="ascii").strip(), self.token)
        previous = self.token
        self.restart()
        self.assertEqual(self.token, previous)

    def test_authentication_and_routes(self):
        code, health, _ = self.request("GET", "/api/v1/health", auth=False)
        self.assertEqual((code, health), (200, {"ok": True, "seq": 0}))
        for path in ("/api/v1/state", "/api/v1/events", "/"):
            with self.subTest(path=path):
                self.assertEqual(self.request("GET", path, auth=False)[0], 403)
                wrong = {"Authorization": "Bearer insufficient"}
                self.assertEqual(self.request("GET", path, auth=False, headers=wrong)[0], 403)
                self.assertEqual(self.request("GET", path)[0], 200)
                self.assertEqual(self.request("GET", path, auth=False, headers=PHONE)[0], 200)
        for method, path in (
            ("POST", "/api/v1/builds"), ("PUT", "/api/v1/builds/sample/apk"),
            ("POST", "/api/v1/reviews"), ("POST", "/api/v1/reviews/sample/cancel"),
        ):
            with self.subTest(path=path):
                self.assertEqual(self.request(method, path, auth=False)[0], 403)
                code, result, _ = self.request(method, path, auth=False, headers=PHONE)
                self.assertEqual((code, result), (403, {"error": "Forbidden"}))
        self.assertEqual(self.request("GET", "/api/v1/unknown")[0], 403)
        self.assertEqual(self.request("DELETE", "/api/v1/health")[0], 403)

    def test_request_logging_omits_token_and_query(self):
        self.request("GET", f"/{self.token}?query-secret=1")
        self.request("GET", "/api/v1/health")
        # The log is flushed at the end of the request, just after the response is written.
        self.stop_server(self.server, self.thread)
        output = self.logs.getvalue().splitlines()
        self.assertEqual(len(output), 2)
        self.assertRegex(output[0], r"^GET /\[redacted\] 403 \d+ms$")
        self.assertRegex(output[1], r"^GET /api/v1/health 200 \d+ms$")
        self.assertNotIn(self.token, self.logs.getvalue())
        self.assertNotIn("query-secret", self.logs.getvalue())

    def test_upload_sha_download_headers_and_landing_page(self):
        build_id = "some-id"
        path = f"/api/v1/builds/{build_id}/apk"
        message = '<script>alert("preview")</script>'
        branch = "feat/<sample>"
        self.assertEqual(self.request("POST", "/api/v1/builds", body=self.build(build_id))[0], 409)
        self.assertEqual(self.request("PUT", path, body=APK)[0], 200)
        folder = self.data / "builds" / build_id
        self.assertEqual((folder / "pending.apk").read_bytes(), APK)
        self.assertEqual(self.request("GET", path)[0], 404)
        wrong = self.build(build_id, sha256="0" * 64)
        self.assertEqual(self.request("POST", "/api/v1/builds", body=wrong)[0], 409)
        self.assertTrue((folder / "pending.apk").exists())
        self.assertEqual(self.request("GET", "/api/v1/health")[1]["seq"], 0)
        code, build, _ = self.request("POST", "/api/v1/builds",
                                      body=self.build(build_id, message=message, branch=branch))
        self.assertEqual(code, 201)
        self.assertEqual(build["size"], len(APK))
        self.assertEqual(devhub.read_json(folder / "build.json"), build)
        self.assertFalse((folder / "pending.apk").exists())
        code, content, headers = self.request("GET", path, auth=False, headers=PHONE)
        self.assertEqual((code, content), (200, APK))
        self.assertEqual(headers["Content-Length"], str(len(APK)))
        self.assertEqual(headers["Content-Type"], devhub.APK_TYPE)
        self.assertEqual(headers["Content-Disposition"],
                         'attachment; filename="JeanCalculPreview-feat-sample.apk"')
        self.assertEqual(self.request("PUT", path, body=b"replacement")[0], 409)
        page = self.request("GET", "/", auth=False, headers=PHONE)[1].decode("utf-8")
        self.assertIn("&lt;script&gt;", page)
        self.assertNotIn(message, page)
        self.assertIn("&lt;sample&gt;", page)
        self.assertIn(f'href="{path}"', page)

    def test_lengths_and_size_caps(self):
        path = "/api/v1/builds/sample/apk"
        self.assertEqual(self.request("PUT", path, no_length=True)[0], 411)
        for method, endpoint, limit in (
            ("PUT", path, devhub.APK_LIMIT),
            ("POST", "/api/v1/reviews", devhub.JSON_LIMIT),
        ):
            self.assertEqual(self.request(method, endpoint, no_length=True,
                                          headers={"Content-Length": str(limit + 1)})[0], 413)
        oversized_image = image(content=PNG + b"\0" * devhub.IMAGE_LIMIT)
        self.assertEqual(self.request("POST", "/api/v1/reviews", body={
            "title": "An image", "branch": "sample", "images": [oversized_image],
        })[0], 413)

    def test_retention_and_state_branch_order(self):
        now = datetime.now(UTC)
        for offset in range(6):
            self.publish(f"build-{offset}", when=now - timedelta(minutes=10 - offset))
        self.publish("current", branch="main", when=now)
        self.publish("expired", branch="feat/stale", when=now - timedelta(days=15))
        _, state, _ = self.request("GET", "/api/v1/state")
        branches = state["branches"]
        self.assertEqual([branch["branch"] for branch in branches], ["main", "feat/sample"])
        self.assertEqual([b["id"] for b in branches[1]["builds"]],
                         [f"build-{i}" for i in range(5, 0, -1)])
        self.assertFalse((self.data / "builds" / "build-0").exists())
        self.assertFalse((self.data / "builds" / "expired").exists())

    def test_startup_prunes_expired_build_and_closed_reviews(self):
        self.publish()
        old_build = self.data / "builds" / "build-1" / "build.json"
        devhub.atomic_json(old_build, {**devhub.read_json(old_build),
                                      "createdAt": "2020-01-01T00:00:00Z"})
        verdicts = ["answer", "cancel"]
        for verdict in verdicts:
            review_id = self.review()["id"]
            path = f"/api/v1/reviews/{review_id}/{verdict}"
            body = {"verdict": "approved"} if verdict == "answer" else None
            self.assertEqual(self.request("POST", path, body=body)[0], 200)
            metadata = self.data / "reviews" / review_id / "review.json"
            review = devhub.read_json(metadata)
            if review["status"] == "answered":
                review["answer"]["answeredAt"] = "2020-01-01T00:00:00Z"
            else:
                review["cancelledAt"] = "2020-01-01T00:00:00Z"
            devhub.atomic_json(metadata, review)
        still_open = self.review()
        self.restart()
        state = self.request("GET", "/api/v1/state")[1]
        self.assertEqual(state["branches"], [])
        self.assertEqual(state["reviews"], [still_open])
        self.assertEqual(len(list((self.data / "reviews").iterdir())), 1)

    def test_events_wait_for_build_and_resume_after_restart(self):
        code, result, _ = self.during_poll(
            "/api/v1/events?since=0&wait=5", lambda: self.publish(),
        )
        self.assertEqual(code, 200)
        self.assertEqual(result, {"seq": 1, "events": [
            {"seq": 1, "type": "build", "id": "build-1", "branch": "feat/sample"},
        ]})
        code, future, _ = self.request("GET", "/api/v1/events?since=100&wait=5")
        self.assertEqual((code, future), (200, {"seq": 1, "events": [], "resync": True}))
        self.request("PUT", "/api/v1/builds/build-2/apk", body=APK)
        self.restart()
        self.assertEqual(self.request("GET", "/api/v1/events?since=0")[1],
                         {"seq": 1, "events": [], "resync": True})
        self.assertEqual(self.request("POST", "/api/v1/builds", body=self.build("build-2"))[0], 201)
        self.assertEqual(self.request("GET", "/api/v1/health")[1]["seq"], 2)

    def test_events_resync_outside_memory_window(self):
        store = self.server.store
        for _ in range(502):
            store.create_review({"branch": "test", "title": "Synthetic"})
        stale = self.request("GET", "/api/v1/events?since=1")[1]
        self.assertEqual(stale, {"seq": 502, "events": [], "resync": True})
        current = self.request("GET", "/api/v1/events?since=2", auth=False, headers=PHONE)[1]
        self.assertEqual(len(current["events"]), 500)
        self.assertEqual(current["events"][0]["seq"], 3)
        self.assertNotIn("resync", current)

    def test_review_images_choices_wait_and_second_answer(self):
        review = self.review(
            images=[image()], options=[
                {"id": "A", "label": "Large"},
                {"id": "B", "label": "Small", "previewFqn": "test.Widget",
                 "image": image("small.png")},
            ], buildId="sample", previewFqn="test.Sheet",
        )
        review_id = review["id"]
        self.assertRegex(review_id, r"^r-\d{8}-\d{6}-[a-f0-9]{4}$")
        self.assertEqual(review["images"], ["sheet.png"])
        self.assertEqual(review["options"][1]["image"], "small.png")
        self.assertEqual(review["options"][1]["previewFqn"], "test.Widget")
        path = f"/api/v1/reviews/{review_id}"
        self.assertEqual(self.request("GET", path, auth=False, headers=PHONE)[1], review)
        for name in ("sheet.png", "small.png"):
            code, contents, headers = self.request("GET", f"{path}/images/{name}",
                                                   auth=False, headers=PHONE)
            self.assertEqual((code, contents, headers["Content-Type"]), (200, PNG, "image/png"))
        for body in (
            {"verdict": "unexpected"}, {"verdict": "choice"}, {"verdict": "choice", "choice": "C"},
            {"verdict": "approved", "choice": "A"}, {"verdict": "changes", "comment": "x" * 4001},
            {"verdict": "approved", "installedBuildId": "../unsafe"},
        ):
            with self.subTest(body={**body, "comment": str(body.get("comment", ""))[:30]}):
                self.assertEqual(self.request("POST", f"{path}/answer", body=body)[0], 400)

        def answer():
            response = self.request("POST", f"{path}/answer", auth=False, headers=PHONE, body={
                "verdict": "choice", "choice": "B", "comment": "Compact, please",
                "installedBuildId": "sample",
            })
            self.assertEqual(response[0], 200)

        code, result, _ = self.during_poll(path + "?wait=5", answer)
        self.assertEqual(code, 200)
        self.assertEqual(result["status"], "answered")
        self.assertEqual(result["answer"]["choice"], "B")
        self.assertEqual(result["answer"]["comment"], "Compact, please")
        self.assertEqual(self.request("POST", f"{path}/answer", body={"verdict": "approved"})[0], 409)
        self.assertEqual(self.request("POST", f"{path}/cancel")[0], 409)
        events = self.request("GET", "/api/v1/events?since=0")[1]["events"]
        self.assertEqual([event["type"] for event in events], ["review", "answer"])

    def test_review_state_cap_and_open_first(self):
        open_reviews = [self.review(title=f"Open {i}") for i in range(31)]
        newest = self.review(title="Closed")
        path = f"/api/v1/reviews/{newest['id']}/cancel"
        self.assertEqual(self.request("POST", path)[1]["status"], "cancelled")
        state = self.request("GET", "/api/v1/state")[1]
        self.assertEqual(state["reviews"], list(reversed(open_reviews))[:30])
        events = self.request("GET", "/api/v1/events?since=32")[1]["events"]
        self.assertEqual(events[0]["type"], "cancel")

    def test_traversal_and_non_png_rejected(self):
        review_id = self.review()["id"]
        for name in ("..", ".", "%2e%2e", "%2e%2e%2ftoken", "other%5cfile", "x" * 97):
            with self.subTest(name=name):
                self.assertEqual(self.request("PUT", f"/api/v1/builds/{name}/apk", body=APK)[0], 400)
                self.assertEqual(self.request("GET", f"/api/v1/reviews/{name}")[0], 400)
                self.assertEqual(self.request("GET", f"/api/v1/reviews/{review_id}/images/{name}")[0],
                                 400)
        self.assertEqual(self.request("POST", "/api/v1/builds", body=self.build("../outside"))[0], 400)
        for fields in (
            {"images": [image("../token")]}, {"images": [image("review.json")]},
            {"images": [image(content=b"not a png")]}, {"buildId": "../outside"},
            {"options": [{"id": "../outside", "label": "Unsafe"}]},
            {"options": [{"id": "A", "label": "Unsafe", "image": image("nested/sample.png")}]},
        ):
            payload = {"branch": "feat/sample", "title": "Invalid", **fields}
            self.assertEqual(self.request("POST", "/api/v1/reviews", body=payload)[0], 400)
        self.assertEqual(list((self.data / "builds").iterdir()), [])
        self.assertEqual([path.name for path in (self.data / "reviews").iterdir()], [review_id])

    def test_cli_skip_build_writes_asset_uploads_apk_and_creates_review(self):
        tree = self.directory / "sample-worktree"
        tree.mkdir()

        def git(*args):
            return subprocess.run(["git", *args], cwd=tree, check=True, capture_output=True,
                                  text=True).stdout.strip()

        git("init", "--initial-branch=feat/test")
        (tree / ".gitignore").write_text("app/build-devpreview/\n", encoding="utf-8")
        git("add", ".gitignore")
        git("-c", "user.name=Test Agent", "-c", "user.email=agent@example.invalid",
            "-c", "commit.gpgsign=false", "commit", "-m", "Create synthetic worktree")
        (tree / "untracked").touch()
        folder = tree / "app" / "build-devpreview"
        output = folder / "outputs" / "apk" / "debug"
        output.mkdir(parents=True)
        (output / "JeanCalculPreview.apk").write_bytes(APK)
        devhub.atomic_json(output / "output-metadata.json", {"elements": [{"versionName": "2.0-test"}]})
        (tree / "test.png").write_bytes(PNG)
        public = "https://example.invalid:4120"
        result = self.run_cli("push", "--skip-build", "-m", "Test push", "--ask", "Review the build",
                              "--option", "A=Compact:test.png@test.Widget", cwd=tree,
                              env={"JC_DEVHUB_PUBLIC_URL": public})
        self.assertEqual(result.returncode, 0, result.stderr)
        metadata = devhub.read_json(folder / "dev-build-assets" / "dev-build.json")
        commit = git("rev-parse", "HEAD")
        build_id = metadata["buildId"]
        self.assertRegex(build_id, rf"^\d{{8}}-\d{{6}}-{commit[:6]}$")
        self.assertEqual(metadata, {
            "buildId": build_id, "branch": "feat/test", "worktree": "sample-worktree",
            "commit": commit, "dirty": True, "message": "Test push", "builtAt": metadata["builtAt"],
            "hubUrl": public,
        })
        self.assertTrue(metadata["builtAt"].endswith("Z"))
        self.assertIn(f"Published {build_id} on feat/test", result.stdout)
        self.assertIn("open Preview Hub on the phone", result.stdout)
        result = self.run_cli("ask", "Follow-up", "--latest", cwd=tree)
        self.assertEqual(result.returncode, 0, result.stderr)
        state = self.request("GET", "/api/v1/state")[1]
        self.assertEqual(state["branches"][0]["builds"][0]["versionName"], "2.0-test")
        self.assertEqual(state["branches"][0]["builds"][0]["createdAt"], metadata["builtAt"])
        self.assertEqual(len(state["reviews"]), 2)
        self.assertTrue(all(review["buildId"] == build_id for review in state["reviews"]))
        pushed_review = next(review for review in state["reviews"] if review["title"] == "Review the build")
        self.assertEqual(pushed_review["options"], [
            {"id": "A", "label": "Compact", "image": "test.png", "previewFqn": "test.Widget"},
        ])

    def test_cli_wait_outputs_and_exit_codes(self):
        for verdict, expected, line in (
            ("approved", 0, "APPROVED"),
            ("choice", 0, "CHOICE A: Compact"),
            ("changes", 3, "CHANGES REQUESTED"),
            ("open", 4, "TIMEOUT"),
            ("cancel", 5, "CANCELLED"),
        ):
            with self.subTest(verdict=verdict):
                review_id = self.review(options=[{"id": "A", "label": "Compact"}])["id"]
                path = f"/api/v1/reviews/{review_id}"
                output = line + "\n"
                if verdict == "cancel":
                    result = self.run_cli("cancel", review_id)
                    self.assertEqual(result.returncode, 0, result.stderr)
                elif verdict != "open":
                    body = {"verdict": verdict, "comment": "Owner feedback"}
                    if verdict == "choice":
                        body["choice"] = "A"
                    self.assertEqual(self.request("POST", path + "/answer", body=body)[0], 200)
                    output += "Owner feedback\n"
                result = self.run_cli("wait", review_id, "--timeout", "0.03")
                self.assertEqual((result.returncode, result.stdout), (expected, output), result.stderr)
                if verdict == "open":
                    self.assertEqual(self.request("GET", path)[1]["status"], "open")

    def test_cli_status_down_has_service_hint(self):
        with socket.socket() as offline:
            offline.bind(("127.0.0.1", 0))
            url = f"http://127.0.0.1:{offline.getsockname()[1]}"
            result = self.run_cli("status", env={"JC_DEVHUB_URL": url})
        self.assertEqual(result.returncode, 1)
        self.assertIn("Devhub down", result.stdout)
        self.assertIn("scripts/jc-preview service install", result.stdout)
        self.assertIn("scripts/jc-preview serve", result.stdout)


class CLITests(unittest.TestCase):
    def test_option_parsing(self):
        cases = (
            ("A=Current", ("A", "Current", None, None)),
            ("B=Compact:shots/review.png@test.Widget", ("B", "Compact", "shots/review.png", "test.Widget")),
            ("C=Live@test.Widget", ("C", "Live", None, "test.Widget")),
            ("D=Label: with colon", ("D", "Label: with colon", None, None)),
            ("E=Image:/tmp/one@two.png@test.Widget", ("E", "Image", "/tmp/one@two.png", "test.Widget")),
            ("F=Label: detail:/tmp/image.png", ("F", "Label: detail", "/tmp/image.png", None)),
        )
        for value, expected in cases:
            self.assertEqual(cli.parse_option(value), expected)
        for value in ("no-equals", "../A=Unsafe", "A=", "A=Label@"):
            with self.assertRaises(cli.argparse.ArgumentTypeError):
                cli.parse_option(value)

    def test_service_install_uses_copied_script_and_activates_unit(self):
        with TemporaryDirectory() as temporary:
            root = Path(temporary)
            configured = root / "custom data"
            environment = {"XDG_DATA_HOME": str(configured), "JC_DEVHUB_NIX": "/test/nix",
                           "JC_DEVHUB_SYSTEMCTL": "/test/systemctl"}
            unit_name = cli.UNIT
            prefix = ["/test/systemctl", "--user"]
            with mock.patch.object(Path, "home", return_value=root), mock.patch.dict(os.environ, environment):
                for returncode, activate in (
                    (3, ["enable", "--now", unit_name]),
                    (0, ["restart", unit_name]),
                ):
                    with self.subTest(activate=activate):
                        results = [mock.Mock(returncode=code) for code in (0, returncode, 0)]
                        with mock.patch.object(cli.subprocess, "run", side_effect=results) as run:
                            with redirect_stdout(io.StringIO()) as stdout:
                                self.assertEqual(cli.service("install"), 0)
                        self.assertEqual(run.call_args_list, [
                            mock.call([*prefix, "daemon-reload"], check=True),
                            mock.call([*prefix, "is-active", "--quiet", unit_name]),
                            mock.call([*prefix, *activate], check=True),
                        ])
            data = configured / devhub.DATA_NAME
            script = data / "bin" / "devhub.py"
            unit = root / ".config" / "systemd" / "user" / cli.UNIT
            self.assertEqual(script.read_bytes(), cli.SERVER_SCRIPT.read_bytes())
            self.assertIn(f'ExecStart=/test/nix shell nixpkgs#python3 --command python3 "{script}"'
                          f' serve --data "{data}"', unit.read_text(encoding="utf-8"))
            self.assertIn("loginctl enable-linger $USER", stdout.getvalue())
            self.assertIn("Tailscale", stdout.getvalue())


if __name__ == "__main__":
    unittest.main()
