package com.michatec.radio.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class ShaderEffectView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var shader: RuntimeShader? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var startTime = System.currentTimeMillis()
    private var isAnimating = false
    private var currentColor: Int = Color.BLUE
    private val fallbackRect = RectF()

    private val shaderCode = """
        uniform float2 iResolution;
        uniform float  iTime;
        uniform float4 iColor;
        
        // Signed distance function for a rounded rectangle
        float sdRoundRect(vec2 p, vec2 b, float r) {
            vec2 d = abs(p) - b + vec2(r);
            return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - r;
        }
        
        vec4 main(vec2 fragCoord) {
            vec2 center = 0.5 * iResolution.xy;
            vec2 p = fragCoord - center;
            float minRes = min(iResolution.x, iResolution.y);
            
            float halfSize = minRes * 0.33; 
            float radius = halfSize * 0.3;
            
            float dist = sdRoundRect(p, vec2(halfSize), radius);
            
            float pulse = 0.5 + 0.5 * sin(iTime * 2.5);
            
            float glow = exp(-max(0.0, dist) / (minRes * (0.01 + 0.01 * pulse)));
            
            vec3 col = iColor.rgb * glow * (1.0 + 0.6 * pulse);
            
            float edgeFade = 1.0 - smoothstep(minRes * 0.35, minRes * 0.48, length(p));
            
            return vec4(col, glow * edgeFade * 0.6);
        }
    """.trimIndent()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shader = RuntimeShader(shaderCode)
        }
    }

    fun startAnimation() {
        if (!isAnimating) {
            isAnimating = true
            startTime = System.currentTimeMillis()
            invalidate()
        }
    }

    fun stopAnimation() {
        isAnimating = false
        invalidate()
    }

    fun setColor(color: Int) {
        currentColor = color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val r = (color shr 16 and 0xFF) / 255f
            val g = (color shr 8 and 0xFF) / 255f
            val b = (color and 0xFF) / 255f
            shader?.setFloatUniform("iColor", r, g, b, 1.0f)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!isAnimating) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            drawWithShader(canvas)
        } else {
            drawFallback(canvas)
        }
        invalidate()
    }

    private fun drawWithShader(canvas: Canvas) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        
        val currentTime = (System.currentTimeMillis() - startTime) / 1000f
        shader?.let { s ->
            s.setFloatUniform("iResolution", width.toFloat(), height.toFloat())
            s.setFloatUniform("iTime", currentTime)
            paint.shader = s
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    private fun drawFallback(canvas: Canvas) {
        val currentTime = (System.currentTimeMillis() - startTime) / 1000f
        val pulse = 0.5f + 0.5f * sin(currentTime * 2.5f)
        
        val minRes = minOf(width, height).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        
        val halfSize = minRes * 0.38f
        val radius = halfSize * 0.3f

        paint.shader = null
        paint.style = Paint.Style.FILL
        
        for (i in 1..3) {
            val glowExpand = (i * 10f * (0.5f + 0.5f * pulse))
            val alpha = (40 / i * (0.6f + 0.4f * pulse)).toInt()
            paint.color = currentColor
            paint.alpha = alpha
            
            fallbackRect.set(
                centerX - halfSize - glowExpand,
                centerY - halfSize - glowExpand,
                centerX + halfSize + glowExpand,
                centerY + halfSize + glowExpand
            )
            canvas.drawRoundRect(fallbackRect, radius + glowExpand, radius + glowExpand, paint)
        }

        paint.alpha = 255
        paint.color = currentColor
        fallbackRect.set(
            centerX - halfSize,
            centerY - halfSize,
            centerX + halfSize,
            centerY + halfSize
        )
        canvas.drawRoundRect(fallbackRect, radius, radius, paint)
    }
}
