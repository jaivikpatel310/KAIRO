package com.example.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/**
 * Concentric pulsing orb system overlay for Kairo.
 * Adheres strictly to the visual spec:
 * Background: #050F0B (near-black)
 * Primary accent: #10B981 (emerald green)
 * Text: #ECFDF5 (mint)
 * Muted accent: #4B9E7E
 * Voice-only, non-interactive visual indicator.
 */
class OrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Palette
    private val colorBg = Color.parseColor("#E6050F0B") // Semi-translucent near-black backdrop
    private val colorPrimary = Color.parseColor("#10B981")
    private val colorMuted = Color.parseColor("#4B9E7E")
    private val colorText = Color.parseColor("#ECFDF5")

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorBg
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorText
        textAlign = Paint.Align.CENTER
        textSize = 42f
        isFakeBoldText = true
        letterSpacing = 0.08f
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorMuted
        textAlign = Paint.Align.CENTER
        textSize = 30f
        letterSpacing = 0.05f
    }

    private var pulseProgress = 0f
    private var pulseAnimator: ValueAnimator? = null
    private var rmsLevel = 0f
    private var statusText = "Listening..."
    private var secondaryText = "KAIRO"

    init {
        startAnimation()
    }

    private fun startAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                pulseProgress = animator.animatedValue as Float
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    fun setStatus(text: String, subText: String = "KAIRO") {
        statusText = text
        secondaryText = subText
        postInvalidateOnAnimation()
    }

    fun setRms(rmsDb: Float) {
        // Normalize rms (-2 to 10 dB typically)
        val normalized = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
        rmsLevel = rmsLevel * 0.7f + normalized * 0.3f
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f - 40f
        val maxRadius = min(width, height) * 0.35f
        val baseCoreRadius = 60f + (rmsLevel * 25f)

        // Draw soft centered backdrop circle
        val bgRadius = maxRadius * 1.35f
        val bgGradient = RadialGradient(
            cx, cy, bgRadius,
            intArrayOf(Color.parseColor("#F5050F0B"), Color.parseColor("#D9050F0B"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        bgPaint.shader = bgGradient
        canvas.drawCircle(cx, cy, bgRadius, bgPaint)

        // Concentric pulsing rings (3 staggered waves)
        val ringCount = 3
        for (i in 0 until ringCount) {
            val offset = (pulseProgress + (i.toFloat() / ringCount)) % 1f
            val ringRadius = baseCoreRadius + (maxRadius - baseCoreRadius) * offset
            val alpha = ((1f - offset) * 200).toInt().coerceIn(0, 255)

            ringPaint.color = if (i % 2 == 0) colorPrimary else colorMuted
            ringPaint.alpha = alpha
            ringPaint.strokeWidth = 3.5f + (1f - offset) * 4f

            canvas.drawCircle(cx, cy, ringRadius, ringPaint)
        }

        // Inner glowing core
        val coreGradient = RadialGradient(
            cx, cy, baseCoreRadius * 1.4f,
            intArrayOf(Color.WHITE, colorPrimary, colorMuted, Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 0.85f, 1f),
            Shader.TileMode.CLAMP
        )
        corePaint.shader = coreGradient
        canvas.drawCircle(cx, cy, baseCoreRadius * 1.4f, corePaint)

        // Solid central pulse sphere
        corePaint.shader = null
        corePaint.color = colorPrimary
        canvas.drawCircle(cx, cy, baseCoreRadius * 0.75f, corePaint)

        // Status typography
        val textY = cy + maxRadius + 70f
        canvas.drawText(statusText, cx, textY, textPaint)
        canvas.drawText(secondaryText.uppercase(), cx, textY + 44f, subTextPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (pulseAnimator == null || !pulseAnimator!!.isRunning) {
            startAnimation()
        }
    }
}
