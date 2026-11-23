package io.github.mangi.eta.ui.screens.home.brief.cards

import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PeugeotCarImageTest {
    private val url = "https://images.example.invalid/car.png".toHttpUrl()

    @Test
    fun loadsImageBytesWithoutSessionHeaders() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals(url, request.url)
            assertNull(request.header("cvs-token"))
            assertNull(request.header("mym-access-token"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
                .message("Synthetic response").body("image".toResponseBody()).build()
        }.build()
        assertEquals("image", loadPeugeotCarImage(url, client, ByteArray::decodeToString))
        assertNull(loadPeugeotCarImage(url, client) { null })
    }

    @Test
    fun selectsBundledFallbackWhenImageIsMissingOrUnavailable() {
        val client = OkHttpClient.Builder().addInterceptor { throw IOException("Synthetic outage") }.build()
        val decode = { _: ByteArray -> error("No image bytes expected") }
        assertNull(loadPeugeotCarImage(null, client, decode))
        assertNull(loadPeugeotCarImage(url, client, decode))
        assertNotNull(ImageIO.read(File("src/main/res/drawable-nodpi/car_peugeot_208.png")))
    }
}
