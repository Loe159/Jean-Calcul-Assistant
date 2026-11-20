package io.github.mangi.eta.ui.screens.home.brief.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.suqi8.coui.kmp.theme.COUITheme

private const val ViewportWidth = 360f
private const val ViewportHeight = 150f
private val RearHub = Offset(83f, 115f)
private val FrontHub = Offset(258f, 116f)
private const val RearWheelScale = .95f

private object Peugeot208Paths {
    // The silhouette, reflection lines and lighting are drawn in a 360 × 150 viewport.
    val body = path("M22 93 Q22 81 35 74 L60 64 Q77 44 108 34 Q132 24 170 26 L190 27 Q211 28 227 38 L263 60 L297 67 Q320 71 331 81 L342 95 L344 108 Q343 116 332 119 L295 124 L24 119 Q15 114 17 105 Z")
    val glass = path("M62 68 Q80 47 108 38 Q131 29 168 30 L190 31 Q208 32 222 42 L253 63 L222 69 L76 74 Z")
    val rearQuarter = path("M62 68 Q80 47 108 38 L115 37 L103 69 L75 74 Z")
    val windshield = path("M190 31 Q208 32 222 42 L253 63 L222 69 Q207 45 180 31 Z")
    val glassReflection = path("M81 59 Q113 36 159 35 L184 36 L194 42 Q141 37 81 65 Z")
    val shoulder = path("M24 89 Q37 81 64 77 L223 70 Q252 67 270 63 L293 67 Q312 70 325 78 Q300 75 270 78 L100 91 Q50 95 21 102 Z")
    val lowerSide = path("M25 108 Q41 111 57 107 Q76 90 99 100 Q110 104 117 117 L226 114 Q235 95 255 96 Q274 96 288 114 L301 113 L295 124 L23 119 Q17 116 17 109 Z")
    val frontFace = path("M273 79 Q296 80 325 78 Q338 87 342 96 L344 107 Q342 116 331 119 L289 124 Q287 97 273 79 Z")
    val grille = path("M293 92 L336 86 L340 97 L335 110 L300 114 Q294 107 293 92 Z")
    val intake = path("M297 112 L336 107 L341 106 L338 114 L302 121 Z")
    val hood = path("M224 69 L254 62 L294 67 Q314 70 327 79 L300 80 L264 77 Z")
    val doorSeams = path("M122 74 L116 88 L122 105 L134 114 M184 72 L187 92 L182 117 M222 70 L231 86 L232 95")
    val sill = path("M113 110 Q155 109 189 106 L231 103 M119 119 L226 117")
    val belt = path("M62 74 L223 69 Q231 71 248 73 M33 90 L56 85 M119 87 L225 79")
    val mirror = path("M208 67 Q205 62 211 59 Q222 58 231 64 L230 69 L216 71 Z")
    val rearLamp = path("M26 87 L52 80 L60 82 L52 89 L24 95 Z")
    val headlamp = path("M263 77 L295 77 L306 82 Q294 88 273 88 L263 85 Z")
    val frontLamp = path("M325 78 L332 82 L338 89 L329 87 L322 83 Z")
    val rearWheelArch = path("M56 117 Q57 91 81 91 Q105 91 111 118")
    val frontWheelArch = path("M230 120 Q227 93 254 91 Q282 89 287 120")
    val runningLights = path("M271 82 L296 81 L300 84 L299 96 L294 106 M325 81 L332 85 L336 90 L336 99")
    val rearLightTrace = path("M28 90 L53 84 M33 92 L51 88")
    val alloySpoke = path("M-2 -3 L-7 -17 L-1 -19 L5 -16 L3 -4 L1 3 Z")

    private fun path(value: String) = PathParser().parsePathString(value).toPath()
}

@Composable
internal fun Peugeot208Illustration(alert: Boolean, motionAllowed: Boolean, modifier: Modifier = Modifier) {
    val colors = COUITheme.colorScheme
    val pulse = briefAmbientCycle(alert && motionAllowed, 800, "brief-208-tyre", reverse = true)
    Box(
        modifier.fillMaxWidth().aspectRatio(ViewportWidth / ViewportHeight)
            .semantics { contentDescription = "Peugeot 208 blanc nacré, vue de trois quarts" },
    ) {
        Canvas(Modifier.matchParentSize()) {
            scale(size.width / ViewportWidth, size.height / ViewportHeight, pivot = Offset.Zero) {
                drawPeugeot208(colors.azure)
            }
        }
        if (alert) {
            Canvas(Modifier.matchParentSize().graphicsLayer()) {
                scale(size.width / ViewportWidth, size.height / ViewportHeight, pivot = Offset.Zero) {
                    val intensity = if (motionAllowed) pulse.value else .72f
                    drawTyreAlert(RearHub, RearWheelScale, colors.red, intensity)
                    drawTyreAlert(FrontHub, 1f, colors.red, intensity)
                }
            }
        }
    }
}

private fun DrawScope.drawPeugeot208(halo: Color) = with(Peugeot208Paths) {
    drawOval(
        Brush.radialGradient(listOf(halo.copy(alpha = .36f), Color.Transparent), Offset(181f, 128f), 172f),
        Offset(11f, 120f), Size(340f, 20f),
    )
    drawOval(Color(0x47313B51), Offset(24f, 127f), Size(312f, 9f))
    drawOval(Color(0x55313B51), Offset(46f, 129f), Size(241f, 5f))

    drawPath(body, Color(0xFF526174), style = Stroke(1.6f))
    drawPath(body, Brush.verticalGradient(
        0f to Color(0xFFFBFBFF), .40f to Color(0xFFF5FAFF), .67f to Color(0xFFDCE5F0),
        .84f to Color(0xFFEEF1F5), 1f to Color(0xFF8695AB), startY = 27f, endY = 125f,
    ))
    drawPath(shoulder, Brush.linearGradient(listOf(Color(0xFFE2E4EF), Color.White, Color(0xFFCFDFEB)), Offset(24f, 86f), Offset(315f, 75f)))
    drawPath(lowerSide, Brush.verticalGradient(listOf(Color(0xBDD6DDE8), Color(0xFF758295)), startY = 103f, endY = 124f))
    drawPath(hood, Brush.horizontalGradient(listOf(Color(0xFFBBBFD0), Color(0xFFF9FCFF), Color(0xFFD3DEEB)), startX = 223f, endX = 330f))
    drawPath(frontFace, Brush.verticalGradient(listOf(Color(0xFFEEF4FA), Color(0xFFAFBED2), Color(0xFF77889E)), startY = 76f, endY = 124f))
    drawPath(grille, Brush.verticalGradient(listOf(Color(0xFF344050), Color(0xFF111828)), startY = 87f, endY = 116f))
    for (row in 0..4) {
        val y = 95f + row * 3.5f
        drawLine(Color(0xFF607085).copy(alpha = .55f), Offset(298f + row * .6f, y), Offset(337f - row * .55f, y - 6f), .7f)
    }
    drawPath(intake, Color(0xFF222C3B))

    drawPath(glass, Color(0xFF151C27), style = Stroke(2f))
    drawPath(glass, Brush.linearGradient(listOf(Color(0xFF516780), Color(0xFF192739), Color(0xFF101722)), Offset(100f, 29f), Offset(156f, 76f)))
    drawPath(rearQuarter, Color(0xFF141A23))
    drawPath(windshield, Brush.linearGradient(listOf(Color(0xFF7890A9), Color(0xFF24334A)), Offset(200f, 31f), Offset(227f, 69f)))
    drawPath(glassReflection, Color(0x719EB6CF))
    drawLine(Color(0xFF151D29), Offset(153f, 32f), Offset(147f, 70f), 5f)
    drawLine(Color(0xFF252E3A), Offset(116f, 37f), Offset(105f, 72f), 2.5f)
    drawPath(belt, Color(0xFFFEFFFF), style = Stroke(1.3f))
    drawPath(doorSeams, Color(0xFF8898AD), style = Stroke(.75f))
    drawPath(sill, Color(0xFF718298), style = Stroke(.9f))
    listOf(126f, 190f).forEach { x ->
        drawLine(Color(0xFF8191A6), Offset(x, 83f), Offset(x + 12f, 82f), 2.1f, StrokeCap.Round)
        drawLine(Color(0xFFF9FAFF), Offset(x, 82f), Offset(x + 11f, 81f), 1f, StrokeCap.Round)
    }
    drawPath(mirror, Color(0xFF202938), style = Stroke(1.5f))
    drawPath(mirror, Brush.verticalGradient(listOf(Color(0xFFF9FCFF), Color(0xFF8293A6)), startY = 59f, endY = 72f))
    drawPath(rearLamp, Color(0xFF612231))
    drawPath(rearLightTrace, Color(0xFFF88A94), style = Stroke(1.4f, cap = StrokeCap.Round))
    drawPath(headlamp, Color(0xFF253145))
    drawPath(frontLamp, Color(0xFF253145))
    drawPath(runningLights, Color(0xFF8BCFF3), style = Stroke(3.5f, cap = StrokeCap.Round))
    drawPath(runningLights, Color(0xFFF5FCFF), style = Stroke(1.6f, cap = StrokeCap.Round))

    drawPath(rearWheelArch, Color(0xFF758598), style = Stroke(1.8f))
    drawPath(frontWheelArch, Color(0xFF758598), style = Stroke(1.8f))
    draw208Wheel(RearHub, RearWheelScale)
    draw208Wheel(FrontHub, 1f)
}

private fun DrawScope.draw208Wheel(hub: Offset, ratio: Float) {
    drawCircle(Color(0xFF111721), 23f * ratio, hub)
    drawCircle(Color(0xFF3D4858), 19.5f * ratio, hub, style = Stroke(1.4f))
    drawCircle(Color(0xFF161E2A), 16.5f * ratio, hub)
    for (spoke in 0..4) {
        scale(ratio, ratio, hub) {
            rotate(spoke * 72f - 12f, hub) {
                translate(hub.x, hub.y) {
                    drawPath(Peugeot208Paths.alloySpoke, Brush.horizontalGradient(
                        listOf(Color(0xFF677B91), Color(0xFFF0F4FA), Color(0xFF9DAABE)), startX = -7f, endX = 5f,
                    ))
                }
            }
        }
    }
    drawCircle(Color(0xFF8896A8), 5f * ratio, hub)
    drawCircle(Color(0xFF263346), 3.3f * ratio, hub)
}

private fun DrawScope.drawTyreAlert(hub: Offset, ratio: Float, alert: Color, pulse: Float) {
    drawCircle(alert.copy(alpha = .16f + .12f * pulse), (24f + 3f * pulse) * ratio, hub, style = Stroke(4f))
    drawCircle(alert.copy(alpha = .40f + .50f * pulse), (22.7f + pulse) * ratio, hub, style = Stroke(1.8f))
}
