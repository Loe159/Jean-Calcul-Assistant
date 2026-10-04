#!/usr/bin/env python3
"""Jean Calcul Codex companion.

Bridges Android to a locally authenticated `codex app-server`. ChatGPT
credentials never leave Codex's own credential store.
"""
from __future__ import annotations

import argparse
import hmac
import json
import os
import queue
import re
import secrets
import shutil
import subprocess
import sys
import threading
import time
from dataclasses import dataclass, field
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Callable
from urllib.parse import parse_qs, urlparse

PROTOCOL_VERSION = "2"
DEFAULT_PORT = 43120
SAFE_DEVELOPER_INSTRUCTIONS = (
    "You are Jean Calcul, the reasoning agent for an Android phone. "
    "Android device capabilities are exposed as dynamic tools in the android namespace. "
    "Use those tools whenever they are relevant to the user's request; do not claim that you lack "
    "phone access when a matching tool is available. Android owns tool execution, policy, auditing, "
    "and permission checks. Never run shell commands, edit host files, browse the network, or operate "
    "the companion host. After a tool result, answer the user naturally and concisely."
)

class CompanionError(RuntimeError):
    pass

class JsonRpcError(CompanionError):
    pass

class CodexAppServer:
    def __init__(self, codex_bin: str, cwd: str) -> None:
        self.codex_bin = codex_bin
        self.cwd = str(Path(cwd).resolve())
        self._process: subprocess.Popen[str] | None = None
        self._pending: dict[int, queue.Queue[dict[str, Any]]] = {}
        self._pending_lock = threading.Lock()
        self._write_lock = threading.Lock()
        self._next_id = 1
        self._handlers: list[Callable[[dict[str, Any]], None]] = []
        self._request_handler: Callable[[dict[str, Any]], dict[str, Any]] | None = None
        self._closed = threading.Event()

    def add_notification_handler(self, handler: Callable[[dict[str, Any]], None]) -> None:
        self._handlers.append(handler)

    def set_request_handler(self, handler: Callable[[dict[str, Any]], dict[str, Any]]) -> None:
        self._request_handler = handler

    @property
    def alive(self) -> bool:
        return self._process is not None and self._process.poll() is None and not self._closed.is_set()

    def start(self) -> None:
        if self._process is not None:
            return
        self._process = subprocess.Popen(
            [
                self.codex_bin,
                "--config",
                "features.shell_tool=false",
                "--config",
                "features.unified_exec=false",
                "--config",
                "features.code_mode_host=false",
                "--config",
                'web_search="disabled"',
                "app-server",
                "--listen",
                "stdio://",
            ],
            cwd=self.cwd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )
        threading.Thread(target=self._read_stdout, name="codex-stdout", daemon=True).start()
        threading.Thread(target=self._drain_stderr, name="codex-stderr", daemon=True).start()
        self.request(
            "initialize",
            {
                "clientInfo": {
                    "name": "jean_calcul_companion",
                    "title": "Jean Calcul Companion",
                    "version": "0.1.0",
                },
                "capabilities": {"experimentalApi": True},
            },
        )
        self.notify("initialized")
        account = self.request("account/read", {})
        account_value = account.get("account")
        if not isinstance(account_value, dict) or account_value.get("type") != "chatgpt":
            self.close()
            raise CompanionError(
                "Codex must be authenticated with a ChatGPT subscription; API-key authentication is refused."
            )

    def close(self) -> None:
        self._closed.set()
        process = self._process
        if process is None:
            return
        if process.stdin:
            try:
                process.stdin.close()
            except OSError:
                pass
        try:
            process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            process.terminate()
            try:
                process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=2)
        for stream in (process.stdout, process.stderr):
            if stream is not None:
                stream.close()
        self._process = None

    def request(self, method: str, params: dict[str, Any] | None = None, timeout: float = 30.0) -> dict[str, Any]:
        if not self.alive:
            raise CompanionError("codex app-server is not running")
        with self._pending_lock:
            request_id = self._next_id
            self._next_id += 1
            response_queue: queue.Queue[dict[str, Any]] = queue.Queue(maxsize=1)
            self._pending[request_id] = response_queue
        payload: dict[str, Any] = {"id": request_id, "method": method}
        if params is not None:
            payload["params"] = params
        self._send(payload)
        try:
            response = response_queue.get(timeout=timeout)
        except queue.Empty as error:
            raise CompanionError(f"timeout waiting for Codex response to {method}") from error
        finally:
            with self._pending_lock:
                self._pending.pop(request_id, None)
        if "error" in response:
            error = response["error"]
            message = error.get("message", "unknown JSON-RPC error") if isinstance(error, dict) else str(error)
            raise JsonRpcError(f"{method}: {message}")
        result = response.get("result", response.get("response"))
        if not isinstance(result, dict):
            raise JsonRpcError(f"{method}: invalid response shape")
        return result

    def notify(self, method: str, params: dict[str, Any] | None = None) -> None:
        payload: dict[str, Any] = {"method": method}
        if params is not None:
            payload["params"] = params
        self._send(payload)

    def start_thread(self, tools: list["ToolSpec"] | None = None) -> str:
        params: dict[str, Any] = {
            "cwd": self.cwd,
            "approvalPolicy": "never",
            "sandbox": "read-only",
            "developerInstructions": SAFE_DEVELOPER_INSTRUCTIONS,
            "ephemeral": False,
        }
        if tools:
            params["dynamicTools"] = [
                {
                    "type": "namespace",
                    "name": "android",
                    "description": "Local Android device tools executed by Jean Calcul on the phone.",
                    "tools": [tool.as_dynamic_tool() for tool in tools],
                }
            ]
        result = self.request("thread/start", params)
        thread = result.get("thread")
        if not isinstance(thread, dict) or not isinstance(thread.get("id"), str):
            raise JsonRpcError("thread/start did not return a thread id")
        return thread["id"]

    def resume_thread(self, thread_id: str) -> str:
        result = self.request(
            "thread/resume",
            {
                "threadId": thread_id,
                "cwd": self.cwd,
                "approvalPolicy": "never",
                "sandbox": "read-only",
                "developerInstructions": SAFE_DEVELOPER_INSTRUCTIONS,
                "excludeTurns": True,
            },
        )
        thread = result.get("thread")
        if not isinstance(thread, dict) or thread.get("id") != thread_id:
            raise JsonRpcError("thread/resume returned an unexpected thread")
        return thread_id

    def start_turn(self, thread_id: str, request_id: str, text: str) -> str:
        result = self.request(
            "turn/start",
            {
                "threadId": thread_id,
                "clientUserMessageId": request_id,
                "input": [{"type": "text", "text": text}],
                "approvalPolicy": "never",
                "sandboxPolicy": {"type": "readOnly", "networkAccess": False},
            },
        )
        turn = result.get("turn")
        if not isinstance(turn, dict) or not isinstance(turn.get("id"), str):
            raise JsonRpcError("turn/start did not return a turn id")
        return turn["id"]

    def interrupt_turn(self, thread_id: str, turn_id: str) -> None:
        self.request("turn/interrupt", {"threadId": thread_id, "turnId": turn_id}, timeout=10.0)

    def _send(self, payload: dict[str, Any]) -> None:
        process = self._process
        if process is None or process.stdin is None or process.poll() is not None:
            raise CompanionError("codex app-server is unavailable")
        encoded = json.dumps(payload, separators=(",", ":"), ensure_ascii=False)
        with self._write_lock:
            process.stdin.write(encoded + "\n")
            process.stdin.flush()

    def _read_stdout(self) -> None:
        process = self._process
        if process is None or process.stdout is None:
            return
        for raw_line in process.stdout:
            try:
                message = json.loads(raw_line)
            except json.JSONDecodeError:
                continue
            if not isinstance(message, dict):
                continue
            request_id = message.get("id")
            method = message.get("method")
            if isinstance(request_id, int) and isinstance(method, str):
                threading.Thread(
                    target=self._handle_server_request,
                    args=(message,),
                    name=f"codex-request-{request_id}",
                    daemon=True,
                ).start()
                continue
            if isinstance(request_id, int):
                with self._pending_lock:
                    pending = self._pending.get(request_id)
                if pending is not None:
                    try:
                        pending.put_nowait(message)
                    except queue.Full:
                        pass
                continue
            if isinstance(method, str):
                for handler in tuple(self._handlers):
                    try:
                        handler(message)
                    except Exception:
                        pass
        self._closed.set()

    def _handle_server_request(self, message: dict[str, Any]) -> None:
        request_id = message.get("id")
        handler = self._request_handler
        if not isinstance(request_id, int):
            return
        if handler is None:
            result = {
                "contentItems": [{"type": "inputText", "text": "Jean Calcul cannot handle this tool request."}],
                "success": False,
            }
        else:
            try:
                result = handler(message)
            except Exception as error:
                result = {
                    "contentItems": [{"type": "inputText", "text": f"Android tool failed: {error}"}],
                    "success": False,
                }
        self._send({"id": request_id, "result": result})

    def _drain_stderr(self) -> None:
        process = self._process
        if process is not None and process.stderr is not None:
            for _ in process.stderr:
                pass

@dataclass(frozen=True)
class ToolSpec:
    name: str
    version: str
    description: str
    input_schema: dict[str, Any]

    @property
    def dynamic_name(self) -> str:
        normalized = re.sub(r"[^A-Za-z0-9_-]+", "_", self.name).strip("_")
        return f"jc_{normalized}" or "jc_tool"

    def as_dynamic_tool(self) -> dict[str, Any]:
        return {
            "type": "function",
            "name": self.dynamic_name,
            "description": self.description,
            "inputSchema": self.input_schema,
            "deferLoading": False,
        }

    @staticmethod
    def parse_many(value: Any) -> list["ToolSpec"]:
        if value is None:
            return []
        if not isinstance(value, list):
            raise CompanionError("tools must be an array")
        tools: list[ToolSpec] = []
        for raw in value:
            if not isinstance(raw, dict):
                raise CompanionError("tool definition must be an object")
            name = raw.get("name")
            version = raw.get("version")
            description = raw.get("description")
            input_schema = raw.get("inputSchema")
            if not all(isinstance(item, str) and item.strip() for item in (name, version, description)):
                raise CompanionError("tool name, version and description are required")
            if not isinstance(input_schema, dict):
                raise CompanionError("tool inputSchema must be an object")
            tools.append(ToolSpec(name, version, description, input_schema))
        dynamic_names = [tool.dynamic_name for tool in tools]
        if len(set(dynamic_names)) != len(dynamic_names):
            raise CompanionError("tool names collide after Codex normalization")
        return tools


@dataclass
class StreamEvent:
    sequence: int
    type: str
    request_id: str
    text: str | None = None
    error: str | None = None
    call_id: str | None = None
    tool_name: str | None = None
    tool_version: str | None = None
    arguments: dict[str, Any] | None = None

    def as_json(self) -> str:
        payload: dict[str, Any] = {
            "sequence": self.sequence,
            "type": self.type,
            "requestId": self.request_id,
        }
        if self.text is not None:
            payload["text"] = self.text
        if self.error is not None:
            payload["error"] = self.error
        if self.call_id is not None:
            payload["callId"] = self.call_id
        if self.tool_name is not None:
            payload["toolName"] = self.tool_name
        if self.tool_version is not None:
            payload["toolVersion"] = self.tool_version
        if self.arguments is not None:
            payload["arguments"] = self.arguments
        return json.dumps(payload, separators=(",", ":"), ensure_ascii=False)


@dataclass
class SessionState:
    session_id: str
    tools: list[ToolSpec] = field(default_factory=list)
    thread_id: str | None = None

    def tool_by_dynamic_name(self, name: str) -> ToolSpec | None:
        return next((tool for tool in self.tools if tool.dynamic_name == name), None)


@dataclass
class RunState:
    session_id: str
    thread_id: str
    turn_id: str
    request_id: str
    next_sequence: int = field(default_factory=time.time_ns)
    events: list[StreamEvent] = field(default_factory=list)
    tool_results: dict[str, dict[str, Any]] = field(default_factory=dict)
    terminal: bool = False
    condition: threading.Condition = field(default_factory=threading.Condition)

    def append(
        self,
        event_type: str,
        *,
        text: str | None = None,
        error: str | None = None,
        call_id: str | None = None,
        tool_name: str | None = None,
        tool_version: str | None = None,
        arguments: dict[str, Any] | None = None,
    ) -> None:
        with self.condition:
            sequence = self.next_sequence
            self.next_sequence += 1
            self.events.append(
                StreamEvent(
                    sequence,
                    event_type,
                    self.request_id,
                    text,
                    error,
                    call_id,
                    tool_name,
                    tool_version,
                    arguments,
                )
            )
            if event_type in {"completed", "failed", "cancelled"}:
                self.terminal = True
            self.condition.notify_all()


class CompanionService:
    def __init__(self, codex: CodexAppServer) -> None:
        self.codex = codex
        self._runs: dict[str, RunState] = {}
        self._sessions: dict[str, SessionState] = {}
        self._runs_lock = threading.Lock()
        self._pending_notifications: dict[str, list[dict[str, Any]]] = {}
        codex.add_notification_handler(self._on_notification)
        codex.set_request_handler(self._handle_server_request)

    def create_session(self, tools: list[ToolSpec] | None = None) -> str:
        session_id = f"jc_{secrets.token_urlsafe(18)}"
        with self._runs_lock:
            self._sessions[session_id] = SessionState(session_id, list(tools or []))
        return session_id

    def resume_session(self, session_id: str, tools: list[ToolSpec] | None = None) -> str:
        with self._runs_lock:
            session = self._sessions.get(session_id)
            if session is None:
                self._sessions[session_id] = SessionState(session_id, list(tools or []))
            elif tools and tools != session.tools:
                session.tools = list(tools)
                session.thread_id = None
        return session_id

    def _session(self, session_id: str, tools: list[ToolSpec] | None = None) -> SessionState:
        self.resume_session(session_id, tools)
        with self._runs_lock:
            return self._sessions[session_id]

    def start_run(
        self,
        session_id: str,
        request_id: str,
        text: str,
        tools: list[ToolSpec] | None = None,
    ) -> RunState:
        session = self._session(session_id, tools)
        if session.thread_id is None:
            session.thread_id = self.codex.start_thread(session.tools)
        turn_id = self.codex.start_turn(session.thread_id, request_id, text)
        run = RunState(session_id, session.thread_id, turn_id, request_id)
        with self._runs_lock:
            self._runs[turn_id] = run
            buffered = self._pending_notifications.pop(turn_id, [])
        for notification in buffered:
            self._apply_notification(run, notification)
        return run

    def get_run(self, turn_id: str) -> RunState | None:
        with self._runs_lock:
            return self._runs.get(turn_id)

    def release_run(self, turn_id: str) -> None:
        with self._runs_lock:
            self._runs.pop(turn_id, None)
            self._pending_notifications.pop(turn_id, None)

    def submit_tool_result(
        self,
        session_id: str,
        turn_id: str,
        call_id: str,
        result: dict[str, Any],
    ) -> None:
        run = self.get_run(turn_id)
        if run is None or run.session_id != session_id:
            raise CompanionError("unknown run")
        with run.condition:
            run.tool_results[call_id] = result
            run.condition.notify_all()

    def cancel(self, session_id: str, turn_id: str) -> None:
        run = self.get_run(turn_id)
        if run is None or run.session_id != session_id:
            raise CompanionError("unknown run")
        self.codex.interrupt_turn(run.thread_id, turn_id)

    def _handle_server_request(self, request: dict[str, Any]) -> dict[str, Any]:
        if request.get("method") != "item/tool/call":
            return self._dynamic_tool_failure("Unsupported Codex client request.")
        params = request.get("params")
        if not isinstance(params, dict):
            return self._dynamic_tool_failure("Invalid Android tool request.")
        turn_id = params.get("turnId")
        call_id = params.get("callId")
        dynamic_name = params.get("tool")
        arguments = params.get("arguments")
        if not all(isinstance(value, str) and value for value in (turn_id, call_id, dynamic_name)):
            return self._dynamic_tool_failure("Invalid Android tool request.")
        if not isinstance(arguments, dict):
            return self._dynamic_tool_failure("Android tool arguments must be a JSON object.")
        run = self.get_run(turn_id)
        if run is None:
            deadline = time.monotonic() + 5.0
            while run is None and time.monotonic() < deadline:
                time.sleep(0.01)
                run = self.get_run(turn_id)
        if run is None:
            return self._dynamic_tool_failure("The Android interaction is no longer active.")
        with self._runs_lock:
            session = self._sessions.get(run.session_id)
        if session is None:
            return self._dynamic_tool_failure("The Android session is unavailable.")
        tool = session.tool_by_dynamic_name(dynamic_name)
        if tool is None:
            return self._dynamic_tool_failure(f"Unknown Android tool: {dynamic_name}")
        run.append(
            "tool_call",
            call_id=call_id,
            tool_name=tool.name,
            tool_version=tool.version,
            arguments=arguments,
        )
        deadline = time.monotonic() + 120.0
        with run.condition:
            while call_id not in run.tool_results and not run.terminal:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    break
                run.condition.wait(timeout=min(10.0, remaining))
            result = run.tool_results.pop(call_id, None)
        if result is None:
            return self._dynamic_tool_failure("Android tool execution timed out or was cancelled.")
        success = result.get("success") is True
        payload = result.get("output") if success else {"error": result.get("error", "Android tool failed")}
        return {
            "contentItems": [
                {
                    "type": "inputText",
                    "text": json.dumps(payload, separators=(",", ":"), ensure_ascii=False),
                }
            ],
            "success": success,
        }

    @staticmethod
    def _dynamic_tool_failure(message: str) -> dict[str, Any]:
        return {
            "contentItems": [{"type": "inputText", "text": message}],
            "success": False,
        }

    def _on_notification(self, notification: dict[str, Any]) -> None:
        params = notification.get("params")
        if not isinstance(params, dict):
            return
        turn_id = params.get("turnId")
        if not isinstance(turn_id, str):
            turn = params.get("turn")
            turn_id = turn.get("id") if isinstance(turn, dict) else None
        if not isinstance(turn_id, str):
            return
        with self._runs_lock:
            run = self._runs.get(turn_id)
            if run is None:
                self._pending_notifications.setdefault(turn_id, []).append(notification)
                return
        self._apply_notification(run, notification)

    @staticmethod
    def _apply_notification(run: RunState, notification: dict[str, Any]) -> None:
        method = notification.get("method")
        params = notification.get("params") or {}
        if method == "item/agentMessage/delta":
            delta = params.get("delta")
            if isinstance(delta, str) and delta:
                run.append("text_delta", text=delta)
        elif method == "turn/completed":
            turn = params.get("turn")
            status = turn.get("status") if isinstance(turn, dict) else None
            if status == "completed":
                run.append("completed")
            elif status == "interrupted":
                run.append("cancelled")
            else:
                error = turn.get("error") if isinstance(turn, dict) else None
                message = error.get("message") if isinstance(error, dict) else None
                run.append("failed", error=message or "Codex turn failed")
        elif method == "error" and params.get("willRetry") is False:
            message = params.get("message")
            run.append("failed", error=message if isinstance(message, str) else "Codex error")

class CompanionHttpServer(ThreadingHTTPServer):
    daemon_threads = True
    def __init__(self, address: tuple[str, int], *, service: CompanionService, token: str) -> None:
        super().__init__(address, Handler)
        self.service = service
        self.token = token

class Handler(BaseHTTPRequestHandler):
    server: CompanionHttpServer
    server_version = "JeanCalculCompanion/0.1"

    def log_message(self, format: str, *args: Any) -> None:
        return

    def do_GET(self) -> None:
        if not self._authenticate():
            return
        parsed = urlparse(self.path)
        if parsed.path == "/v1/status":
            self._json(HTTPStatus.OK, {
                "protocolVersion": PROTOCOL_VERSION,
                "state": "ready" if self.server.service.codex.alive else "unavailable",
                "auth": "chatgpt",
            })
            return
        parts = parsed.path.strip("/").split("/")
        if len(parts) == 6 and parts[:2] == ["v1", "sessions"] and parts[3] == "runs" and parts[5] == "events":
            session_id, turn_id = parts[2], parts[4]
            run = self.server.service.get_run(turn_id)
            if run is None or run.session_id != session_id:
                self._json(HTTPStatus.NOT_FOUND, {"error": "unknown_run"})
                return
            try:
                after = max(0, int(parse_qs(parsed.query).get("after", ["0"])[0]))
            except ValueError:
                self._json(HTTPStatus.BAD_REQUEST, {"error": "invalid_after"})
                return
            self._stream_events(run, after)
            return
        self._json(HTTPStatus.NOT_FOUND, {"error": "not_found"})

    def do_POST(self) -> None:
        if not self._authenticate():
            return
        parts = urlparse(self.path).path.strip("/").split("/")
        try:
            if parts == ["v1", "sessions"]:
                body = self._read_json()
                tools = ToolSpec.parse_many(body.get("tools"))
                session_id = self.server.service.create_session(tools)
                self._json(HTTPStatus.CREATED, {"sessionId": session_id, "resumable": True})
                return
            if len(parts) == 4 and parts[:2] == ["v1", "sessions"] and parts[3] == "resume":
                body = self._read_json()
                tools = ToolSpec.parse_many(body.get("tools"))
                session_id = self.server.service.resume_session(parts[2], tools)
                self._json(HTTPStatus.OK, {"sessionId": session_id, "resumable": True})
                return
            if len(parts) == 4 and parts[:2] == ["v1", "sessions"] and parts[3] == "runs":
                body = self._read_json()
                request_id = body.get("requestId")
                text = body.get("text")
                tools = ToolSpec.parse_many(body.get("tools"))
                if not isinstance(request_id, str) or not request_id.strip() or not isinstance(text, str) or not text.strip():
                    self._json(HTTPStatus.BAD_REQUEST, {"error": "invalid_request"})
                    return
                run = self.server.service.start_run(parts[2], request_id, text, tools)
                self._json(HTTPStatus.ACCEPTED, {"runId": run.turn_id, "status": "running"})
                return
            if len(parts) == 6 and parts[:2] == ["v1", "sessions"] and parts[3] == "runs" and parts[5] == "tool-results":
                body = self._read_json()
                call_id = body.get("callId")
                if not isinstance(call_id, str) or not call_id.strip() or not isinstance(body.get("success"), bool):
                    self._json(HTTPStatus.BAD_REQUEST, {"error": "invalid_tool_result"})
                    return
                self.server.service.submit_tool_result(parts[2], parts[4], call_id, body)
                self._json(HTTPStatus.ACCEPTED, {"status": "accepted"})
                return
            if len(parts) == 6 and parts[:2] == ["v1", "sessions"] and parts[3] == "runs" and parts[5] == "cancel":
                self.server.service.cancel(parts[2], parts[4])
                self._json(HTTPStatus.ACCEPTED, {"status": "cancelling"})
                return
        except JsonRpcError as error:
            self._json(HTTPStatus.BAD_GATEWAY, {"error": "codex_protocol", "message": str(error)})
            return
        except CompanionError as error:
            self._json(HTTPStatus.SERVICE_UNAVAILABLE, {"error": "codex_unavailable", "message": str(error)})
            return
        self._json(HTTPStatus.NOT_FOUND, {"error": "not_found"})

    def _authenticate(self) -> bool:
        supplied = self.headers.get("Authorization", "")
        if not hmac.compare_digest(supplied, f"Bearer {self.server.token}"):
            self._json(HTTPStatus.UNAUTHORIZED, {"error": "unauthorized"})
            return False
        protocol_version = self.headers.get("X-Jean-Calcul-Protocol")
        if protocol_version != PROTOCOL_VERSION:
            self._json(
                HTTPStatus.UPGRADE_REQUIRED,
                {"error": "incompatible_protocol", "supportedProtocolVersion": PROTOCOL_VERSION},
            )
            return False
        return True

    def _read_json(self) -> dict[str, Any]:
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError as error:
            raise CompanionError("invalid content length") from error
        if length <= 0 or length > 1_000_000:
            raise CompanionError("invalid request body size")
        try:
            value = json.loads(self.rfile.read(length).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise CompanionError("invalid JSON body") from error
        if not isinstance(value, dict):
            raise CompanionError("JSON body must be an object")
        return value

    def _json(self, status: HTTPStatus, payload: dict[str, Any]) -> None:
        encoded = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
        self.send_response(status.value)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def _stream_events(self, run: RunState, after: int) -> None:
        self.send_response(HTTPStatus.OK.value)
        self.send_header("Content-Type", "application/x-ndjson; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "close")
        self.end_headers()
        cursor = after
        deadline = time.monotonic() + 300
        try:
            while time.monotonic() < deadline:
                with run.condition:
                    snapshot = [event for event in run.events if event.sequence > cursor]
                    while not snapshot and not run.terminal:
                        remaining = deadline - time.monotonic()
                        if remaining <= 0:
                            break
                        run.condition.wait(timeout=min(15.0, remaining))
                        snapshot = [event for event in run.events if event.sequence > cursor]
                    terminal = run.terminal
                for event in snapshot:
                    self.wfile.write(event.as_json().encode("utf-8") + b"\n")
                    self.wfile.flush()
                    cursor = event.sequence
                if terminal and not any(event.sequence > cursor for event in run.events):
                    self.server.service.release_run(run.turn_id)
                    return
        except (BrokenPipeError, ConnectionResetError):
            return

def config_path() -> Path:
    root = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config"))
    return root / "jean-calcul" / "companion.json"

def load_or_create_token() -> str:
    path = config_path()
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(path.parent, 0o700)
    if path.exists():
        os.chmod(path, 0o600)
        data = json.loads(path.read_text(encoding="utf-8"))
        token = data.get("pairingToken")
        if isinstance(token, str) and len(token) >= 32:
            return token
        raise CompanionError(f"invalid companion config: {path}")
    token = secrets.token_urlsafe(32)
    payload = json.dumps({"pairingToken": token}, indent=2) + "\n"
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as handle:
        handle.write(payload)
    return token

def resolve_codex(binary: str) -> str:
    resolved = shutil.which(binary)
    if resolved is None:
        raise CompanionError("Codex CLI not found. Install Codex before starting the companion.")
    return resolved

def companion_workspace_path() -> Path:
    root = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local" / "share"))
    path = root / "jean-calcul" / "codex-workspace"
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(path, 0o700)
    return path

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Jean Calcul companion for ChatGPT-authenticated Codex")
    parser.add_argument("--codex", default=os.environ.get("CODEX_BIN", "codex"))
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("login")
    sub.add_parser("logout")
    sub.add_parser("status")
    serve = sub.add_parser("serve")
    serve.add_argument("--host", default="127.0.0.1")
    serve.add_argument("--port", type=int, default=DEFAULT_PORT)
    serve.add_argument("--cwd", default=None, help="Explicit Codex read-only working directory")
    return parser

def main() -> int:
    args = build_parser().parse_args()
    try:
        codex_bin = resolve_codex(args.codex)
        if args.command == "login":
            return subprocess.call([codex_bin, "login", "--device-auth"])
        if args.command == "logout":
            return subprocess.call([codex_bin, "logout"])
        if args.command == "status":
            app_server = CodexAppServer(codex_bin, str(companion_workspace_path()))
            try:
                app_server.start()
                print("Codex authenticated with a ChatGPT subscription.")
                print(f"Companion pairing token: {load_or_create_token()}")
                return 0
            finally:
                app_server.close()
        if args.command == "serve":
            if args.host not in {"127.0.0.1", "localhost"}:
                raise CompanionError("Remote plaintext binding is disabled. Use loopback plus adb reverse or a trusted tunnel.")
            token = load_or_create_token()
            cwd = args.cwd or str(companion_workspace_path())
            app_server = CodexAppServer(codex_bin, cwd)
            app_server.start()
            server = CompanionHttpServer((args.host, args.port), service=CompanionService(app_server), token=token)
            print(f"Jean Calcul companion listening on http://{args.host}:{args.port}")
            print(f"Pairing token: {token}")
            print("Android over USB: adb reverse tcp:43120 tcp:43120")
            try:
                server.serve_forever(poll_interval=0.5)
            except KeyboardInterrupt:
                pass
            finally:
                server.server_close()
                app_server.close()
            return 0
    except (CompanionError, OSError, subprocess.SubprocessError, json.JSONDecodeError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    return 2

if __name__ == "__main__":
    raise SystemExit(main())
