package com.michatec.radio.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.util.AttributeSet
import android.view.View

class ShaderEffectView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var shader: RuntimeShader? = null
    private val paint = Paint()
    private var startTime = System.currentTimeMillis()
    private var isAnimating = false

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val r = (color shr 16 and 0xFF) / 255f
            val g = (color shr 8 and 0xFF) / 255f
            val b = (color and 0xFF) / 255f
            shader?.setFloatUniform("iColor", r, g, b, 1.0f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!isAnimating || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val currentTime = (System.currentTimeMillis() - startTime) / 1000f
        
        shader?.let { s ->
            s.setFloatUniform("iResolution", width.toFloat(), height.toFloat())
            s.setFloatUniform("iTime", currentTime)
            paint.shader = s
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            invalidate()
        }
    }
}
