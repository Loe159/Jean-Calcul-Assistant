package io.github.mangi.eta.agent.majordome

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
    fun schemaTakesABoundedRequestOrAnApprovalDecision() {
        val function = MajordomeTool.schema().getJSONObject("function")
        assertEquals("ask_majordome", function.getString("name"))
        val properties = function.getJSONObject("parameters").getJSONObject("properties")
        assertEquals(4_000, properties.getJSONObject("request").getInt("maxLength"))
        val decisions = properties.getJSONObject("decision").getJSONArray("enum")
        assertEquals(listOf("approve", "decline"), (0 until decisions.length()).map(decisions::getString))
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
        val executor = MajordomeToolExecutor("conversation-42", { pairing }) { MastraSignedTransport(it, http) }

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

    @Test
    fun approvalRequiredIsReportedAsNotDoneYet() {
        val json = MajordomeTool.result(
            """{"status":"APPROVAL_REQUIRED","text":"En attente","approval":{"id":"apr_1","toolName":"createCalendarEvent","summary":"Dentiste, jeudi 10:00–10:30"}}""",
        )
        assertTrue(json.getBoolean("ok"))
        assertEquals("APPROVAL_REQUIRED", json.getString("status"))
        assertEquals("Dentiste, jeudi 10:00–10:30", json.getString("pending_action"))
        assertTrue(json.getString("message").contains("Nothing has been done yet"))
        assertFalse(json.toString().contains("apr_1"))
    }

    @Test
    fun anApprovalIsSentOnlyAfterTheUserRepliedInALaterTurn() {
        val bodies = mutableListOf<Pair<String, JSONObject>>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = JSONObject(Buffer().also { request.body?.writeTo(it) }.readUtf8())
            bodies += request.url.encodedPath to body
            if (request.url.encodedPath == MajordomeTool.PATH) {
                chain.respond(200, """{"schemaVersion":1,"threadId":"x","status":"APPROVAL_REQUIRED","text":"En attente","approval":{"id":"apr_42","toolName":"deleteRecipe","summary":"Supprimer la recette « Pâtes » de la bibliothèque."}}""")
            } else {
                chain.respond(200, """{"schemaVersion":1,"threadId":"x","status":"COMPLETED","text":"Recette supprimée."}""")
            }
        }.build()
        val conversation = "conversation-approval"
        val requestTurn = MajordomeToolExecutor(conversation, { pairing }) { MastraSignedTransport(it, http) }

        val pending = JSONObject(requestTurn.execute(call("""{"request":"Supprime la recette Pâtes"}""")).content)
        assertEquals("APPROVAL_REQUIRED", pending.getString("status"))
        val sameTurn = JSONObject(requestTurn.execute(call("""{"decision":"approve"}""")).content)
        assertEquals("USER_CONFIRMATION_REQUIRED", sameTurn.getString("code"))
        assertEquals(1, bodies.size)

        val replyTurn = MajordomeToolExecutor(conversation, { pairing }) { MastraSignedTransport(it, http) }
        val approved = JSONObject(replyTurn.execute(call("""{"decision":"approve"}""")).content)
        assertEquals("COMPLETED", approved.getString("status"))
        assertEquals("Recette supprimée.", approved.getString("answer"))
        val (path, body) = bodies.last()
        assertEquals(MajordomeTool.APPROVALS_PATH, path)
        assertEquals(MajordomeTool.threadId(conversation), body.getString("threadId"))
        assertEquals("apr_42", body.getString("approvalId"))
        assertEquals("APPROVE", body.getString("decision"))

        val again = JSONObject(replyTurn.execute(call("""{"decision":"approve"}""")).content)
        assertEquals("NO_PENDING_ACTION", again.getString("code"))
        assertEquals(2, bodies.size)
    }

    @Test
    fun aDeclineCanBeSentInTheSameTurnAndANewRequestForgetsThePendingAction() {
        val bodies = mutableListOf<JSONObject>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            bodies += JSONObject(Buffer().also { request.body?.writeTo(it) }.readUtf8())
            if (request.url.encodedPath == MajordomeTool.PATH) {
                chain.respond(200, """{"status":"APPROVAL_REQUIRED","text":"","approval":{"id":"apr_7","summary":"Créer « Dentiste »"}}""")
            } else {
                chain.respond(200, """{"status":"COMPLETED","text":"Rendez-vous non créé."}""")
            }
        }.build()
        val executor = MajordomeToolExecutor("conversation-decline", { pairing }) { MastraSignedTransport(it, http) }
        executor.execute(call("""{"request":"Ajoute le dentiste jeudi"}"""))
        val declined = JSONObject(executor.execute(call("""{"decision":"decline"}""")).content)
        assertEquals("COMPLETED", declined.getString("status"))
        assertEquals("DECLINE", bodies.last().getString("decision"))

        executor.execute(call("""{"request":"Ajoute le dentiste jeudi"}"""))
        executor.execute(call("""{"request":"Finalement, liste mes recettes"}"""))
        val later = MajordomeToolExecutor("conversation-decline", { pairing }) { MastraSignedTransport(it, http) }
        // The second request also returned APPROVAL_REQUIRED in this stub: only that newest action is pending.
        later.execute(call("""{"decision":"approve"}"""))
        assertEquals("apr_7", bodies.last().getString("approvalId"))
        assertEquals("INVALID_ARGUMENT", JSONObject(later.execute(call("""{"request":"x","decision":"approve"}""")).content).getString("code"))
        assertEquals("INVALID_ARGUMENT", JSONObject(later.execute(call("""{"decision":"maybe"}""")).content).getString("code"))
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
        val unpaired = MajordomeToolExecutor("c", { null }) { MastraSignedTransport(it, http) }
        assertFalse(unpaired.available())
        assertEquals("NOT_PAIRED", JSONObject(unpaired.execute(call("""{"request":"Qu'y a-t-il au frigo ?"}""")).content).getString("code"))
        val paired = MajordomeToolExecutor("c", { pairing }) { MastraSignedTransport(it, http) }
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
