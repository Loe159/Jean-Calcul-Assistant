package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

internal object AgentTerminalToolCatalog {
    val names = setOf("terminal", "terminal_job", "terminal_daemon")

    fun appendTo(tools: JSONArray) {
        tools.put(
            AgentToolSchema.function(
                name = "terminal",
                description = "Run a shell command by supplying command. Default environment, identity and working directory are in this run's context." +
                    "For persistent cd or environment state, use action=open, execute with session_id, then action=close." +
                    "One-shot processes are cleaned up after commands; do not use & for background work. Use terminal_job for long/background commands and terminal_daemon for cross-run services." +
                    "Prefer dedicated file tools for reading, writing, editing and searching known files.",
                parameters = parameters(
                    commandProperties()
                        .put("action", action("exec", "open", "close", "check_root")
                            .put("description", "Default exec runs a command; open creates a persistent shell; close closes it. check_root runs the fixed su -c 'id -u' to verify/update root without command, and may show a system grant prompt."))
                        .put("session_id", text("Session ID from open. exec keeps its directory/environment/identity; use cd within the session to change directories.")),
                ),
            ),
        )
        tools.put(
            AgentToolSchema.function(
                name = "terminal_job",
                description = "Start a background command that can outlive this reply. start returns job_id immediately; completion/failure sends a notification. completion_notifications_enabled reports notification availability." +
                    "list inspects jobs; read returns status/logs; wait only when the result is needed; stop terminates." +
                    "Logs are saved in output_path. Use read_file pagination when the preview is truncated." +
                    "Background jobs use a separate shell, not the terminal session. Continue other work or reply without polling.",
                parameters = parameters(
                    executionProperties()
                        .put("action", action("start", "list", "read", "wait", "stop"))
                        .put("command", text("Complete command for start"))
                        .put("description", text("Optional short task description for lists and completion notifications"))
                        .put("timeout_ms", integer("Execution limit for start, default 600000 ms; 0 means no timeout"))
                        .put("job_id", text("Task ID from start/list; used by read/wait/stop"))
                        .put("active_only", bool("Only running jobs in list; default true"))
                        .put("max_bytes", integer("Log tail bytes for read, default 16000, maximum 65536. Full logs are in output_path."))
                        .put("wait_ms", integer("Maximum wait, default 30000 ms, maximum 60000. A wait timeout does not stop the command.")),
                    "action",
                ),
            ),
        )
        tools.put(
            AgentToolSchema.function(
                name = "terminal_daemon",
                description = "Manage background services that persist across agent runs, such as web servers. start returns task_id." +
                    "list inspects tasks, logs reads logs and stop terminates. Services are not reclaimed when the current run ends," +
                    "but may still end on exit, system reclamation, permission changes or device restart.",
                parameters = parameters(
                    executionProperties()
                        .put("action", action("start", "list", "logs", "stop"))
                        .put("command", text("Command for start, using shell syntax supported by the actual environment"))
                        .put("task_id", text("Task ID from start/list; used by logs/stop")),
                    "action",
                ),
            ),
        )
    }

    private fun executionProperties(): JSONObject = JSONObject()
        .put("environment", text("Environnement Android uniquement ; les environnements Linux sont exclus.")
            .put("enum", JSONArray(listOf("android"))))
        .put("identity", text("Optional host identity. Confirmed Android root defaults to root, otherwise user. Do not first try user or repeat root checks after authorization. Explicit user uses the Jean-Calcul app UID. Simulated Linux root does not grant Android privileges.")
            .put("enum", JSONArray(listOf("user", "root"))))
        .put("cwd", text("Optional working directory. Defaults to the environment workspace; relative paths resolve there."))

    private fun commandProperties(): JSONObject = executionProperties()
        .put("command", text("Command using shell syntax supported by the actual environment"))
        .put("timeout_ms", integer("Command timeout, default 30000 ms, maximum 180000. Timeout terminates the command and closes persistent command sessions."))
        .put("merge_stderr", bool("Merge stderr into stdout; default false"))

    private fun parameters(properties: JSONObject, vararg required: String): JSONObject = JSONObject()
        .put("type", "object")
        .put("properties", properties)
        .put("required", JSONArray(required.toList()))
        .put("additionalProperties", false)

    private fun action(vararg values: String): JSONObject = text("Action to execute")
        .put("enum", JSONArray(values.toList()))

    private fun text(description: String) = JSONObject().put("type", "string").put("description", description)
    private fun bool(description: String) = JSONObject().put("type", "boolean").put("description", description)
    private fun integer(description: String) = JSONObject().put("type", "integer").put("description", description)
}
