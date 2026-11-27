package io.github.mangi.eta.agent.majordome

import io.github.mangi.eta.agent.confirm.ActionRisk
import io.github.mangi.eta.agent.confirm.ConfirmationDecision
import io.github.mangi.eta.agent.confirm.ConfirmationRequest
import io.github.mangi.eta.agent.device.notifications.remote.MastraHttpException
import io.github.mangi.eta.agent.device.notifications.remote.MastraPairing
import io.github.mangi.eta.agent.device.notifications.remote.MastraSignedTransport
import io.github.mangi.eta.agent.model.AgentModelClient
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MajordomeToolTest {
    private val secret = Base64.getUrlEncoder().encodeToString(ByteArray(32))
    private val pairing = MastraPairing("https://homelab.test", "jc_test", secret)

    @Test
    fun threadIdsAreStablePrivateAndAcceptedByMastra() {
        val first = MajordomeTool.threadId("conversation-42")
        assertEquals(first, MajordomeTool.threadId("conversation-42"))
        assertTrue(first != MajordomeTool.threadId("conversation-43"))
        assertTrue(Regex("^[A-Za-z0-9_-]{1,64}$").matches(first))
        assertFalse(first.contains("conversation"))
    }

    @Test
    fun schemaTakesOnlyABoundedRequest() {
        val function = MajordomeTool.schema().getJSONObject("function")
        assertEquals("ask_majordome", function.getString("name"))
        val properties = function.getJSONObject("parameters").getJSONObject("properties")
        assertEquals(4_000, properties.getJSONObject("request").getInt("maxLength"))
        assertFalse(properties.has("decision"))
    }

    @Test
    fun postsASignedRequestOnTheConversationThreadAndRelaysTheAnswer() {
        var seenBody = ""
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
            seenBody = body
            assertEquals("POST", request.method)
            assertEquals("/mobile/v1/assistant/messages", request.url.encodedPath)
            assertEquals(null, request.url.query)
            assertEquals(
                MastraSignedTransport.sign(secret, "POST", request.url.encodedPath, request.header("X-JC-Timestamp")!!,
                    request.header("X-JC-Nonce")!!, body),
                request.header("X-JC-Signature"),
            )
            chain.respond(200, """{"schemaVersion":1,"threadId":"x","status":"COMPLETED","text":"Lasagnes prévues jeudi soir.","extra":true}""")
        }.build()
        val executor = MajordomeToolExecutor("conversation-42", { pairing }, { ConfirmationDecision.DECLINED }) { MastraSignedTransport(it, http) }

        val result = executor.execute(call("""{"request":"  Planifie des lasagnes jeudi soir pour 4  "}"""))

        val sent = JSONObject(seenBody)
        assertEquals(MajordomeTool.threadId("conversation-42"), sent.getString("threadId"))
        assertEquals("Planifie des lasagnes jeudi soir pour 4", sent.getString("message"))
        val json = JSONObject(result.content)
        assertTrue(json.getBoolean("ok"))
        assertEquals("COMPLETED", json.getString("status"))
        assertEquals("Lasagnes prévues jeudi soir.", json.getString("answer"))
        assertTrue(result.sensitive)
    }

    private fun approvalHttp(bodies: MutableList<Pair<String, JSONObject>>, approval: String) =
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            bodies += request.url.encodedPath to JSONObject(Buffer().also { request.body?.writeTo(it) }.readUtf8())
            if (request.url.encodedPath == MajordomeTool.PATH) {
                chain.respond(200, """{"schemaVersion":1,"threadId":"x","status":"APPROVAL_REQUIRED","text":"En attente","approval":$approval}""")
            } else {
                chain.respond(200, """{"schemaVersion":1,"threadId":"x","status":"COMPLETED","text":"Fait."}""")
            }
        }.build()

    private fun executor(http: OkHttpClient, decision: ConfirmationDecision, asked: MutableList<ConfirmationRequest> = mutableListOf()) =
        MajordomeToolExecutor("conversation-approval", { pairing }, { asked += it; decision }) { MastraSignedTransport(it, http) }

    @Test
    fun anApprovedActionIsSentAfterThePopupAndTheModelNeverSeesTheApprovalId() {
        val bodies = mutableListOf<Pair<String, JSONObject>>()
        val asked = mutableListOf<ConfirmationRequest>()
        val http = approvalHttp(bodies, """{"id":"apr_42","toolName":"deleteRecipe","summary":"Supprimer la recette « Pâtes »."}""")
        val result = executor(http, ConfirmationDecision.APPROVED, asked).execute(call("""{"request":"Supprime la recette Pâtes"}"""))

        assertEquals(1, asked.size)
        assertEquals(ActionRisk.HIGH, asked.single().risk)
        val json = JSONObject(result.content)
        assertEquals("COMPLETED", json.getString("status"))
        assertFalse(result.content.contains("apr_42"))
        val (path, body) = bodies.last()
        assertEquals(MajordomeTool.APPROVALS_PATH, path)
        assertEquals(MajordomeTool.threadId("conversation-approval"), body.getString("threadId"))
        assertEquals("apr_42", body.getString("approvalId"))
        assertEquals("APPROVE", body.getString("decision"))
    }

    @Test
    fun aServerRiskFieldRaisesTheConfirmationLevel() {
        val asked = mutableListOf<ConfirmationRequest>()
        val http = approvalHttp(mutableListOf(), """{"id":"apr_1","summary":"Créer « Dentiste »","risk":"high"}""")
        executor(http, ConfirmationDecision.APPROVED, asked).execute(call("""{"request":"Ajoute le dentiste"}"""))
        assertEquals(ActionRisk.HIGH, asked.single().risk)
    }

    @Test
    fun aRefusedTimedOutOrUnshownConfirmationDeclinesTheActionOnTheHomelab() {
        listOf(
            ConfirmationDecision.DECLINED to "USER_DECLINED",
            ConfirmationDecision.TIMED_OUT to "CONFIRMATION_TIMED_OUT",
            ConfirmationDecision.UNAVAILABLE to "CONFIRMATION_UNAVAILABLE",
        ).forEach { (decision, code) ->
            val bodies = mutableListOf<Pair<String, JSONObject>>()
            val http = approvalHttp(bodies, """{"id":"apr_7","summary":"Créer « Dentiste »"}""")
            val json = JSONObject(executor(http, decision).execute(call("""{"request":"Ajoute le dentiste"}""")).content)
            assertFalse(json.getBoolean("ok"))
            assertEquals(code, json.getString("code"))
            assertEquals("DECLINE", bodies.last().second.getString("decision"))
        }
    }

    @Test
    fun anApprovalRequiredReplyWithoutUsableIdIsRejectedWithoutPopup() {
        val asked = mutableListOf<ConfirmationRequest>()
        val http = approvalHttp(mutableListOf(), """{"summary":"x"}""")
        val json = JSONObject(executor(http, ConfirmationDecision.APPROVED, asked).execute(call("""{"request":"x"}""")).content)
        assertEquals("INVALID_RESPONSE", json.getString("code"))
        assertTrue(asked.isEmpty())
    }

    @Test
    fun unknownStatusAndFailuresBecomeExplicitErrors() {
        assertEquals("INVALID_RESPONSE", MajordomeTool.result("""{"status":"LATER","text":"?"}""").getString("code"))
        assertEquals("TIMEOUT", MajordomeTool.failure(MastraHttpException(504, "TIMEOUT")).getString("code"))
        assertEquals("NOT_AUTHORIZED", MajordomeTool.failure(MastraHttpException(401, "BAD_SIGNATURE")).getString("code"))
        assertEquals("UNSUPPORTED", MajordomeTool.failure(MastraHttpException(404, null)).getString("code"))
        assertEquals("AI_UNAVAILABLE", MajordomeTool.failure(MastraHttpException(503, "AI_UNAVAILABLE")).getString("code"))
        assertEquals("TIMEOUT", MajordomeTool.failure(SocketTimeoutException()).getString("code"))
        assertEquals("OFFLINE", MajordomeTool.failure(UnknownHostException()).getString("code"))
        assertEquals("APPROVAL_NOT_FOUND", MajordomeTool.failure(MastraHttpException(404, "APPROVAL_NOT_FOUND")).getString("code"))
        assertEquals("APPROVAL_EXPIRED", MajordomeTool.failure(MastraHttpException(410, "APPROVAL_EXPIRED")).getString("code"))
    }

    @Test
    fun missingPairingOrRequestNeverCallsTheHomelab() {
        val http = OkHttpClient.Builder().addInterceptor { error("no network call expected") }.build()
        val unpaired = MajordomeToolExecutor("c", { null }, { ConfirmationDecision.DECLINED }) { MastraSignedTransport(it, http) }
        assertFalse(unpaired.available())
        assertEquals("NOT_PAIRED", JSONObject(unpaired.execute(call("""{"request":"Qu'y a-t-il au frigo ?"}""")).content).getString("code"))
        val paired = MajordomeToolExecutor("c", { pairing }, { ConfirmationDecision.DECLINED }) { MastraSignedTransport(it, http) }
        assertEquals("INVALID_ARGUMENT", JSONObject(paired.execute(call("""{"request":"   "}""")).content).getString("code"))
        assertEquals("INVALID_ARGUMENT", JSONObject(paired.execute(call(JSONObject().put("request", "a".repeat(4_001)).toString())).content).getString("code"))
    }

    private fun call(arguments: String) = AgentModelClient.ToolCall(id = "call_1", name = MajordomeTool.NAME, argumentsJson = arguments)

    private fun okhttp3.Interceptor.Chain.respond(code: Int, body: String): Response = Response.Builder()
        .request(request())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("OK")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()
}
