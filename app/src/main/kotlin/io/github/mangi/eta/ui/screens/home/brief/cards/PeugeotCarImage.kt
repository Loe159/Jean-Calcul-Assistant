package io.github.mangi.eta.ui.screens.home.brief.cards

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.model.AgentHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Composable
internal fun PeugeotCarImage(url: HttpUrl?, modifier: Modifier = Modifier) = key(url) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null) {
        if (url != null) {
            value = runInterruptible(Dispatchers.IO) {
                loadPeugeotCarImage(url, PeugeotCarImageCache.client(context), ::decodeCarImage)
            }
        }
    }
    Image(
        painter = image?.let(::BitmapPainter) ?: painterResource(R.drawable.car_peugeot_208),
        contentDescription = null,
        modifier = modifier.fillMaxWidth().aspectRatio(2.4f),
    )
}

internal fun <T> loadPeugeotCarImage(url: HttpUrl?, client: OkHttpClient, decode: (ByteArray) -> T?): T? {
    if (url == null) return null
    return try {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful || response.body.source().request(maxImageBytes + 1)) return null
            decode(response.body.bytes())
        }
    } catch (_: IOException) {
        null
    }
}

private fun decodeCarImage(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longestSide / sample > 1200) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

private const val maxImageBytes = 2_097_152L

private object PeugeotCarImageCache {
    private var cached: OkHttpClient? = null

    @Synchronized
    fun client(context: Context): OkHttpClient = cached ?: AgentHttpClient.client.newBuilder()
        .cache(Cache(File(context.cacheDir, "peugeot-car-images"), 5 * maxImageBytes))
        .followSslRedirects(false)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
        .also { cached = it }
}
