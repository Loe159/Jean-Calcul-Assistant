package io.github.mangi.eta.food

import io.github.mangi.eta.agent.device.notifications.remote.MastraHttpException
import io.github.mangi.eta.agent.device.notifications.remote.MastraPairing
import io.github.mangi.eta.agent.device.notifications.remote.MastraSignedTransport
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodClientTest {
    private val secret = Base64.getUrlEncoder().encodeToString(ByteArray(32))
    private val pairing = MastraPairing("https://example.test", "jc_test", secret)

    @Test
    fun signsAllMethodsAndUsesEncodedPathsWithNoQuery() = runBlocking {
        val seen = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
            val path = request.url.encodedPath
            assertEquals(null, request.url.query)
            assertEquals("jc_test", request.header("X-JC-Device"))
            assertEquals("1234", request.header("X-JC-Timestamp"))
            assertEquals(
                MastraSignedTransport.sign(secret, request.method, path, "1234", request.header("X-JC-Nonce")!!, body),
                request.header("X-JC-Signature"),
            )
            seen += "${request.method} $path"
            when (request.method) {
                "POST" -> chain.response(200, foodFixture("job"))
                "PATCH" -> chain.response(200, FoodContract.json.encodeToString(
                    RecipeDetailResponse(1, recipeFixture(), RecipeLibrary(true, null, "", 0, null)),
                ))
                "DELETE" -> if (path.contains("/recipes/")) chain.response(200, "{\"schemaVersion\":1}")
                    else chain.response(200, foodFixture("preferences"))
                else -> chain.response(200, foodFixture("preferences"))
            }
        }.build()
        val client = FoodClient(pairing, http) { 1234 }
        val preferences = client.preferences()
        client.updatePreferences(UpdatePreferencesRequest(preferences.declared))
        client.cancelJob("job_a")
        assertTrue(client.updateLibrary("rcp_a", UpdateRecipeLibraryRequest(favorite = true)).library.favorite)
        assertEquals(preferences, client.deleteInferredPreference("cuisine/été ?"))
        assertEquals(1, client.archiveRecipe("rcp_a").schemaVersion)
        assertEquals(
            listOf(
                "GET /mobile/v1/food/preferences",
                "PUT /mobile/v1/food/preferences",
                "POST /mobile/v1/food/jobs/job_a/cancel",
                "PATCH /mobile/v1/food/recipes/rcp_a/library",
                "DELETE /mobile/v1/food/preferences/inferred/cuisine%2F%C3%A9t%C3%A9%20%3F",
                "DELETE /mobile/v1/food/recipes/rcp_a",
            ),
            seen,
        )
        assertThrows(IllegalArgumentException::class.java) {
            MastraSignedTransport(pairing, http).execute("GET", "/mobile/v1/food/pantry?filter=1")
        }
        Unit
    }

    @Test
    fun mapsSignedApiErrorsToSharedHttpException() {
        val http = OkHttpClient.Builder().addInterceptor {
            it.response(503, "{\"error\":\"AI_DISABLED\",\"message\":\"Désactivée\"}")
        }.build()
        val error = assertThrows(MastraHttpException::class.java) { runBlocking { FoodClient(pairing, http).recipes() } }
        assertEquals("AI_DISABLED", error.code)
        assertEquals(503, error.status)
        assertTrue(!error.permanent)
    }
}

internal fun Interceptor.Chain.response(code: Int, body: String): Response = Response.Builder()
    .request(request()).protocol(Protocol.HTTP_1_1).code(code).message("Test").body(body.toResponseBody()).build()
