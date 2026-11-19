package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.avatar.AvatarAnimationState
import com.example.avatar.AvatarRenderState
import com.example.avatar.StructuredExpression
import com.example.model.RomiMood
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private data class AvatarExpressionSpec(
    val leftEyeOpen: Float,
    val rightEyeOpen: Float,
    val leftEyeSmileArc: Float,
    val rightEyeSmileArc: Float,
    val leftBrowOffsetY: Float,
    val rightBrowOffsetY: Float,
    val leftBrowTilt: Float,
    val rightBrowTilt: Float,
    val gazeX: Float,
    val gazeY: Float,
    val pupilScale: Float,
    val blushIntensity: Float,
    val smileAmount: Float,
    val restMouthOpen: Float,
    val restMouthWidth: Float,
    val headTiltDeg: Float
)

private fun expressionSpecForBase(
    expression: StructuredExpression,
    mood: RomiMood
): AvatarExpressionSpec = when {
    expression == StructuredExpression.SAD -> AvatarExpressionSpec(
        leftEyeOpen = 0.74f,
        rightEyeOpen = 0.74f,
        leftEyeSmileArc = 0f,
        rightEyeSmileArc = 0f,
        leftBrowOffsetY = 1.6f,
        rightBrowOffsetY = 1.6f,
        leftBrowTilt = -13f,
        rightBrowTilt = -13f,
        gazeX = -0.06f,
        gazeY = 0.24f,
        pupilScale = 1.02f,
        blushIntensity = 0.20f,
        smileAmount = -0.65f,
        restMouthOpen = 0.0f,
        restMouthWidth = 0.50f,
        headTiltDeg = -2.4f
    )
    expression == StructuredExpression.SHY || mood == RomiMood.SHY -> AvatarExpressionSpec(
        leftEyeOpen = 0.88f,
        rightEyeOpen = 0.88f,
        leftEyeSmileArc = 0.10f,
        rightEyeSmileArc = 0.10f,
        leftBrowOffsetY = -1.0f,
        rightBrowOffsetY = -1.0f,
        leftBrowTilt = -9f,
        rightBrowTilt = -9f,
        gazeX = 0.26f,
        gazeY = 0.20f,
        pupilScale = 1.06f,
        blushIntensity = 0.95f,
        smileAmount = 0.42f,
        restMouthOpen = 0.0f,
        restMouthWidth = 0.48f,
        headTiltDeg = 4.2f
    )
    expression == StructuredExpression.SURPRISED || mood == RomiMood.SURPRISED -> AvatarExpressionSpec(
        leftEyeOpen = 1.16f,
        rightEyeOpen = 1.16f,
        leftEyeSmileArc = 0f,
        rightEyeSmileArc = 0f,
        leftBrowOffsetY = -6.8f,
        rightBrowOffsetY = -6.8f,
        leftBrowTilt = 5f,
        rightBrowTilt = 5f,
        gazeX = 0f,
        gazeY = -0.06f,
        pupilScale = 0.82f,
        blushIntensity = 0.52f,
        smileAmount = -0.05f,
        restMouthOpen = 0.54f,
        restMouthWidth = 0.42f,
        headTiltDeg = -0.6f
    )
    expression == StructuredExpression.THOUGHTFUL || mood == RomiMood.SLEEPY -> AvatarExpressionSpec(
        leftEyeOpen = if (mood == RomiMood.SLEEPY) 0.42f else 0.86f,
        rightEyeOpen = if (mood == RomiMood.SLEEPY) 0.42f else 0.86f,
        leftEyeSmileArc = 0f,
        rightEyeSmileArc = 0f,
        leftBrowOffsetY = -1.4f,
        rightBrowOffsetY = 1.2f,
        leftBrowTilt = 6f,
        rightBrowTilt = -5f,
        gazeX = 0.34f,
        gazeY = -0.26f,
        pupilScale = 0.96f,
        blushIntensity = 0.28f,
        smileAmount = 0.12f,
        restMouthOpen = 0.0f,
        restMouthWidth = 0.44f,
        headTiltDeg = 4.6f
    )
    mood == RomiMood.WINK -> AvatarExpressionSpec(
        leftEyeOpen = 1.02f,
        rightEyeOpen = 0.0f,
        leftEyeSmileArc = 0.25f,
        rightEyeSmileArc = 1.0f,
        leftBrowOffsetY = -2.0f,
        rightBrowOffsetY = 2.0f,
        leftBrowTilt = 4f,
        rightBrowTilt = -4f,
        gazeX = -0.10f,
        gazeY = 0f,
        pupilScale = 1.04f,
        blushIntensity = 0.52f,
        smileAmount = 0.90f,
        restMouthOpen = 0.18f,
        restMouthWidth = 0.76f,
        headTiltDeg = 3.6f
    )
    mood == RomiMood.ATTITUDE && expression == StructuredExpression.PLAYFUL -> AvatarExpressionSpec(
        leftEyeOpen = 0.74f,
        rightEyeOpen = 0.74f,
        leftEyeSmileArc = 0f,
        rightEyeSmileArc = 0f,
        leftBrowOffsetY = 1.8f,
        rightBrowOffsetY = -4.2f,
        leftBrowTilt = -10f,
        rightBrowTilt = 6f,
        gazeX = -0.32f,
        gazeY = -0.05f,
        pupilScale = 0.94f,
        blushIntensity = 0.24f,
        smileAmount = 0.40f,
        restMouthOpen = 0.0f,
        restMouthWidth = 0.58f,
        headTiltDeg = -3.8f
    )
    expression == StructuredExpression.HAPPY || expression == StructuredExpression.PLAYFUL -> AvatarExpressionSpec(
        leftEyeOpen = 1.0f,
        rightEyeOpen = 1.0f,
        leftEyeSmileArc = 0.32f,
        rightEyeSmileArc = 0.32f,
        leftBrowOffsetY = -2.0f,
        rightBrowOffsetY = -2.0f,
        leftBrowTilt = 3f,
        rightBrowTilt = 3f,
        gazeX = 0f,
        gazeY = 0f,
        pupilScale = 1.05f,
        blushIntensity = 0.52f,
        smileAmount = 0.90f,
        restMouthOpen = 0.14f,
        restMouthWidth = 0.74f,
        headTiltDeg = 1.4f
    )
    else -> AvatarExpressionSpec(
        leftEyeOpen = 0.98f,
        rightEyeOpen = 0.98f,
        leftEyeSmileArc = 0.14f,
        rightEyeSmileArc = 0.14f,
        leftBrowOffsetY = -0.8f,
        rightBrowOffsetY = -0.8f,
        leftBrowTilt = 2f,
        rightBrowTilt = 2f,
        gazeX = 0f,
        gazeY = 0f,
        pupilScale = 1.02f,
        blushIntensity = 0.36f,
        smileAmount = 0.52f,
        restMouthOpen = 0.0f,
        restMouthWidth = 0.62f,
        headTiltDeg = 0.6f
    )
}

private fun resolveExpressionSpec(avatarState: AvatarRenderState): AvatarExpressionSpec {
    return when (avatarState.animationState) {
        AvatarAnimationState.IDLE -> expressionSpecForBase(StructuredExpression.NEUTRAL, avatarState.mood)
        AvatarAnimationState.LISTENING -> AvatarExpressionSpec(
            leftEyeOpen = 1.06f,
            rightEyeOpen = 1.06f,
            leftEyeSmileArc = 0.18f,
            rightEyeSmileArc = 0.18f,
            leftBrowOffsetY = -2.6f,
            rightBrowOffsetY = -2.6f,
            leftBrowTilt = 4f,
            rightBrowTilt = 4f,
            gazeX = 0f,
            gazeY = -0.02f,
            pupilScale = 1.06f,
            blushIntensity = 0.40f,
            smileAmount = 0.58f,
            restMouthOpen = 0.0f,
            restMouthWidth = 0.60f,
            headTiltDeg = 2.8f
        )
        AvatarAnimationState.THINKING -> expressionSpecForBase(StructuredExpression.THOUGHTFUL, avatarState.mood)
        AvatarAnimationState.SPEAKING -> expressionSpecForBase(avatarState.expression, avatarState.mood)
        AvatarAnimationState.HAPPY -> expressionSpecForBase(avatarState.expression, avatarState.mood)
        AvatarAnimationState.SHY -> expressionSpecForBase(StructuredExpression.SHY, avatarState.mood)
        AvatarAnimationState.SURPRISED -> expressionSpecForBase(StructuredExpression.SURPRISED, avatarState.mood)
        AvatarAnimationState.SAD -> expressionSpecForBase(StructuredExpression.SAD, avatarState.mood)
        AvatarAnimationState.WINK -> expressionSpecForBase(StructuredExpression.PLAYFUL, RomiMood.WINK)
    }
}

@Composable
fun RomiLiveAvatar(
    avatarState: AvatarRenderState,
    affectionBurstTick: Int,
    modifier: Modifier = Modifier
) {
    val animState = avatarState.animationState
    val spec = remember(animState, avatarState.expression, avatarState.mood) {
        resolveExpressionSpec(avatarState)
    }

    // Reactive gentle pop on character tap or reply arrival
    var tapPopped by remember { mutableStateOf(false) }
    LaunchedEffect(affectionBurstTick) {
        if (affectionBurstTick > 0) {
            tapPopped = true
            delay(180L)
            tapPopped = false
        }
    }

    // Continuous subtle breathing and natural head sway
    val infiniteTransition = rememberInfiniteTransition(label = "romi_2d_rig")
    val breathPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    animState == AvatarAnimationState.SAD -> 3800
                    avatarState.mood == RomiMood.SLEEPY -> 3800
                    else -> 2800
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "breath_phase"
    )
    val swayPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sway_phase"
    )

    // Smooth spring/tween transitions across all 9 animation states
    val exprSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val blinkAmount by animateFloatAsState(
        targetValue = avatarState.blinkProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 55, easing = FastOutSlowInEasing),
        label = "blink_amount"
    )
    val leftEyeBaseOpen by animateFloatAsState(spec.leftEyeOpen, exprSpring, label = "l_eye_open")
    val rightEyeBaseOpen by animateFloatAsState(spec.rightEyeOpen, exprSpring, label = "r_eye_open")
    val leftEyeSmileArc by animateFloatAsState(spec.leftEyeSmileArc, exprSpring, label = "l_eye_arc")
    val rightEyeSmileArc by animateFloatAsState(spec.rightEyeSmileArc, exprSpring, label = "r_eye_arc")
    val leftBrowOffsetY by animateFloatAsState(spec.leftBrowOffsetY, exprSpring, label = "l_brow_y")
    val rightBrowOffsetY by animateFloatAsState(spec.rightBrowOffsetY, exprSpring, label = "r_brow_y")
    val leftBrowTilt by animateFloatAsState(spec.leftBrowTilt, exprSpring, label = "l_brow_tilt")
    val rightBrowTilt by animateFloatAsState(spec.rightBrowTilt, exprSpring, label = "r_brow_tilt")

    val targetGazeX = when (animState) {
        AvatarAnimationState.THINKING -> 0.34f
        AvatarAnimationState.LISTENING -> 0f
        AvatarAnimationState.SAD -> -0.06f + avatarState.gazeX * 0.35f
        else -> spec.gazeX + avatarState.gazeX
    }
    val targetGazeY = when (animState) {
        AvatarAnimationState.THINKING -> -0.26f
        AvatarAnimationState.LISTENING -> -0.02f
        AvatarAnimationState.SAD -> 0.24f + avatarState.gazeY * 0.35f
        else -> spec.gazeY + avatarState.gazeY
    }
    val gazeX by animateFloatAsState(targetGazeX, tween(280), label = "gaze_x")
    val gazeY by animateFloatAsState(targetGazeY, tween(280), label = "gaze_y")
    val pupilScale by animateFloatAsState(spec.pupilScale, exprSpring, label = "pupil_scale")
    val blushIntensity by animateFloatAsState(spec.blushIntensity, tween(380), label = "blush")
    val smileAmount by animateFloatAsState(spec.smileAmount, exprSpring, label = "smile")
    val restMouthOpen by animateFloatAsState(spec.restMouthOpen, exprSpring, label = "rest_mouth_open")
    val restMouthWidth by animateFloatAsState(spec.restMouthWidth, exprSpring, label = "rest_mouth_w")

    // Real-time lip sync driven strictly by LipSyncController frames (25 FPS, no random mouth movement)
    val lipSyncFrame = avatarState.lipSyncFrame
    val isSpeakingActive = lipSyncFrame.isActive && animState == AvatarAnimationState.SPEAKING
    val speechMouthOpen by animateFloatAsState(
        targetValue = if (isSpeakingActive) lipSyncFrame.mouthOpenness else 0f,
        animationSpec = tween(
            durationMillis = if (isSpeakingActive) 36 else 65,
            easing = FastOutSlowInEasing
        ),
        label = "speech_mouth_open"
    )
    val speechMouthWidth by animateFloatAsState(
        targetValue = if (isSpeakingActive) lipSyncFrame.mouthWidth else restMouthWidth,
        animationSpec = tween(durationMillis = 55),
        label = "speech_mouth_width"
    )
    val speakingBlend by animateFloatAsState(
        targetValue = if (isSpeakingActive) 1f else 0f,
        animationSpec = tween(durationMillis = if (isSpeakingActive) 90 else 65),
        label = "speaking_blend"
    )

    val moodHeadTilt by animateFloatAsState(
        targetValue = spec.headTiltDeg,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "mood_head_tilt"
    )
    val tapScale by animateFloatAsState(
        targetValue = if (tapPopped) 1.045f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tap_pop"
    )
    val tapBounceY by animateFloatAsState(
        targetValue = if (tapPopped) -10f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tap_y"
    )

    Canvas(modifier = modifier) {
        val u = min(size.width / 320f, size.height / 390f) * 1.15f
        val cx = size.width * 0.48f
        val headBaseY = size.height * 0.54f

        // Organic breathing & layered head kinematics
        val breathSin = sin(breathPhase)
        val breathScaleY = 1f + breathSin * 0.015f
        val breathScaleX = 1f + breathSin * 0.008f
        val bodyBreathY = -breathSin * 3.0f * u

        val headLagSin = sin(breathPhase - 0.45f)
        // Subtle speech-energy micro-emphasis proportional to real lip-sync mouth openness
        val speakNodY = speakingBlend * speechMouthOpen * 1.6f * u
        val idleSwayDeg = sin(swayPhase) * 1.6f + cos(swayPhase * 2f) * 0.55f
        val totalHeadTilt = moodHeadTilt + idleSwayDeg
        val headOffsetX = sin(swayPhase) * 2.2f * u
        val headOffsetY = -headLagSin * 3.6f * u + speakNodY + tapBounceY

        val backHairSwayDeg = totalHeadTilt * 0.72f + sin(swayPhase - 0.8f) * 1.8f
        val bangsLagX = -sin(swayPhase - 0.4f) * 1.8f * u

        val headCenter = Offset(cx + headOffsetX, headBaseY + headOffsetY)
        val neckPivot = Offset(cx + headOffsetX * 0.4f, headBaseY + 68f * u + bodyBreathY)

        // Subtle tap scale around lower bust
        withTransform({
            scale(scaleX = tapScale, scaleY = tapScale, pivot = Offset(cx, size.height * 0.85f))
        }) {
            // Layer 1: Flowing back hair (behind neck & shoulders)
            withTransform({
                rotate(degrees = backHairSwayDeg, pivot = headCenter)
            }) {
                drawBackHair(headCenter = headCenter, u = u)
            }

            // Layer 2: Neck & Cozy Pink Knit Sweater Torso
            drawSweaterAndNeck(
                neckCenter = Offset(cx + headOffsetX * 0.25f, headBaseY + 68f * u),
                u = u,
                breathY = bodyBreathY,
                breathScaleX = breathScaleX,
                breathScaleY = breathScaleY
            )

            // Layer 3: Head, Face, Expressions, Lip-Sync Mouth & Front Hair
            withTransform({
                rotate(degrees = totalHeadTilt, pivot = neckPivot)
            }) {
                drawEarsAndJaw(headCenter = headCenter, u = u)

                // Cheek blush
                drawCheekBlush(
                    leftCheek = Offset(headCenter.x - 29f * u, headCenter.y + 18f * u),
                    rightCheek = Offset(headCenter.x + 29f * u, headCenter.y + 18f * u),
                    u = u,
                    intensity = blushIntensity
                )

                // Eyes with independent blink + mood aperture + gaze tracking
                val leftEyeOpen = (leftEyeBaseOpen * (1f - blinkAmount)).coerceAtLeast(0f)
                val rightEyeOpen = (rightEyeBaseOpen * (1f - blinkAmount)).coerceAtLeast(0f)
                val eyeParallaxX = gazeX * 1.5f * u

                drawAnimeEye(
                    center = Offset(headCenter.x - 24f * u + eyeParallaxX, headCenter.y + 3f * u),
                    u = u,
                    isLeftEye = true,
                    openness = leftEyeOpen,
                    smileArc = leftEyeSmileArc.coerceAtLeast(blinkAmount * 0.4f),
                    gazeX = gazeX,
                    gazeY = gazeY,
                    pupilScale = pupilScale
                )
                drawAnimeEye(
                    center = Offset(headCenter.x + 24f * u + eyeParallaxX, headCenter.y + 3f * u),
                    u = u,
                    isLeftEye = false,
                    openness = rightEyeOpen,
                    smileArc = rightEyeSmileArc.coerceAtLeast(blinkAmount * 0.4f),
                    gazeX = gazeX,
                    gazeY = gazeY,
                    pupilScale = pupilScale
                )

                // Eyebrows
                val blinkBrowDip = blinkAmount * 1.8f * u
                drawAnimeEyebrow(
                    center = Offset(headCenter.x - 24f * u, headCenter.y - 16f * u + blinkBrowDip),
                    u = u,
                    isLeft = true,
                    offsetY = leftBrowOffsetY,
                    tiltDeg = leftBrowTilt
                )
                drawAnimeEyebrow(
                    center = Offset(headCenter.x + 24f * u, headCenter.y - 16f * u + blinkBrowDip),
                    u = u,
                    isLeft = false,
                    offsetY = rightBrowOffsetY,
                    tiltDeg = rightBrowTilt
                )

                // Delicate anime nose with subtle 2.5D parallax
                val nosePos = Offset(headCenter.x + gazeX * 1.1f * u, headCenter.y + 22.5f * u)
                drawLine(
                    color = SkinShadow,
                    start = Offset(nosePos.x - 1.2f * u, nosePos.y - 2.2f * u),
                    end = Offset(nosePos.x + 0.6f * u, nosePos.y + 1.2f * u),
                    strokeWidth = 1.6f * u,
                    cap = StrokeCap.Round
                )

                // Articulated lip-sync mouth driven purely by LipSyncController + expression rest mouth
                val finalMouthOpen = restMouthOpen * (1f - speakingBlend) + speechMouthOpen * speakingBlend
                val finalMouthWidth = restMouthWidth * (1f - speakingBlend) + speechMouthWidth * speakingBlend
                val finalSmile = smileAmount * (1f - speakingBlend * 0.35f)

                drawAnimeMouth(
                    center = Offset(headCenter.x + gazeX * 0.6f * u, headCenter.y + 38f * u),
                    u = u,
                    openness = finalMouthOpen,
                    widthFactor = finalMouthWidth,
                    smileAmount = finalSmile
                )

                // Front bangs & cheek-framing hair locks with secondary sway
                drawFrontHairAndBangs(
                    headCenter = headCenter,
                    u = u,
                    bangsLagX = bangsLagX
                )

                // Expressive mood & state accents beside Romi's face
                drawExpressionAccents(
                    animationState = animState,
                    expression = avatarState.expression,
                    mood = avatarState.mood,
                    headCenter = headCenter,
                    u = u,
                    phase = swayPhase
                )
            }
        }
    }
}

private fun DrawScope.drawBackHair(headCenter: Offset, u: Float) {
    val topY = headCenter.y - 78f * u
    val tipY = headCenter.y + 128f * u
    val backPath = Path().apply {
        moveTo(headCenter.x, topY)
        cubicTo(
            headCenter.x - 74f * u,
            topY + 4f * u,
            headCenter.x - 84f * u,
            headCenter.y + 42f * u,
            headCenter.x - 72f * u,
            tipY
        )
        quadraticTo(
            headCenter.x - 52f * u,
            tipY + 14f * u,
            headCenter.x - 32f * u,
            tipY - 4f * u
        )
        lineTo(headCenter.x + 32f * u, tipY - 4f * u)
        quadraticTo(
            headCenter.x + 52f * u,
            tipY + 14f * u,
            headCenter.x + 72f * u,
            tipY
        )
        cubicTo(
            headCenter.x + 84f * u,
            headCenter.y + 42f * u,
            headCenter.x + 74f * u,
            topY + 4f * u,
            headCenter.x,
            topY
        )
        close()
    }
    drawPath(
        path = backPath,
        brush = Brush.verticalGradient(
            colors = listOf(HairDark, HairMid, HairDark),
            startY = topY,
            endY = tipY
        )
    )
    drawPath(
        path = backPath,
        color = OutlineInk,
        style = Stroke(width = 2.4f * u, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawEarsAndJaw(headCenter: Offset, u: Float) {
    // Ears
    listOf(-1f, 1f).forEach { side ->
        val earCenter = Offset(headCenter.x + side * 52f * u, headCenter.y + 12f * u)
        drawOval(
            color = SkinBase,
            topLeft = Offset(earCenter.x - 7f * u, earCenter.y - 12f * u),
            size = Size(14f * u, 24f * u)
        )
        drawOval(
            color = SkinShadow,
            topLeft = Offset(earCenter.x - 3.5f * u, earCenter.y - 6f * u),
            size = Size(7f * u, 12f * u)
        )
        drawOval(
            color = OutlineInk,
            topLeft = Offset(earCenter.x - 7f * u, earCenter.y - 12f * u),
            size = Size(14f * u, 24f * u),
            style = Stroke(width = 1.9f * u)
        )
    }

    // Smooth anime jawline & face shape
    val jawPath = Path().apply {
        moveTo(headCenter.x - 51f * u, headCenter.y - 26f * u)
        cubicTo(
            headCenter.x - 53f * u,
            headCenter.y + 18f * u,
            headCenter.x - 36f * u,
            headCenter.y + 48f * u,
            headCenter.x,
            headCenter.y + 57f * u
        )
        cubicTo(
            headCenter.x + 36f * u,
            headCenter.y + 48f * u,
            headCenter.x + 53f * u,
            headCenter.y + 18f * u,
            headCenter.x + 51f * u,
            headCenter.y - 26f * u
        )
        cubicTo(
            headCenter.x + 48f * u,
            headCenter.y - 64f * u,
            headCenter.x - 48f * u,
            headCenter.y - 64f * u,
            headCenter.x - 51f * u,
            headCenter.y - 26f * u
        )
        close()
    }
    drawPath(
        path = jawPath,
        brush = Brush.radialGradient(
            colors = listOf(SkinHighlight, SkinBase, SkinShadow.copy(alpha = 0.65f)),
            center = Offset(headCenter.x, headCenter.y + 8f * u),
            radius = 70f * u
        )
    )
    drawPath(
        path = jawPath,
        color = OutlineInk,
        style = Stroke(width = 2.2f * u, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawFrontHairAndBangs(
    headCenter: Offset,
    u: Float,
    bangsLagX: Float
) {
    val crownY = headCenter.y - 77f * u

    // Side framing hair locks (left & right curving softly past cheeks)
    val leftLock = Path().apply {
        moveTo(headCenter.x - 8f * u, crownY + 2f * u)
        cubicTo(
            headCenter.x - 62f * u,
            crownY + 4f * u,
            headCenter.x - 74f * u,
            headCenter.y + 38f * u,
            headCenter.x - 52f * u + bangsLagX * 0.7f,
            headCenter.y + 106f * u
        )
        cubicTo(
            headCenter.x - 42f * u + bangsLagX * 0.7f,
            headCenter.y + 84f * u,
            headCenter.x - 44f * u,
            headCenter.y + 24f * u,
            headCenter.x - 46f * u,
            headCenter.y - 18f * u
        )
        close()
    }
    val rightLock = Path().apply {
        moveTo(headCenter.x - 8f * u, crownY + 2f * u)
        cubicTo(
            headCenter.x + 58f * u,
            crownY + 2f * u,
            headCenter.x + 74f * u,
            headCenter.y + 38f * u,
            headCenter.x + 52f * u + bangsLagX * 0.7f,
            headCenter.y + 106f * u
        )
        cubicTo(
            headCenter.x + 42f * u + bangsLagX * 0.7f,
            headCenter.y + 84f * u,
            headCenter.x + 44f * u,
            headCenter.y + 24f * u,
            headCenter.x + 46f * u,
            headCenter.y - 18f * u
        )
        close()
    }

    val hairBrush = Brush.verticalGradient(
        colors = listOf(HairLight, HairMid, HairDark),
        startY = crownY,
        endY = headCenter.y + 105f * u
    )
    listOf(leftLock, rightLock).forEach { lock ->
        drawPath(path = lock, brush = hairBrush)
        drawPath(path = lock, color = OutlineInk, style = Stroke(width = 2.2f * u, join = StrokeJoin.Round))
    }

    // Sweeping parted bangs across forehead
    val bangsPath = Path().apply {
        moveTo(headCenter.x - 50f * u, headCenter.y - 12f * u)
        cubicTo(
            headCenter.x - 54f * u,
            crownY + 6f * u,
            headCenter.x - 24f * u,
            crownY,
            headCenter.x - 4f * u,
            crownY + 1f * u
        )
        cubicTo(
            headCenter.x + 26f * u,
            crownY,
            headCenter.x + 54f * u,
            crownY + 8f * u,
            headCenter.x + 50f * u,
            headCenter.y - 12f * u
        )
        // Rightmost bang swoop
        quadraticTo(
            headCenter.x + 38f * u + bangsLagX,
            headCenter.y - 26f * u,
            headCenter.x + 26f * u + bangsLagX,
            headCenter.y - 12f * u
        )
        // Center-right wispy strand
        quadraticTo(
            headCenter.x + 14f * u + bangsLagX,
            headCenter.y - 22f * u,
            headCenter.x + 2f * u + bangsLagX,
            headCenter.y - 10f * u
        )
        // Center parted notch & left swoop
        quadraticTo(
            headCenter.x - 10f * u + bangsLagX,
            headCenter.y - 27f * u,
            headCenter.x - 22f * u + bangsLagX,
            headCenter.y - 12f * u
        )
        quadraticTo(
            headCenter.x - 36f * u + bangsLagX,
            headCenter.y - 25f * u,
            headCenter.x - 50f * u,
            headCenter.y - 12f * u
        )
        close()
    }
    drawPath(
        path = bangsPath,
        brush = Brush.verticalGradient(
            colors = listOf(HairLight, HairMid),
            startY = crownY,
            endY = headCenter.y - 8f * u
        )
    )
    drawPath(
        path = bangsPath,
        color = OutlineInk,
        style = Stroke(width = 2.2f * u, join = StrokeJoin.Round)
    )

    // Glossy anime hair highlight arc across crown
    val shinePath = Path().apply {
        moveTo(headCenter.x - 32f * u, crownY + 26f * u)
        quadraticTo(headCenter.x - 4f * u, crownY + 15f * u, headCenter.x + 28f * u, crownY + 28f * u)
    }
    drawPath(
        path = shinePath,
        color = HairShine.copy(alpha = 0.72f),
        style = Stroke(width = 5.2f * u, cap = StrokeCap.Round)
    )

    // Cute hair ahoge / top flyaway strand
    val ahogePath = Path().apply {
        moveTo(headCenter.x - 2f * u, crownY + 1f * u)
        quadraticTo(
            headCenter.x + 4f * u + bangsLagX * 1.4f,
            crownY - 14f * u,
            headCenter.x + 16f * u + bangsLagX * 1.6f,
            crownY - 8f * u
        )
    }
    drawPath(
        path = ahogePath,
        color = OutlineInk,
        style = Stroke(width = 2.4f * u, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawExpressionAccents(
    animationState: AvatarAnimationState,
    expression: StructuredExpression,
    mood: RomiMood,
    headCenter: Offset,
    u: Float,
    phase: Float
) {
    val bob = sin(phase * 2f) * 3.5f * u
    when {
        animationState == AvatarAnimationState.SAD || expression == StructuredExpression.SAD -> {
            // Soft glistening lavender-blue teardrop glint near lower left eye
            val dropCenter = Offset(headCenter.x - 36f * u, headCenter.y + 14f * u + bob * 0.25f)
            val dropPath = Path().apply {
                moveTo(dropCenter.x, dropCenter.y - 4.5f * u)
                cubicTo(
                    dropCenter.x + 4f * u,
                    dropCenter.y + 1.2f * u,
                    dropCenter.x + 3.2f * u,
                    dropCenter.y + 5.4f * u,
                    dropCenter.x,
                    dropCenter.y + 5.4f * u
                )
                cubicTo(
                    dropCenter.x - 3.2f * u,
                    dropCenter.y + 5.4f * u,
                    dropCenter.x - 4f * u,
                    dropCenter.y + 1.2f * u,
                    dropCenter.x,
                    dropCenter.y - 4.5f * u
                )
                close()
            }
            drawPath(dropPath, color = Color(0xFF93C5FD).copy(alpha = 0.88f))
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = 1.1f * u,
                center = Offset(dropCenter.x - 0.8f * u, dropCenter.y + 1.6f * u)
            )
        }
        animationState == AvatarAnimationState.WINK || mood == RomiMood.WINK -> {
            // Playful 4-point star sparkle next to winking eye
            val starPos = Offset(headCenter.x + 56f * u, headCenter.y - 12f * u + bob * 0.6f)
            val r = 8f * u
            val star = Path().apply {
                moveTo(starPos.x, starPos.y - r)
                quadraticTo(starPos.x, starPos.y, starPos.x + r, starPos.y)
                quadraticTo(starPos.x, starPos.y, starPos.x, starPos.y + r)
                quadraticTo(starPos.x, starPos.y, starPos.x - r, starPos.y)
                quadraticTo(starPos.x, starPos.y, starPos.x, starPos.y - r)
                close()
            }
            drawPath(star, color = Color(0xFFFFD166))
        }
        animationState == AvatarAnimationState.SURPRISED || expression == StructuredExpression.SURPRISED -> {
            // Manga surprise exclamation pop lines
            val origin = Offset(headCenter.x - 60f * u, headCenter.y - 48f * u)
            for (i in 0..2) {
                val angle = (-150f + i * 24f) * (PI / 180f)
                val start = Offset(
                    origin.x + cos(angle).toFloat() * 4f * u,
                    origin.y + sin(angle).toFloat() * 4f * u
                )
                val end = Offset(
                    origin.x + cos(angle).toFloat() * 16f * u,
                    origin.y + sin(angle).toFloat() * 16f * u
                )
                drawLine(
                    color = Color(0xFFFDE047),
                    start = start,
                    end = end,
                    strokeWidth = 2.6f * u,
                    cap = StrokeCap.Round
                )
            }
        }
        animationState == AvatarAnimationState.THINKING -> {
            // Gentle floating thought bubbles
            listOf(
                Triple(-60f, -34f, 2.5f),
                Triple(-68f, -44f, 4.0f),
                Triple(-76f, -56f, 5.5f)
            ).forEach { (dx, dy, r) ->
                drawCircle(
                    color = Color(0xFFE9D5FF).copy(alpha = 0.85f),
                    radius = r * u,
                    center = Offset(headCenter.x + dx * u, headCenter.y + dy * u + bob * 0.4f)
                )
            }
        }
        mood == RomiMood.ATTITUDE && expression == StructuredExpression.PLAYFUL -> {
            val pos = Offset(headCenter.x - 62f * u, headCenter.y - 36f * u + bob * 0.5f)
            val r = 7f * u
            drawLine(
                color = Color(0xFFE879F9),
                start = Offset(pos.x - r, pos.y),
                end = Offset(pos.x + r, pos.y),
                strokeWidth = 2.2f * u,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFE879F9),
                start = Offset(pos.x, pos.y - r),
                end = Offset(pos.x, pos.y + r),
                strokeWidth = 2.2f * u,
                cap = StrokeCap.Round
            )
        }
        mood == RomiMood.SLEEPY && animationState != AvatarAnimationState.LISTENING -> {
            val z1 = Offset(headCenter.x + 62f * u, headCenter.y - 44f * u + bob)
            val zSize = 9f * u
            val zPath = Path().apply {
                moveTo(z1.x, z1.y)
                lineTo(z1.x + zSize, z1.y)
                lineTo(z1.x, z1.y + zSize)
                lineTo(z1.x + zSize, z1.y + zSize)
            }
            drawPath(
                zPath,
                color = Color(0xFFC4B5FD).copy(alpha = 0.85f),
                style = Stroke(width = 2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        animationState == AvatarAnimationState.HAPPY ||
            animationState == AvatarAnimationState.SHY ||
            mood == RomiMood.HAPPY ||
            mood == RomiMood.SHY -> {
            val heartPos = Offset(headCenter.x - 66f * u, headCenter.y - 44f * u + bob)
            val heartSize = 7.5f * u
            val heartPath = Path().apply {
                moveTo(heartPos.x, heartPos.y + heartSize * 0.45f)
                cubicTo(
                    heartPos.x - heartSize,
                    heartPos.y - heartSize * 0.25f,
                    heartPos.x - heartSize * 0.5f,
                    heartPos.y - heartSize * 0.9f,
                    heartPos.x,
                    heartPos.y - heartSize * 0.25f
                )
                cubicTo(
                    heartPos.x + heartSize * 0.5f,
                    heartPos.y - heartSize * 0.9f,
                    heartPos.x + heartSize,
                    heartPos.y - heartSize * 0.25f,
                    heartPos.x,
                    heartPos.y + heartSize * 0.45f
                )
                close()
            }
            drawPath(heartPath, color = BlushPink.copy(alpha = 0.85f))
        }
        else -> Unit
    }
}
