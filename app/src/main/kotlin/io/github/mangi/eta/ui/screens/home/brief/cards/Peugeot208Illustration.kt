package io.github.mangi.eta.ui.screens.home.brief.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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

// In viewport units: the body spans x = 20 to 340, the roof peaks at y = 18,
// and the tyres meet the ground at y = 130.
private val FrontHub = Offset(84f, 105.2f)
private val RearHub = Offset(285.6f, 105.2f)
private const val TyreRadius = 24.8f
private const val WheelForeshortening = .97f

private fun path(data: String): Path = PathParser().parsePathString(data).toPath()
private fun color(rgb: Int): Color = Color(rgb or 0xFF000000.toInt())
private fun gradient(from: Offset, to: Offset, vararg rgb: Int): Brush =
    Brush.linearGradient(rgb.map(::color), start = from, end = to)

private object Peugeot208Paths {
    val body = path(
        "M20 85 Q20 76 27 72 Q34 68 53 67 L118 59 L135 61 " +
            "L280 53 L318 51 Q331 56 337 69 Q340 77 340 92 Q340 108 331 113 " +
            "L308 117 L266 118.8 L107 118.8 L57 117 L32 113 Q20 108 20 98Z",
    )
    val side = path(
        "M36 79 Q75 68 135 61 C181 59 252 56 282 54 Q309 54 330 63 " +
            "Q340 73 340 91 Q340 106 331 113 L308 117 L266 118.8 L107 118.8 " +
            "L57 117 L34 112 L31 104Z",
    )
    val shoulder = path(
        "M43 80 Q85 67 135 61 C188 59 240 57 281 54 Q308 54 331 64 " +
            "L335 74 Q316 66 288 66 Q209 68 138 72 Q74 73 44 84Z",
    )
    val sideHollow = path(
        "M104 92 C145 88 192 96 240 89 Q256 86 266 80 " +
            "Q257 95 252 102 C214 106 157 110 112 103Z",
    )
    val rocker = path("M103 107 Q147 112 230 109 L265 105 L269 118.8 L104 118.8Z")
    val rockerHighlight = path("M107 110 Q170 116 263 109")
    val rearBumper = path("M312 96 L339 89 Q341 105 331 113 L308 117Z")
    val rearValance = path("M312 108 Q328 107 337 104 Q335 111 331 113 L308 117Z")
    val rearLamp = path("M303 64 Q317 63 333 67 L337 65.8 L338.5 71 Q324 75 305 69.5 L300 67Z")
    val rearLampLens = path("M304 66 Q317 65 331 68.7 L336 68 L337 69.5 Q325 72.5 307 68.5Z")
    val rearLampClaws = path("M310 66.8 L308.5 68.3 M316 67.1 L314.5 68.7 M322 67.9 L320.5 69.6")
    val rearReflector = path("M327 98 L338 95 L337 98 L327 101Z")

    val roof = path(
        "M118 59 C139 42 159 27 178 22 Q193 17.8 218 18 " +
            "C254 18 293 28 318 37 L324 38 L326 40.5 L321 42 " +
            "Q330 50 334 61 Q314 55.5 282 55 L135 62Z",
    )
    val roofReflection = path(
        "M175 25 Q196 18.5 219 19.5 C255 20.5 287 28 317 38 " +
            "L312 39 Q273 28 220 23 Q198 22 179 28Z",
    )
    val windshield = path("M120 59 C138 44 157 29 178 24 L186 25 C170 34 151 49 135 61Z")
    val windshieldReflection = path("M129 55 Q151 35 178 25 L182 25 Q152 42 135 56Z")
    val sideGlass = path(
        "M142 59 C157 47 176 32 190 27 Q205 22 224 24 " +
            "Q251 25 269 31 Q279 36 284 44 Q277 51 268 54Z",
    )
    val glassReflection = path(
        "M158 48 Q177 33 191 28 Q205 23 224 25 Q253 26 270 32 L277 36 " +
            "Q246 30 222 31 Q182 31 158 48Z",
    )
    val bPillar = path("M215 23 L223 24 L222 58 L213 58Z")
    val quarterPillarReflection = path(
        "M275 32 Q302 34 317 40 Q327 50 330 58 L324 56 Q317 46 312 43 Q296 35 275 32Z",
    )
    val spoilerEdge = path("M313 39 L321 41 L325 40.5")
    val windowTrim = path("M140 61 Q218 57 268 55 Q278 51 284 44")

    val bonnet = path("M26 73 Q33 67 54 67 L118 59 L135 61 Q105 67 80 72 Q58 75 38 80Z")
    val bonnetHighlight = path("M38 71 Q79 67 118 60 L127 61 Q86 68 53 72Z")
    val bonnetShutLine = path("M38 78 Q86 71 135 62")
    val bumperFace = path("M27 73 L39 78 L44 96 L52 114 L32 113 Q20 108 20 98 L20 85 Q20 77 27 73Z")
    val grille = path("M21 85 Q27 81 36 83 L39 87 L39 95 Q29 96 21 92Z")
    val lowerIntake = path("M21 98 Q29 101 41 101 L46 108 Q31 110 24 105Z")
    val frontValance = path("M26 107 Q36 112 57 110 L60 117 L32 113Z")
    val lamp = path("M28 75 L39 72 Q49 69 65 71 L75 72 Q71 76 57 79 L43 81 L36 79Z")
    val lampSignature = path("M41 75.5 Q52 72.5 66 73.4")
    val lampLens = path("M43 77 Q53 75 63 75.2 Q54 77.5 45 78.5Z")
    val fangHousing = path("M37 77 L43 78 L42 88 L45 95 L42 101 L37 96 L35 86Z")
    val fang = path("M38 79 Q37 87 40 93 L41.5 96")

    val doorSeams = path(
        "M146 61 Q136 72 131 86 L128 104 Q127 110 130 113 " +
            "M220 58 L218 82 L215 108 Q215 111 212 113 " +
            "M282 55 Q287 61 287 73 Q270 73 261 89 Q257 97 256 107 L253 110",
    )
    val beltCrease = path("M94 76 Q154 73 186 71 Q258 65 291 64")
    val lowerCrease = path("M119 104 Q183 110 249 99")
    val frontHandle = path("M190 69.5 L205 68.5 L203 71.5 L192 72.5Z")
    val rearHandle = path("M254 65.5 L268 64.4 L266 67.4 L255 68.4Z")
    val handleHighlights = path("M192 69.6 L203 68.8 M256 65.5 L266 64.7")
    val mirrorMount = path("M146 57 L154 57 L158 66 L149 65Z")
    val mirror = path("M135 53 Q144 49 152 52 Q158 54 157 58 Q154 62 145 62 L136 60 Q132 57 135 53Z")
    val mirrorHighlight = path("M136 55 Q144 51 152 54 L155 56 Q147 54 137 57Z")

    val frontArch = path("M56 117 L56 105 C56 89 67 77 84 77 C101 77 112 89 112 105 L112 118.8Z")
    val rearArch = path(
        "M257.6 118.8 L257.6 105 C257.6 89 268.6 77 285.6 77 " +
            "C302.6 77 313.6 89 313.6 105 L313.6 116Z",
    )
    val archHighlights = path(
        "M56 105 C56 89 68 77 84 77 Q104 77 110 96 " +
            "M257.6 105 C257.6 89 269.6 77 285.6 77 Q305.6 77 311.6 96",
    )
    val spoke = path("M-2.3 -4.6 L-7.2 -17.7 L-3.8 -19 L.3 -9.4 L3.9 -19 L7.1 -17.5 L2.8 -5Z")
    val spokeBevel = path("M-.8 -5.6 L-5.8 -17.9 L-4.2 -18.3 L.1 -8.4 L4.2 -18.3 L5.7 -17.7 L1.6 -5.6Z")
}

@Composable
internal fun Peugeot208Illustration(alert: Boolean, motionAllowed: Boolean, modifier: Modifier = Modifier) {
    val colors = COUITheme.colorScheme
    val pulse = briefAmbientCycle(alert && motionAllowed, 800, "brief-208-tyre", reverse = true)
    Box(
        modifier.fillMaxWidth().aspectRatio(ViewportWidth / ViewportHeight)
            .semantics { contentDescription = "Peugeot 208 blanc nacré, profil gauche avec la face avant visible" },
    ) {
        Canvas(Modifier.matchParentSize()) { drawPeugeot208() }
        if (alert) {
            Canvas(Modifier.matchParentSize().graphicsLayer()) {
                scale(size.width / ViewportWidth, size.height / ViewportHeight, pivot = Offset.Zero) {
                    val intensity = if (motionAllowed) pulse.value else .72f
                    drawTyreAlert(RearHub, colors.red, intensity)
                    drawTyreAlert(FrontHub, colors.red, intensity)
                }
            }
        }
    }
}

private fun DrawScope.drawPeugeot208() {
    scale(size.width / ViewportWidth, size.height / ViewportHeight, pivot = Offset.Zero) {
        drawGround()
        drawBodywork()
        drawCabin()
        drawFront()
        draw208Wheel(FrontHub)
        draw208Wheel(RearHub)
    }
}

private fun DrawScope.drawGround() {
    val center = Offset(180f, 125f)
    scale(1f, .085f, center) {
        drawCircle(
            Brush.radialGradient(listOf(Color(0x79101720), Color.Transparent), center, 173f),
            radius = 173f, center = center,
        )
    }
    for (hub in listOf(FrontHub, RearHub)) {
        val contact = hub + Offset(0f, TyreRadius)
        scale(1f, .18f, contact) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0x6B0E1520), Color.Transparent), contact, 29f),
                radius = 29f, center = contact,
            )
        }
    }
}

private fun DrawScope.drawBodywork() = with(Peugeot208Paths) {
    drawPath(body, gradient(Offset(180f, 30f), Offset(182f, 119f),
        0xFAFAFC, 0xEDEFF2, 0xB7BFCA))
    drawPath(side, Brush.verticalGradient(
        0f to color(0xF6F7F9), .32f to color(0xF2F4F7), .56f to color(0xD8DDE6),
        .78f to color(0xE7EBEF), 1f to color(0xA1AAB7), startY = 57f, endY = 119f,
    ))
    drawPath(shoulder, gradient(Offset(175f, 58f), Offset(175f, 78f),
        0xE8ECF1, 0xFDFDFE, 0xF3F5F8))
    drawPath(sideHollow, gradient(Offset(183f, 88f), Offset(183f, 107f),
        0xD9DFE8, 0xE5E9EE, 0xEBEEF0))
    drawPath(rocker, gradient(Offset(184f, 109f), Offset(184f, 119f), 0x55606B, 0x181D25))
    outline(rockerHighlight, 0x7B8590, .85f)
    drawPath(rearBumper, gradient(Offset(326f, 93f), Offset(329f, 116f), 0xDDE0E7, 0x9FA8B5))
    fill(rearValance, 0x272B34)
    fill(rearLamp, 0x24212A)
    fill(rearLampLens, 0x702439)
    outline(rearLampClaws, 0xE48993, .9f)
    fill(rearReflector, 0xA94D5B)
    outline(beltCrease, 0xFDFDFE, 1f)
    outline(lowerCrease, 0xB5BFCA, .8f)
    drawPath(doorSeams, Color(0xA3687585), style = Stroke(.8f, cap = StrokeCap.Round))
    fill(frontHandle, 0x8893A0)
    fill(rearHandle, 0x8893A0)
    outline(handleHighlights, 0xF9FAFD, .8f)
    fill(frontArch, 0x12161E)
    fill(rearArch, 0x12161E)
    outline(archHighlights, 0x414952, 1.6f)
}

private fun DrawScope.drawCabin() = with(Peugeot208Paths) {
    drawPath(roof, gradient(Offset(204f, 18f), Offset(208f, 63f), 0x313842, 0x1C242E, 0x10151D))
    drawPath(sideGlass, gradient(Offset(227f, 25f), Offset(230f, 58f), 0x617080, 0x354352, 0x202832))
    fill(roofReflection, 0x58626E)
    drawPath(glassReflection, Color(0x566F8294))
    fill(bPillar, 0x19212A)
    drawPath(quarterPillarReflection, gradient(Offset(283f, 32f), Offset(328f, 57f),
        0x444F5B, 0x394654, 0x293442))
    outline(spoilerEdge, 0x57606A, .7f)
    drawPath(windshield, gradient(Offset(174f, 26f), Offset(133f, 62f), 0x738697, 0x455769, 0x263444))
    drawPath(windshieldReflection, Color(0x5196A9BD))
    outline(windowTrim, 0xA5ABB3, .8f)
    fill(mirrorMount, 0x181F29)
    drawPath(mirror, gradient(Offset(145f, 51f), Offset(146f, 62f), 0x667483, 0x242E3A, 0x11161F))
    drawPath(mirrorHighlight, Color(0x6596A5B4))
}

private fun DrawScope.drawFront() = with(Peugeot208Paths) {
    drawPath(bonnet, gradient(Offset(101f, 60f), Offset(101f, 81f), 0xFDFDFE, 0xE8EBEF, 0xC6CED9))
    fill(bonnetHighlight, 0xFBFBFC)
    outline(bonnetShutLine, 0xA7B2C0, .75f)
    drawPath(bumperFace, gradient(Offset(28f, 74f), Offset(29f, 113f), 0xC9D1DC, 0xE1E6EC, 0x959EAB))
    fill(grille, 0x232B35)
    clipPath(grille) {
        for (row in 0..2) {
            val y = 85f + 3.3f * row
            for (column in 0..2) {
                val x = 21f + 6.5f * column + 1.2f * (row % 2)
                val slope = (x - 21f) * .13f
                drawLine(color(0xBFC6CF), Offset(x, y + slope), Offset(x + 4.5f, y + slope + .6f), 1f)
            }
        }
    }
    fill(lowerIntake, 0x151C25)
    fill(frontValance, 0x424B56)
    fill(lamp, 0x171F2A)
    fill(lampLens, 0x354C61)
    outline(lampSignature, 0xD8E8F6, 1.35f)
    fill(fangHousing, 0x273444)
    outline(fang, 0x8EB5CF, 2.8f)
    outline(fang, 0xF6FAFF, 1.35f)
}

private fun DrawScope.draw208Wheel(hub: Offset) {
    scale(WheelForeshortening, 1f, hub) {
        drawCircle(color(0x10141A), TyreRadius, hub)
        drawCircle(color(0x3B4858), 21.7f, hub, style = Stroke(1.25f))
        drawCircle(
            gradient(hub - Offset(11f, 22f), hub + Offset(9f, 23f), 0xB1BBC7, 0x4C5969, 0x8092A8),
            19.25f, hub, style = Stroke(1.5f),
        )
        drawCircle(color(0x181F2B), 18f, hub)
        drawCircle(color(0x3F4955), 12.5f, hub)
        drawCircle(color(0x222A34), 10.5f, hub, style = Stroke(.8f))
        repeat(5) { spoke ->
            rotate(spoke * 72f - 13f, hub) {
                translate(hub.x, hub.y) {
                    fill(Peugeot208Paths.spoke, 0x536173)
                    drawPath(Peugeot208Paths.spokeBevel, gradient(Offset(-6f, -18f), Offset(4f, -7f),
                        0xF1F4F7, 0xBAC5D2, 0xE2E8EE, 0x8E9BAD))
                }
            }
        }
        drawCircle(color(0x778496), 5.6f, hub)
        drawCircle(color(0x222D3B), 3.8f, hub)
        repeat(4) { index ->
            rotate(index * 90f + 45f, hub) {
                drawCircle(color(0xB7C4D1), .63f, hub - Offset(0f, 4.65f))
            }
        }
    }
}

private fun DrawScope.fill(path: Path, rgb: Int) = drawPath(path, color(rgb))

private fun DrawScope.outline(path: Path, rgb: Int, width: Float) =
    drawPath(path, color(rgb), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))

private fun DrawScope.drawTyreAlert(hub: Offset, alert: Color, pulse: Float) {
    scale(WheelForeshortening, 1f, hub) {
        drawCircle(alert.copy(alpha = .16f + .12f * pulse), TyreRadius + 3f * pulse, hub, style = Stroke(4f))
        drawCircle(alert.copy(alpha = .40f + .50f * pulse), TyreRadius - .3f + pulse, hub, style = Stroke(1.8f))
    }
}
