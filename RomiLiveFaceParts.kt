package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.abs

internal val OutlineInk = Color(0xFF3A1816)
internal val SkinBase = Color(0xFFFFE5D7)
internal val SkinHighlight = Color(0xFFFFF3EC)
internal val SkinShadow = Color(0xFFF3BCA8)
internal val HairDark = Color(0xFF6B2A1A)
internal val HairMid = Color(0xFF9E4728)
internal val HairLight = Color(0xFFC8693E)
internal val HairShine = Color(0xFFE89B70)
internal val IrisTop = Color(0xFF6E2F12)
internal val IrisMid = Color(0xFFC66B26)
internal val IrisGlow = Color(0xFFF8BC54)
internal val BlushPink = Color(0xFFFF6E91)
internal val MouthCavity = Color(0xFF6E1C33)
internal val TongueCoral = Color(0xFFF0738E)
internal val SweaterBase = Color(0xFFF7BED1)
internal val SweaterShade = Color(0xFFE393B0)
internal val SweaterLight = Color(0xFFFFDCE8)

internal fun DrawScope.drawAnimeEye(
    center: Offset,
    u: Float,
    isLeftEye: Boolean,
    openness: Float,
    smileArc: Float,
    gazeX: Float,
    gazeY: Float,
    pupilScale: Float
) {
    val mirror = if (isLeftEye) 1f else -1f
    val eyeW = 19.5f * u
    val maxEyeH = 14.5f * u
    val clampedOpen = openness.coerceIn(0f, 1.18f)

    // When fully closed or winking, draw expressive curved lash line
    if (clampedOpen < 0.14f) {
        val archLift = (-4.5f - smileArc * 5.2f) * u
        val closedPath = Path().apply {
            moveTo(center.x - eyeW * 0.92f, center.y + 1.5f * u)
            cubicTo(
                center.x - eyeW * 0.42f,
                center.y + archLift,
                center.x + eyeW * 0.42f,
                center.y + archLift,
                center.x + eyeW * 0.92f,
                center.y + 1.8f * u
            )
        }
        drawPath(
            path = closedPath,
            color = OutlineInk,
            style = Stroke(width = 3.0f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Outer cute lash tick
        val outerX = center.x - mirror * eyeW * 0.92f
        drawLine(
            color = OutlineInk,
            start = Offset(outerX, center.y + 1.5f * u),
            end = Offset(outerX - mirror * 3.5f * u, center.y - 1.4f * u),
            strokeWidth = 2.0f * u,
            cap = StrokeCap.Round
        )
        return
    }

    val halfH = maxEyeH * clampedOpen
    val topControlY = center.y - halfH * 1.12f
    val bottomControlY = center.y + halfH * (0.95f - smileArc * 0.25f)

    val eyeOpeningPath = Path().apply {
        moveTo(center.x - eyeW, center.y + 0.8f * u)
        cubicTo(
            center.x - eyeW * 0.55f,
            topControlY,
            center.x + eyeW * 0.55f,
            topControlY,
            center.x + eyeW,
            center.y + 0.8f * u
        )
        cubicTo(
            center.x + eyeW * 0.52f,
            bottomControlY,
            center.x - eyeW * 0.52f,
            bottomControlY,
            center.x - eyeW,
            center.y + 0.8f * u
        )
        close()
    }

    clipPath(eyeOpeningPath) {
        // Sclera base
        drawRect(
            color = Color(0xFFFFFCFD),
            topLeft = Offset(center.x - eyeW * 1.2f, center.y - maxEyeH * 1.4f),
            size = Size(eyeW * 2.4f, maxEyeH * 2.8f)
        )

        // Soft top eyelid cast shadow across sclera
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFD6C0CD), Color.Transparent),
                startY = center.y - halfH,
                endY = center.y - halfH * 0.2f
            ),
            topLeft = Offset(center.x - eyeW * 1.1f, center.y - halfH * 1.1f),
            size = Size(eyeW * 2.2f, halfH * 0.95f)
        )

        // Iris & pupil group shifted by gaze
        val irisCenter = Offset(
            x = center.x + gazeX * 4.2f * u,
            y = center.y + gazeY * 3.2f * u
        )
        val irisRx = 11.2f * u * pupilScale
        val irisRy = 12.6f * u * pupilScale

        // Dark outer limbal ring
        drawOval(
            color = OutlineInk,
            topLeft = Offset(irisCenter.x - irisRx, irisCenter.y - irisRy),
            size = Size(irisRx * 2f, irisRy * 2f)
        )

        // Golden-amber anime iris gradient
        val innerRx = irisRx * 0.88f
        val innerRy = irisRy * 0.89f
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(IrisTop, IrisMid, IrisGlow),
                startY = irisCenter.y - innerRy,
                endY = irisCenter.y + innerRy
            ),
            topLeft = Offset(irisCenter.x - innerRx, irisCenter.y - innerRy),
            size = Size(innerRx * 2f, innerRy * 2f)
        )

        // Glowing lower amber crescent
        drawOval(
            color = Color(0xFFFFE594).copy(alpha = 0.65f),
            topLeft = Offset(irisCenter.x - innerRx * 0.66f, irisCenter.y + innerRy * 0.22f),
            size = Size(innerRx * 1.32f, innerRy * 0.56f)
        )

        // Deep pupil
        val pupilRx = innerRx * 0.44f
        val pupilRy = innerRy * 0.48f
        drawOval(
            color = Color(0xFF2D120E),
            topLeft = Offset(irisCenter.x - pupilRx, irisCenter.y - pupilRy * 1.05f),
            size = Size(pupilRx * 2f, pupilRy * 2f)
        )

        // Crisp upper specular catchlight
        drawOval(
            color = Color.White,
            topLeft = Offset(irisCenter.x - innerRx * 0.72f, irisCenter.y - innerRy * 0.68f),
            size = Size(5.6f * u, 4.6f * u)
        )
        // Secondary lower sparkle catchlight
        drawCircle(
            color = Color.White.copy(alpha = 0.92f),
            radius = 1.9f * u,
            center = Offset(irisCenter.x + innerRx * 0.44f, irisCenter.y + innerRy * 0.40f)
        )
    }

    // Thick tapered upper lash arch
    val upperLash = Path().apply {
        moveTo(center.x - eyeW * 1.04f, center.y + 1.2f * u)
        cubicTo(
            center.x - eyeW * 0.56f,
            topControlY - 1.2f * u,
            center.x + eyeW * 0.56f,
            topControlY - 1.2f * u,
            center.x + eyeW * 1.04f,
            center.y + 1.2f * u
        )
    }
    drawPath(
        path = upperLash,
        color = OutlineInk,
        style = Stroke(width = 3.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Outer wing lash flick
    val outerTipX = center.x - mirror * eyeW * 1.02f
    drawLine(
        color = OutlineInk,
        start = Offset(outerTipX, center.y + 0.8f * u),
        end = Offset(outerTipX - mirror * 4.2f * u, center.y - 2.6f * u),
        strokeWidth = 2.2f * u,
        cap = StrokeCap.Round
    )

    // Soft lower lash outline
    val lowerLash = Path().apply {
        moveTo(center.x - eyeW * 0.68f, center.y + halfH * 0.62f)
        quadraticTo(
            center.x,
            bottomControlY,
            center.x + eyeW * 0.68f,
            center.y + halfH * 0.62f
        )
    }
    drawPath(
        path = lowerLash,
        color = OutlineInk.copy(alpha = 0.55f),
        style = Stroke(width = 1.3f * u, cap = StrokeCap.Round)
    )

    // Delicate upper eyelid crease line
    val creaseY = topControlY * 0.35f + (center.y - halfH) * 0.65f - 4.2f * u
    val creasePath = Path().apply {
        moveTo(center.x - eyeW * 0.70f, creaseY + 2.5f * u)
        quadraticTo(center.x, creaseY - 1.2f * u, center.x + eyeW * 0.70f, creaseY + 2.5f * u)
    }
    drawPath(
        path = creasePath,
        color = OutlineInk.copy(alpha = 0.42f),
        style = Stroke(width = 1.15f * u, cap = StrokeCap.Round)
    )
}

internal fun DrawScope.drawAnimeEyebrow(
    center: Offset,
    u: Float,
    isLeft: Boolean,
    offsetY: Float,
    tiltDeg: Float
) {
    val mirror = if (isLeft) 1f else -1f
    val browW = 16f * u
    val browCenter = Offset(center.x, center.y + offsetY * u)

    withTransform({
        rotate(degrees = tiltDeg * mirror, pivot = browCenter)
    }) {
        val browPath = Path().apply {
            moveTo(browCenter.x - browW, browCenter.y + 1.8f * u)
            quadraticTo(
                browCenter.x,
                browCenter.y - 4.2f * u,
                browCenter.x + browW,
                browCenter.y + 1.2f * u
            )
        }
        drawPath(
            path = browPath,
            color = HairDark,
            style = Stroke(width = 2.6f * u, cap = StrokeCap.Round)
        )
    }
}

internal fun DrawScope.drawCheekBlush(
    leftCheek: Offset,
    rightCheek: Offset,
    u: Float,
    intensity: Float
) {
    val alpha = intensity.coerceIn(0f, 1f)
    if (alpha <= 0.02f) return

    listOf(leftCheek, rightCheek).forEach { center ->
        val rx = 14.5f * u
        val ry = 8.2f * u
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    BlushPink.copy(alpha = 0.56f * alpha),
                    BlushPink.copy(alpha = 0.22f * alpha),
                    Color.Transparent
                ),
                center = center,
                radius = rx * 1.1f
            ),
            topLeft = Offset(center.x - rx, center.y - ry),
            size = Size(rx * 2f, ry * 2f)
        )

        // Cute anime blush hatch marks appear cleanly as shyness/blush rises
        if (alpha > 0.45f) {
            val hatchAlpha = ((alpha - 0.45f) / 0.55f).coerceIn(0f, 1f) * 0.55f
            for (i in -1..1) {
                val hx = center.x + i * 4.5f * u
                drawLine(
                    color = Color(0xFFE84C72).copy(alpha = hatchAlpha),
                    start = Offset(hx - 1.5f * u, center.y + 2.4f * u),
                    end = Offset(hx + 1.5f * u, center.y - 2.4f * u),
                    strokeWidth = 1.25f * u,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

internal fun DrawScope.drawAnimeMouth(
    center: Offset,
    u: Float,
    openness: Float,
    widthFactor: Float,
    smileAmount: Float
) {
    val safeOpen = openness.coerceIn(0f, 1f)
    val safeWidth = widthFactor.coerceIn(0.35f, 1.1f)
    val halfW = (10.5f + 6.5f * safeWidth) * u

    if (safeOpen < 0.08f) {
        // Closed expressive mouth curve (positive = smile, negative = soft sad/worried curve)
        val cornerOffsetY = if (smileAmount >= 0f) {
            -smileAmount * 4.2f * u * 0.55f
        } else {
            -smileAmount * 3.0f * u
        }
        val midControlY = if (smileAmount >= 0f) {
            center.y + abs(smileAmount) * 1.4f * u + 1.5f * u
        } else {
            center.y + smileAmount * 3.4f * u
        }
        val mouthLine = Path().apply {
            moveTo(center.x - halfW * 0.85f, center.y + cornerOffsetY)
            quadraticTo(
                center.x,
                midControlY,
                center.x + halfW * 0.85f,
                center.y + cornerOffsetY
            )
        }
        drawPath(
            path = mouthLine,
            color = OutlineInk,
            style = Stroke(width = 2.1f * u, cap = StrokeCap.Round)
        )
        // Subtle lower lip shadow
        drawLine(
            color = SkinShadow.copy(alpha = 0.7f),
            start = Offset(center.x - 3.5f * u, center.y + 4.0f * u),
            end = Offset(center.x + 3.5f * u, center.y + 4.0f * u),
            strokeWidth = 1.3f * u,
            cap = StrokeCap.Round
        )
        return
    }

    // Open articulated anime mouth for lip-sync & cheerful smiles
    val mouthH = (3.5f + 13.5f * safeOpen) * u
    val topCornerY = center.y - smileAmount * 2.4f * u
    val topMidY = center.y - 1.2f * u
    val bottomMidY = center.y + mouthH

    val cavityPath = Path().apply {
        moveTo(center.x - halfW, topCornerY)
        quadraticTo(center.x, topMidY, center.x + halfW, topCornerY)
        cubicTo(
            center.x + halfW * 0.72f,
            bottomMidY,
            center.x - halfW * 0.72f,
            bottomMidY,
            center.x - halfW,
            topCornerY
        )
        close()
    }

    clipPath(cavityPath) {
        // Dark rose interior
        drawRect(
            color = MouthCavity,
            topLeft = Offset(center.x - halfW * 1.1f, center.y - 6f * u),
            size = Size(halfW * 2.2f, mouthH + 10f * u)
        )

        // Warm coral tongue arc at lower half
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(TongueCoral, Color(0xFFD85374)),
                startY = center.y + mouthH * 0.3f,
                endY = bottomMidY + 2f * u
            ),
            topLeft = Offset(center.x - halfW * 0.72f, center.y + mouthH * 0.34f),
            size = Size(halfW * 1.44f, mouthH * 0.85f)
        )

        // Subtle upper teeth highlight strip when smiling/speaking widely
        if (safeOpen > 0.25f) {
            drawRoundRect(
                color = Color(0xFFFFF8FA),
                topLeft = Offset(center.x - halfW * 0.68f, topMidY - 1.5f * u),
                size = Size(halfW * 1.36f, (2.6f + safeOpen * 1.2f) * u),
                cornerRadius = CornerRadius(2f * u, 2f * u)
            )
        }
    }

    // Crisp outer lip stroke
    drawPath(
        path = cavityPath,
        color = OutlineInk,
        style = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Soft lower lip shadow accent
    drawArc(
        color = SkinShadow.copy(alpha = 0.65f),
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(center.x - 5f * u, bottomMidY + 1.5f * u),
        size = Size(10f * u, 3.2f * u),
        style = Stroke(width = 1.4f * u, cap = StrokeCap.Round)
    )
}

internal fun DrawScope.drawSweaterAndNeck(
    neckCenter: Offset,
    u: Float,
    breathY: Float,
    breathScaleX: Float,
    breathScaleY: Float
) {
    val bodyPivot = Offset(neckCenter.x, neckCenter.y + 42f * u)
    withTransform({
        translate(top = breathY)
        scale(scaleX = breathScaleX, scaleY = breathScaleY, pivot = bodyPivot)
    }) {
        // Neck column
        val neckW = 16.5f * u
        val neckPath = Path().apply {
            moveTo(neckCenter.x - neckW, neckCenter.y - 16f * u)
            lineTo(neckCenter.x + neckW, neckCenter.y - 16f * u)
            lineTo(neckCenter.x + neckW * 1.15f, neckCenter.y + 28f * u)
            lineTo(neckCenter.x - neckW * 1.15f, neckCenter.y + 28f * u)
            close()
        }
        drawPath(
            path = neckPath,
            brush = Brush.verticalGradient(
                colors = listOf(SkinShadow, SkinBase),
                startY = neckCenter.y - 8f * u,
                endY = neckCenter.y + 24f * u
            )
        )
        // Chin shadow on neck
        drawOval(
            color = SkinShadow.copy(alpha = 0.85f),
            topLeft = Offset(neckCenter.x - neckW * 0.95f, neckCenter.y - 14f * u),
            size = Size(neckW * 1.9f, 18f * u)
        )
        // Neck outlines
        drawLine(
            color = OutlineInk,
            start = Offset(neckCenter.x - neckW * 0.95f, neckCenter.y - 4f * u),
            end = Offset(neckCenter.x - neckW * 1.08f, neckCenter.y + 22f * u),
            strokeWidth = 2.1f * u,
            cap = StrokeCap.Round
        )
        drawLine(
            color = OutlineInk,
            start = Offset(neckCenter.x + neckW * 0.95f, neckCenter.y - 4f * u),
            end = Offset(neckCenter.x + neckW * 1.08f, neckCenter.y + 22f * u),
            strokeWidth = 2.1f * u,
            cap = StrokeCap.Round
        )

        // Cozy pink knit sweater torso & sloping shoulders
        val shoulderTopY = neckCenter.y + 18f * u
        val torsoBottomY = neckCenter.y + 210f * u
        val torsoPath = Path().apply {
            moveTo(neckCenter.x - 28f * u, shoulderTopY)
            cubicTo(
                neckCenter.x - 76f * u,
                shoulderTopY + 12f * u,
                neckCenter.x - 108f * u,
                shoulderTopY + 46f * u,
                neckCenter.x - 118f * u,
                torsoBottomY
            )
            lineTo(neckCenter.x + 118f * u, torsoBottomY)
            cubicTo(
                neckCenter.x + 108f * u,
                shoulderTopY + 46f * u,
                neckCenter.x + 76f * u,
                shoulderTopY + 12f * u,
                neckCenter.x + 28f * u,
                shoulderTopY
            )
            quadraticTo(neckCenter.x, shoulderTopY + 18f * u, neckCenter.x - 28f * u, shoulderTopY)
            close()
        }
        drawPath(
            path = torsoPath,
            brush = Brush.verticalGradient(
                colors = listOf(SweaterLight, SweaterBase, SweaterShade),
                startY = shoulderTopY,
                endY = neckCenter.y + 150f * u
            )
        )

        // Subtle vertical cable-knit texture lines
        for (i in -2..2) {
            val kx = neckCenter.x + i * 24f * u
            val startY = shoulderTopY + 26f * u + abs(i) * 8f * u
            drawLine(
                color = SweaterShade.copy(alpha = 0.55f),
                start = Offset(kx, startY),
                end = Offset(kx + i * 3f * u, startY + 95f * u),
                strokeWidth = 2.0f * u,
                cap = StrokeCap.Round
            )
        }

        // Ribbed crewneck collar band
        val collarRect = Rect(
            left = neckCenter.x - 32f * u,
            top = shoulderTopY - 4f * u,
            right = neckCenter.x + 32f * u,
            bottom = shoulderTopY + 24f * u
        )
        drawArc(
            color = SweaterLight,
            startAngle = 8f,
            sweepAngle = 164f,
            useCenter = false,
            topLeft = collarRect.topLeft,
            size = collarRect.size,
            style = Stroke(width = 11f * u, cap = StrokeCap.Round)
        )
        drawArc(
            color = OutlineInk,
            startAngle = 10f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = collarRect.topLeft,
            size = collarRect.size,
            style = Stroke(width = 2.1f * u, cap = StrokeCap.Round)
        )

        // Outer sweater silhouette outline
        val outerStroke = Path().apply {
            moveTo(neckCenter.x - 28f * u, shoulderTopY)
            cubicTo(
                neckCenter.x - 76f * u,
                shoulderTopY + 12f * u,
                neckCenter.x - 108f * u,
                shoulderTopY + 46f * u,
                neckCenter.x - 118f * u,
                torsoBottomY
            )
            moveTo(neckCenter.x + 28f * u, shoulderTopY)
            cubicTo(
                neckCenter.x + 76f * u,
                shoulderTopY + 12f * u,
                neckCenter.x + 108f * u,
                shoulderTopY + 46f * u,
                neckCenter.x + 118f * u,
                torsoBottomY
            )
        }
        drawPath(
            path = outerStroke,
            color = OutlineInk,
            style = Stroke(width = 2.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Cute mini bunny brooch pin on Romi's sweater
        val pinCenter = Offset(neckCenter.x + 28f * u, shoulderTopY + 48f * u)
        drawOval(
            color = Color.White,
            topLeft = Offset(pinCenter.x - 4.5f * u, pinCenter.y - 12f * u),
            size = Size(3.6f * u, 9f * u)
        )
        drawOval(
            color = Color.White,
            topLeft = Offset(pinCenter.x + 1f * u, pinCenter.y - 12f * u),
            size = Size(3.6f * u, 9f * u)
        )
        drawCircle(color = Color.White, radius = 6.2f * u, center = pinCenter)
        drawCircle(
            color = OutlineInk.copy(alpha = 0.7f),
            radius = 6.2f * u,
            center = pinCenter,
            style = Stroke(width = 1.2f * u)
        )
        drawCircle(color = BlushPink, radius = 1.6f * u, center = Offset(pinCenter.x, pinCenter.y + 4.8f * u))
    }
}
