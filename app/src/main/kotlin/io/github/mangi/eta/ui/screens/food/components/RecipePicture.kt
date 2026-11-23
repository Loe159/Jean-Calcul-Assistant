package io.github.mangi.eta.ui.screens.food.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.food.RecipeImage
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val recipeImages = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()

@Composable
internal fun RecipePicture(image: RecipeImage?) {
    if (image == null) return
    val bitmap by produceState<Bitmap?>(null, image.url) {
        value = null
        value = withContext(Dispatchers.IO) { loadRecipePicture(image.url) }
    }
    bitmap?.let {
        Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1.8f).clip(SquircleShape(18.dp)))
    }
}

private fun loadRecipePicture(url: String): Bitmap? {
    return try {
        val request = Request.Builder().url(url).build()
        recipeImages.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body
            if (body.contentType()?.type != "image") return null
            val source = body.source()
            if (source.request(4L * 1024 * 1024 + 1)) return null
            val bytes = source.readByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 16384 || bounds.outHeight > 16384) return null
            val options = BitmapFactory.Options().apply {
                while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize.coerceAtLeast(1) > 1600) {
                    inSampleSize = inSampleSize.coerceAtLeast(1) * 2
                }
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
