package io.github.mangi.eta.agent.phone

import android.content.Context
import android.os.Looper
import android.os.Process
import kotlin.system.exitProcess
import org.json.JSONObject

/** 由 Eta 的已授权 Root 执行器启动；仅接受固定业务动作，正文只走 stdin。 */
internal object PhoneCommandMain {
    const val MARKER = "ETA_PHONE_RESULT:"

    @JvmStatic
    @Suppress("DEPRECATION")
    fun main(arguments: Array<String>) {
        val result =
            try {
                if (Process.myUid() != 0) PhoneOperation.error("ROOT_REQUIRED", "Root identity required")
                val input = System.`in`.readNBytes(128 * 1024 + 1)
                if (input.size > 128 * 1024) PhoneOperation.error("PHONE_INPUT_LIMIT", "Request exceeds the size limit")
                val request = JSONObject(input.toString(Charsets.UTF_8))
                if (request.optInt("version") != 1)
                    PhoneOperation.error("PHONE_PROTOCOL_UNSUPPORTED", "Incompatible request protocol")
                val tool = PhoneOperation.text(request, "tool", true, 80)!!
                if (tool !in PhoneOperation.tools && tool != "capabilities")
                    PhoneOperation.error("UNKNOWN_TOOL", "Unknown first-party app action")
                val userId = PhoneOperation.integer(request, "user_id", 0, 21474).toInt()
                if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
                val activityThread = Class.forName("android.app.ActivityThread")
                val thread = activityThread.getMethod("systemMain").invoke(null)
                val context = activityThread.getMethod("getSystemContext").invoke(thread) as Context
                val access = RootPhoneProviderAccess(userId)
                if (tool == "capabilities") {
                    val sources = JSONObject()
                    val queries = NativePersonalQueries(access, userId)
                    for (name in listOf("search_media", "search_notes", "search_messages")) {
                        sources.put(
                            name,
                            try {
                                queries.probe(name)
                            } catch (_: Exception) {
                                false
                            },
                        )
                    }
                    val calendar =
                        try {
                            access
                                .query(
                                    android.net.Uri.parse(
                                        "content://$userId@com.android.calendar/events"
                                    ),
                                    arrayOf("_id"),
                                    "0",
                                    null,
                                    null,
                                )
                                ?.use { true } ?: false
                        } catch (_: Exception) {
                            false
                        }
                    PhoneOperation.ok("capabilities")
                        .put("version", 1)
                        .put("caller_package", access.callerPackage)
                        .put("calendar", calendar)
                        .put("sources", sources)
                } else
                    PhoneOperations.execute(
                        context,
                        tool,
                        request.getJSONObject("arguments"),
                        userId,
                        access,
                    )
            } catch (failure: PhoneOperationFailure) {
                PhoneOperation.failure(failure.code, failure.message ?: "Invalid request")
            } catch (_: Throwable) {
                PhoneOperation.failure("PHONE_BRIDGE_UNAVAILABLE", "System cannot start the native app action channel")
            }
        val encoded = result.toString()
        println(
            MARKER +
                if (encoded.toByteArray(Charsets.UTF_8).size <= 512 * 1024) encoded
                else
                    PhoneOperation.failure("PHONE_OUTPUT_LIMIT", "Result exceeds the size limit. Query current state first.")
                        .put("status", "unconfirmed")
        )
        exitProcess(0)
    }
}
