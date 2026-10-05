package com.sundown.player.ui.theme

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/**
 * High-Performance AGSL Glossy Shader.
 * This runs directly on the GPU to calculate the curved specular highlight.
 */
private const val GLOSS_SHADER_SRC = """
    uniform float2 size;
    uniform float intensity;
    
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / size;
        
        // Curved highlight math
        float curve = 1.0 - pow(abs(uv.x - 0.5) * 2.0, 2.0);
        float highlight = smoothstep(0.45, 0.0, uv.y - (curve * 0.1));
        
        return half4(1.0, 1.0, 1.0, highlight * intensity);
    }
"""

fun Modifier.gpuGloss(intensity: Float = 0.25f): Modifier = this.drawWithCache {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = RuntimeShader(GLOSS_SHADER_SRC)
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("intensity", intensity)
        val brush = ShaderBrush(shader)
        onDrawWithContent {
            drawContent()
            drawRect(brush)
        }
    } else {
        // High-performance CPU fallback using vertical gradient
        val brush = Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = intensity),
            0.5f to Color.Transparent
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush)
        }
    }
}
