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
private val RearHub = Offset(88f, 109f)
private val FrontHub = Offset(247f, 115f)
private const val RearWheelScale = .91f

private fun path(data: String): Path = PathParser().parsePathString(data).toPath()
private fun color(rgb: Int): Color = Color(rgb or 0xFF000000.toInt())
private fun gradient(from: Offset, to: Offset, vararg rgb: Int): Brush =
    Brush.linearGradient(rgb.map(::color), start = from, end = to)

private object Peugeot208Paths {
    // The left flank and nose converge towards different vanishing points.
    // The tailgate ends at the C pillar. That edge continues into the rear bumper.
    val body = path("M36 106 Q34 99 37 92 L40 77 Q42 70 47 61 L49 49 Q51 40 60 35 C79 25 115 20 162 20 Q195 19 211 28 C227 35 243 47 257 55 L299 60 Q318 63 327 72 Q335 78 339 90 L341 107 Q341 114 332 119 L281 132 L252 133 L41 117 Q36 114 36 106Z")
    val side = path("M40 79 L60 69 Q114 68 166 71 L218 74 L261 75 L287 85 L289 116 L277 131 L43 117 Q35 114 36 105Z")
    val shoulder = path("M41 78 L61 68 Q127 68 167 69 Q212 70 232 73 L267 75 L276 82 Q250 82 224 85 Q140 80 62 84 L38 93Z")
    val sideHollow = path("M38 97 Q67 89 119 90 Q179 93 217 89 Q241 86 270 88 L285 98 Q252 98 219 106 Q173 112 111 106 L37 110Z")
    val rocker = path("M41 108 Q101 112 148 116 L222 119 L281 124 L276 132 L42 117Z")
    val rockerHighlight = path("M111 112 Q160 116 220 116 L224 120 L110 117Z")
    val tailgate = path("M49 49 Q50 40 58 37 L62 40 L55 61 L46 77 L39 88 L42 77 L47 64Z")
    val rearBumper = path("M37 95 L46 92 L51 93 L48 106 L62 110 L64 119 L42 117 Q35 114 36 105Z")
    val rearLamp = path("M44 67 L52 61 Q61 61 75 63 L73 67 L61 70 L43 73Z")
    val rearLampLens = path("M45 68 L54 64 L71 64 L69 66 L45 70Z")
    val rearLampClaws = path("M54 64 L53 68 M60 63.5 L59 67.5 M66 64 L65 67")
    val rearReflector = path("M37 98 L48 95 L48 98 L37 101Z")

    val roof = path("M51 47 Q53 39 60 35 C79 25 115 20 162 20 Q194 19 211 28 Q227 35 244 48 L257 55 L224 76 L61 70 L49 60Z")
    val roofReflection = path("M55 45 C77 27 124 22 165 22 Q194 21 209 30 L214 34 Q188 26 155 26 Q98 25 64 42Z")
    val sideGlass = path("M67 43 C89 31 115 27 151 26 L169 27 Q191 32 221 72 Q147 68 71 67 L61 61Z")
    val rearGlassReflection = path("M66 47 Q84 35 111 32 L113 37 Q86 41 63 55Z")
    val frontGlassReflection = path("M122 32 Q153 28 170 31 Q183 35 193 45 Q160 36 123 39Z")
    val quarterPillar = path("M60 38 L70 40 L66 51 Q63 57 62 60 L73 68 L59 67 L52 61Z")
    val bPillar = path("M111 29 L120 28 L130 69 L120 69Z")
    val windshield = path("M172 27 Q198 26 211 33 C228 41 240 49 254 57 L223 74 Q201 45 172 27Z")
    val windshieldReflection = path("M183 29 Q203 29 212 36 Q229 43 246 55 L239 59 Q222 48 203 42Z")
    val cowl = path("M216 70 L254 54 L258 57 L223 76 L214 75Z")
    val wipers = path("M220 70 L238 63 M230 68 L251 58")

    val bonnet = path("M222 75 L257 55 L301 60 Q319 64 327 72 L333 78 L289 89 L270 83 L243 80Z")
    val bonnetCrown = path("M234 74 L261 57 Q287 60 307 65 L320 71 L285 78 L266 77Z")
    val bonnetCreases = path("M232 75 Q262 74 284 80 L322 71 M256 57 Q274 58 293 62")
    val bonnetShutLine = path("M222 76 Q251 82 269 81 L288 86 Q312 82 333 76")
    val nearFender = path("M224 86 Q252 79 274 85 L289 94 L295 116 L276 130 L270 112 Q265 94 246 92 Q232 91 222 101Z")
    val frontBumper = path("M276 87 L332 75 Q339 82 340 94 L341 107 Q341 114 332 119 L281 132 Q284 128 280 112 L270 89Z")
    val nearScoop = path("M273 91 L288 91 L296 114 L287 120 L281 117Z")
    val farScoop = path("M331 81 L336 81 Q341 94 339 105 L333 110 L330 107Z")
    val grille = path("M288 89 Q306 84 331 79 Q335 87 334 101 L329 110 L302 119 L294 114Z")
    val grilleRim = path("M287 89 Q312 84 332 78 M294 114 L302 119 L329 110")
    val lowerIntake = path("M285 123 Q309 117 334 111 L332 117 L281 131Z")
    val splitter = path("M281 131 L332 118 L327 121 L280 134 L268 133Z")
    val noseReflection = path("M297 119 L334 109 L332 112 L292 124Z")
    val nearLamp = path("M263 79 Q276 76 288 77 L300 81 Q294 85 289 87 Q274 88 267 86 L261 83Z")
    val farLamp = path("M321 70 Q330 71 334 77 L336 82 L330 81 L321 75 L317 73Z")
    val lampSignature = path("M266 82 Q276 79 286 80 L292 81 M322 73 Q329 76 332 78")
    val nearFang = path("M288 82 L291 86 L291 99 L287 111 L283 114 L285 102 L285 87Z")
    val farFang = path("M331 77 L335 81 L337 91 L336 101 L333 105 L333 91 L330 81Z")
    val fangCore = path("M288 85 L288 101 L285 111 M333 80 L335 91 L334 102")

    val windowTrim = path("M59 68 Q136 66 219 74")
    val doorSeams = path("M73 69 L73 78 Q74 89 84 90 Q100 88 109 102 L114 115 M125 70 L133 82 L135 113 Q136 117 140 118 M215 75 Q221 91 220 104 L216 120")
    val beltCrease = path("M55 77 Q124 73 210 80 L237 82")
    val lowerCrease = path("M114 109 Q163 115 219 109")
    val rearHandle = path("M88 75 L104 75 L102 78 L90 78Z")
    val frontHandle = path("M152 77 L170 78 L168 81 L154 81Z")
    val handleHighlights = path("M90 75.5 L101 75.5 M154 77.5 L167 78")
    val mirrorMount = path("M198 63 L211 65 L218 74 L206 74Z")
    val mirror = path("M189 63 Q189 59 197 58 Q207 58 214 62 L214 66 Q209 70 196 69 L190 67Z")
    val mirrorHighlight = path("M191 62 Q197 59 204 61 L212 63 L204 64 L193 64Z")

    val rearArch = path("M63 119 L63 109 C63 95 72 85 88 85 C104 85 114 98 114 113 L115 121Z")
    val frontArch = path("M218 130 L219 115 C219 99 230 88 247 88 C265 88 276 101 276 117 L275 132Z")
    val archHighlights = path("M62 111 C62 95 73 84 88 84 Q109 84 114 105 M218 116 C218 99 230 87 247 87 Q269 87 275 107")
    val spoke = path("M-2 -3 L-9 -15 L-6 -19 L-2 -19 L2 -11 L4 -18 L8 -17 L6 -9 L2 -2Z")
    val spokeCut = path("M-2 -5 L-6 -15 L-4 -17 L0 -9 L2 -5Z")
    val spokeBevel = path("M1 -7 L5 -16 L7 -16 L5 -9Z")
}

@Composable
internal fun Peugeot208Illustration(alert: Boolean, motionAllowed: Boolean, modifier: Modifier = Modifier) {
    val colors = COUITheme.colorScheme
    val pulse = briefAmbientCycle(alert && motionAllowed, 800, "brief-208-tyre", reverse = true)
    Box(
        modifier.fillMaxWidth().aspectRatio(ViewportWidth / ViewportHeight)
            .semantics { contentDescription = "Peugeot 208 blanc nacré, vue de trois quarts avant gauche" },
    ) {
        Canvas(Modifier.matchParentSize()) { drawPeugeot208(colors.azure) }
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

internal fun DrawScope.drawPeugeot208(halo: Color) {
    scale(size.width / ViewportWidth, size.height / ViewportHeight, pivot = Offset.Zero) {
        drawGround(halo)
        drawBodywork()
        drawCabin()
        drawFront()
        draw208Wheel(RearHub, RearWheelScale)
        draw208Wheel(FrontHub, 1f)
    }
}

private fun DrawScope.drawGround(halo: Color) {
    scale(1f, .11f, Offset(181f, 130f)) {
        drawCircle(
            Brush.radialGradient(listOf(halo.copy(alpha = .43f), Color.Transparent), Offset(181f, 130f), 168f),
            radius = 168f, center = Offset(181f, 130f),
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0xA8101D2A), Color.Transparent), Offset(177f, 137f), 143f),
            radius = 143f, center = Offset(177f, 137f),
        )
    }
    drawOval(Color(0x55132436), Offset(65f, 127f), Size(48f, 7f))
    drawOval(Color(0x66132436), Offset(222f, 134f), Size(53f, 7f))
}

private fun DrawScope.drawBodywork() = with(Peugeot208Paths) {
    outline(body, 0x485363, 1.6f)
    drawPath(body, gradient(Offset(124f, 23f), Offset(145f, 132f),
        0xFBFCFF, 0xE6EBF5, 0xEFF4FA, 0x8796AA))
    drawPath(side, gradient(Offset(145f, 69f), Offset(142f, 126f),
        0xF6F9FC, 0xDCE4EE, 0xF4F3F5, 0x8390A2))
    drawPath(shoulder, gradient(Offset(55f, 82f), Offset(266f, 77f),
        0xB7BCCB, 0xF3F5FB, 0xFFFFFF, 0xD6E2ED))
    drawPath(sideHollow, gradient(Offset(159f, 90f), Offset(159f, 111f),
        0xCED8E4, 0xE2E9F2, 0xE7E5ED))
    drawPath(rocker, gradient(Offset(143f, 111f), Offset(143f, 127f),
        0xA8B4C3, 0x647487, 0x293748))
    fill(rockerHighlight, 0x9AA9BD)
    fill(tailgate, 0x9AA4B6)
    drawPath(rearBumper, gradient(Offset(45f, 91f), Offset(46f, 116f), 0xE9ECF4, 0x939DB0, 0x596979))
    fill(rearLamp, 0x2C202C)
    fill(rearLampLens, 0x6E263B)
    outline(rearLampClaws, 0xF67F8D, 1f)
    fill(rearReflector, 0xA63654)
    outline(beltCrease, 0xFFFFFF, 1.1f)
    outline(lowerCrease, 0xA1AEC0, .85f)
    outline(doorSeams, 0x7D8A9A, .8f)
    fill(rearHandle, 0x8793A4)
    fill(frontHandle, 0x8290A1)
    outline(handleHighlights, 0xFAFBFE, 1f)
    fill(rearArch, 0x111823)
    outline(rearArch, 0x333C4B, 1.7f)
}

private fun DrawScope.drawCabin() = with(Peugeot208Paths) {
    drawPath(roof, gradient(Offset(112f, 20f), Offset(119f, 72f), 0x313B49, 0x141B26, 0x080E17))
    drawPath(sideGlass, gradient(Offset(123f, 27f), Offset(137f, 70f), 0x556E87, 0x26374C, 0x151D2B))
    fill(roofReflection, 0x5C6E83)
    drawPath(rearGlassReflection, Color(0x686C89A4))
    drawPath(frontGlassReflection, Color(0x707994B1))
    fill(quarterPillar, 0x171F2A)
    fill(bPillar, 0x141C28)
    drawPath(windshield, gradient(Offset(190f, 27f), Offset(221f, 73f), 0x8197AD, 0x3C5369, 0x132235))
    drawPath(windshieldReflection, Color(0x5CABC2D6))
    fill(cowl, 0x131C29)
    outline(wipers, 0x223144, 1.1f)
    outline(windowTrim, 0xE4EAF2, .9f)
    fill(mirrorMount, 0x171E28)
    outline(mirror, 0x161D27, 1.1f)
    drawPath(mirror, gradient(Offset(199f, 58f), Offset(202f, 69f), 0x5E748A, 0x212E3D, 0x0A1019))
    drawPath(mirrorHighlight, Color(0x657F9AB7))
}

private fun DrawScope.drawFront() = with(Peugeot208Paths) {
    drawPath(bonnet, gradient(Offset(253f, 56f), Offset(282f, 88f), 0xB6C1D0, 0xF3F6FA, 0xDEE6F2))
    drawPath(bonnetCrown, gradient(Offset(247f, 63f), Offset(311f, 77f), 0xE5ECF5, 0xFDFDFE, 0xD3DEEC))
    outline(bonnetCreases, 0xD1DBE8, .8f)
    outline(bonnetShutLine, 0x7B899C, .8f)
    drawPath(nearFender, gradient(Offset(250f, 86f), Offset(262f, 128f), 0xEDF2F9, 0xD3DDEA, 0x7C8DA0))
    drawPath(frontBumper, gradient(Offset(296f, 78f), Offset(310f, 126f), 0xE8EDF5, 0xACBBCD, 0x77879B))
    fill(nearScoop, 0x263342)
    fill(farScoop, 0x1B2533)
    drawPath(grille, gradient(Offset(309f, 80f), Offset(313f, 118f), 0x33404D, 0x17212D, 0x0E151E))
    clipPath(grille) { drawGrilleBars() }
    outline(grilleRim, 0x5D6C7D, .9f)
    fill(lowerIntake, 0x181F29)
    fill(splitter, 0x4F5E70)
    fill(noseReflection, 0xB8C7D8)
    fill(nearLamp, 0x151F2F)
    fill(farLamp, 0x151F2F)
    outline(lampSignature, 0x90ACC9, 2.5f)
    outline(lampSignature, 0xEDF8FF, 1f)
    drawPath(nearFang, Color(0x3366C6FC), style = Stroke(5f, join = StrokeJoin.Round))
    drawPath(farFang, Color(0x3366C6FC), style = Stroke(4f, join = StrokeJoin.Round))
    fill(nearFang, 0x8ACAF3)
    fill(farFang, 0x8ACAF3)
    outline(fangCore, 0xF6FCFF, 1.3f)
    fill(frontArch, 0x111823)
    outline(frontArch, 0x303B4B, 2.1f)
    outline(archHighlights, 0xCDD6E1, 1.35f)
}

private fun DrawScope.drawGrilleBars() {
    // Staggered pearl-coloured inserts follow the slope of the grille face.
    for (row in 0..5) {
        for (column in 0..5) {
            val x = 289f + 7.6f * column + 1.8f * (row % 2)
            val y = 91f + 4.7f * row - (x - 289f) * .25f
            drawLine(
                color(if (row < 3) 0xC1CCD8 else 0x8596AB),
                Offset(x, y), Offset(x + 4f, y - 1f), 1.6f, StrokeCap.Round,
            )
            drawLine(color(0x526175), Offset(x, y + 1.3f), Offset(x + 4f, y + .3f), .55f)
        }
    }
}

private fun DrawScope.draw208Wheel(hub: Offset, ratio: Float) {
    scale(ratio * .93f, ratio, hub) {
        drawCircle(color(0x0E131B), 24f, hub)
        drawCircle(color(0x3B4858), 21.7f, hub, style = Stroke(1.25f))
        drawCircle(
            gradient(hub - Offset(11f, 22f), hub + Offset(9f, 23f), 0xB1BBC7, 0x4C5969, 0x8092A8),
            19.25f, hub, style = Stroke(1.5f),
        )
        drawCircle(color(0x181F2B), 18f, hub)
        drawCircle(color(0x586577), 12.5f, hub)
        drawCircle(color(0x292F3A), 10.5f, hub, style = Stroke(.8f))
        repeat(5) { spoke ->
            rotate(spoke * 72f - 13f, hub) {
                translate(hub.x, hub.y) {
                    drawPath(Peugeot208Paths.spoke, gradient(Offset(-8f, -15f), Offset(8f, -6f),
                        0x758A9F, 0xEAF0F6, 0xACBAC9, 0x4E5E70))
                    fill(Peugeot208Paths.spokeCut, 0x252E3A)
                    fill(Peugeot208Paths.spokeBevel, 0xC2CFDE)
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

private fun DrawScope.drawTyreAlert(hub: Offset, ratio: Float, alert: Color, pulse: Float) {
    scale(.93f, 1f, hub) {
        drawCircle(alert.copy(alpha = .16f + .12f * pulse), (24f + 3f * pulse) * ratio, hub, style = Stroke(4f))
        drawCircle(alert.copy(alpha = .40f + .50f * pulse), (23.7f + pulse) * ratio, hub, style = Stroke(1.8f))
    }
}
