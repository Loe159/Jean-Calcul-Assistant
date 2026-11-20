package io.github.mangi.eta.ui.screens.home.brief.cards

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceThemeMode
import io.github.mangi.eta.ui.app.AgentAppTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Peugeot208IllustrationTest {
    @Test
    fun rendersPearlBodyworkAndDarkWheelArchesInBothThemes() {
        for (theme in listOf(AppearanceThemeMode.LIGHT, AppearanceThemeMode.DARK)) {
            val background = if (theme == AppearanceThemeMode.DARK) Color(0xFF1C1C1E) else Color.White
            Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
                val activity = controller.get()
                val view = ComposeView(activity).apply {
                    setContent {
                        AgentAppTheme(AppearanceSettings(themeMode = theme), applyInterfaceScale = false) {
                            Peugeot208Illustration(
                                alert = false,
                                motionAllowed = false,
                                modifier = Modifier.background(background),
                            )
                        }
                    }
                }
                activity.setContentView(view)
                shadowOf(Looper.getMainLooper()).idle()
                view.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
                view.layout(0, 0, view.measuredWidth, view.measuredHeight)
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val directory = File("build/reports/brief-visual")
                directory.mkdirs()
                val output = File(directory, "peugeot-208-${theme.name.lowercase()}.png")
                output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }

                val body = bitmap.getPixel(312, 176)
                assertTrue("Pearl paint should stay light in ${theme.name}", (body and 0xff) > 170)
                for (hubX in listOf(168, 571)) {
                    val tyre = bitmap.getPixel(hubX, 257)
                    assertTrue("Both tyres should stay dark in ${theme.name}", (tyre and 0xff) < 80)
                }
                assertEquals("Capture should show the chosen background", background.toArgb(), bitmap.getPixel(0, 0))
            }
        }
    }
}
