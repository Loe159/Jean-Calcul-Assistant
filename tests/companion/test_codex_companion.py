import importlib.util
import json
import sys
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[2] / "companion" / "codex_companion.py"
spec = importlib.util.spec_from_file_location("codex_companion", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec and spec.loader
sys.modules[spec.name] = module
spec.loader.exec_module(module)


class CodexCompanionTest(unittest.TestCase):
    def test_stream_event_does_not_serialize_secrets(self):
        event = module.StreamEvent(sequence=1, type="text_delta", request_id="request", text="bonjour")
        payload = json.loads(event.as_json())
        self.assertEqual(
            payload,
            {"sequence": 1, "type": "text_delta", "requestId": "request", "text": "bonjour"},
        )
        self.assertNotIn("token", payload)
        self.assertNotIn("authorization", payload)

    def test_run_state_sequences_events_and_becomes_terminal(self):
        run = module.RunState("session", "thread", "turn", "request")
        run.append("text_delta", text="a")
        run.append("text_delta", text="b")
        run.append("completed")
        sequences = [event.sequence for event in run.events]
        self.assertEqual(sequences, sorted(sequences))
        self.assertEqual(sequences[1], sequences[0] + 1)
        self.assertEqual(sequences[2], sequences[1] + 1)
        self.assertTrue(run.terminal)

    def test_notification_mapping_handles_delta_and_completion(self):
        run = module.RunState("session", "thread", "turn", "request")
        module.CompanionService._apply_notification(
            run,
            {"method": "item/agentMessage/delta", "params": {"turnId": "turn", "delta": "Salut"}},
        )
        module.CompanionService._apply_notification(
            run,
            {"method": "turn/completed", "params": {"turn": {"id": "turn", "status": "completed"}}},
        )
        self.assertEqual([event.type for event in run.events], ["text_delta", "completed"])
        self.assertEqual(run.events[0].text, "Salut")

    def test_tool_event_serializes_android_call(self):
        run = module.RunState("session", "thread", "turn", "request")
        run.append(
            "tool_call",
            call_id="call-1",
            tool_name="device.toggle_flashlight",
            tool_version="1.0.0",
            arguments={"enabled": True},
        )
        payload = json.loads(run.events[0].as_json())
        self.assertEqual(payload["type"], "tool_call")
        self.assertEqual(payload["callId"], "call-1")
        self.assertEqual(payload["toolName"], "device.toggle_flashlight")
        self.assertEqual(payload["arguments"], {"enabled": True})


class FakeCodex:
    alive = True

    def __init__(self):
        self.handler = None
        self.request_handler = None
        self.resume_calls = 0

    def add_notification_handler(self, handler):
        self.handler = handler

    def set_request_handler(self, handler):
        self.request_handler = handler

    def start_thread(self, tools=None):
        return "thread-1"

    def resume_thread(self, session_id):
        self.resume_calls += 1
        return session_id


class CompanionServiceTest(unittest.TestCase):
    def test_resume_is_idempotent_for_session_created_in_current_process(self):
        codex = FakeCodex()
        service = module.CompanionService(codex)
        session_id = service.create_session()

        self.assertEqual(service.resume_session(session_id), session_id)
        self.assertEqual(service.resume_session(session_id), session_id)
        self.assertEqual(codex.resume_calls, 0)

    def test_dynamic_tool_request_round_trips_through_android_result(self):
        codex = FakeCodex()
        service = module.CompanionService(codex)
        tool = module.ToolSpec(
            "device.toggle_flashlight",
            "1.0.0",
            "Toggle flashlight",
            {
                "type": "object",
                "properties": {"enabled": {"type": "boolean"}},
                "required": ["enabled"],
                "additionalProperties": False,
            },
        )
        session_id = service.create_session([tool])
        session = service._sessions[session_id]
        session.thread_id = "thread-1"
        run = module.RunState(session_id, "thread-1", "turn-1", "request-1")
        service._runs["turn-1"] = run
        request = {
            "id": 77,
            "method": "item/tool/call",
            "params": {
                "threadId": "thread-1",
                "turnId": "turn-1",
                "callId": "call-1",
                "namespace": "android",
                "tool": tool.dynamic_name,
                "arguments": {"enabled": True},
            },
        }
        output = {}

        def invoke():
            output.update(service._handle_server_request(request))

        thread = threading.Thread(target=invoke)
        thread.start()
        with run.condition:
            run.condition.wait_for(lambda: any(event.type == "tool_call" for event in run.events), timeout=1)
        service.submit_tool_result(
            session_id,
            "turn-1",
            "call-1",
            {"callId": "call-1", "success": True, "output": {"enabled": True, "cameraId": "0"}},
        )
        thread.join(timeout=1)

        self.assertTrue(output["success"])
        self.assertIn('"enabled":true', output["contentItems"][0]["text"])


class CompanionHttpAuthenticationTest(unittest.TestCase):
    def setUp(self):
        service = module.CompanionService(FakeCodex())
        self.server = module.CompanionHttpServer(("127.0.0.1", 0), service=service, token="pairing-token")
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base_url = f"http://127.0.0.1:{self.server.server_address[1]}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)

    def test_rejects_unauthenticated_client(self):
        with self.assertRaises(urllib.error.HTTPError) as raised:
            urllib.request.urlopen(self.base_url + "/v1/status", timeout=2)
        self.assertEqual(raised.exception.code, 401)
        raised.exception.close()

    def test_rejects_incompatible_protocol(self):
        request = urllib.request.Request(
            self.base_url + "/v1/status",
            headers={"Authorization": "Bearer pairing-token", "X-Jean-Calcul-Protocol": "999"},
        )
        with self.assertRaises(urllib.error.HTTPError) as raised:
            urllib.request.urlopen(request, timeout=2)
        self.assertEqual(raised.exception.code, 426)
        body = json.loads(raised.exception.read())
        self.assertEqual(body["supportedProtocolVersion"], "2")
        raised.exception.close()

    def test_accepts_authenticated_current_protocol(self):
        request = urllib.request.Request(
            self.base_url + "/v1/status",
            headers={"Authorization": "Bearer pairing-token", "X-Jean-Calcul-Protocol": "2"},
        )
        with urllib.request.urlopen(request, timeout=2) as response:
            body = json.load(response)
        self.assertEqual(body["state"], "ready")
        self.assertEqual(body["auth"], "chatgpt")


class CodexAppServerProtocolTest(unittest.TestCase):
    def _fake_codex(self, account_type="chatgpt"):
        import os
        import tempfile
        from textwrap import dedent

        directory = tempfile.TemporaryDirectory()
        path = Path(directory.name) / "codex"
        path.write_text(
            dedent(
                f'''\
                #!/usr/bin/env python3
                import json, sys
                assert "features.shell_tool=false" in sys.argv
                assert "features.unified_exec=false" in sys.argv
                assert "features.code_mode_host=false" in sys.argv
                assert 'web_search="disabled"' in sys.argv
                assert "app-server" in sys.argv
                for line in sys.stdin:
                    message = json.loads(line)
                    method = message.get("method")
                    request_id = message.get("id")
                    if request_id is None:
                        continue
                    if method == "initialize":
                        assert message["params"]["capabilities"]["experimentalApi"] is True
                        result = {{}}
                    elif method == "account/read":
                        result = {{"account": {{"type": "{account_type}"}}, "requiresOpenaiAuth": True}}
                    elif method == "thread/start":
                        result = {{"thread": {{"id": "thread-1"}}}}
                    elif method == "thread/resume":
                        result = {{"thread": {{"id": message["params"]["threadId"]}}}}
                    elif method == "turn/start":
                        result = {{"turn": {{"id": "turn-1"}}}}
                    elif method == "turn/interrupt":
                        result = {{}}
                    else:
                        result = {{}}
                    print(json.dumps({{"id": request_id, "result": result}}), flush=True)
                    if method == "turn/start":
                        print(json.dumps({{"method": "item/agentMessage/delta", "params": {{"turnId": "turn-1", "delta": "OK"}}}}), flush=True)
                        print(json.dumps({{"method": "turn/completed", "params": {{"turn": {{"id": "turn-1", "status": "completed"}}}}}}), flush=True)
                '''
            ),
            encoding="utf-8",
        )
        os.chmod(path, 0o755)
        return directory, str(path)

    def test_chatgpt_account_runs_a_streaming_turn(self):
        directory, binary = self._fake_codex("chatgpt")
        try:
            app_server = module.CodexAppServer(binary, directory.name)
            app_server.start()
            service = module.CompanionService(app_server)
            session = service.create_session()
            run = service.start_run(session, "request-1", "Bonjour")
            deadline = __import__("time").monotonic() + 2
            while not run.terminal and __import__("time").monotonic() < deadline:
                __import__("time").sleep(0.01)
            self.assertTrue(session.startswith("jc_"))
            self.assertEqual(run.thread_id, "thread-1")
            self.assertEqual([event.type for event in run.events], ["text_delta", "completed"])
            self.assertEqual(run.events[0].text, "OK")
        finally:
            app_server.close()
            directory.cleanup()

    def test_api_key_account_is_refused(self):
        directory, binary = self._fake_codex("apiKey")
        app_server = module.CodexAppServer(binary, directory.name)
        try:
            with self.assertRaisesRegex(module.CompanionError, "ChatGPT subscription"):
                app_server.start()
        finally:
            app_server.close()
            directory.cleanup()


if __name__ == "__main__":
    unittest.main()
