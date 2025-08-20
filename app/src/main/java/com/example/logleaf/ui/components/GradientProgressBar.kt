package com.example.logleaf.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun GradientProgressBar(
    progress: Float,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val deepColor = Color(0xFF899EAF)
    val shallowColor = Color(0xFFBDAAC6)
    val remColor = Color(0xFFF2C5B6)
    val awakeColor = Color(0xFFE8D8A0)

    // エラー時の点滅制御
    var isFlashing by remember { mutableStateOf(false) }

    LaunchedEffect(isError) {
        if (isError) {
            // 2回点滅
            repeat(2) {
                isFlashing = true
                delay(200)
                isFlashing = false
                delay(200)
            }
        }
    }

    Canvas(modifier = modifier.height(2.dp)) {
        val width = size.width
        val height = size.height

        // 背景（薄いグレー）
        drawRect(
            color = Color.Gray.copy(alpha = 0.2f),
            size = androidx.compose.ui.geometry.Size(width, height)
        )

        // プログレス部分
        val progressColor = if (isError && isFlashing) {
            Color.Red
        } else {
            // 通常のグラデーション
            Brush.horizontalGradient(
                colors = listOf(deepColor, shallowColor, remColor, awakeColor),
                startX = 0f,
                endX = width
            )
        }

        if (isError && isFlashing) {
            // エラー時は全幅で赤色
            drawRect(
                color = Color.Red,
                size = androidx.compose.ui.geometry.Size(width, height)
            )
        } else {
            // 通常時はグラデーション
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(deepColor, shallowColor, remColor, awakeColor),
                    startX = 0f,
                    endX = width
                ),
                size = androidx.compose.ui.geometry.Size(width * progress.coerceIn(0f, 1f), height)
            )
        }
    }
}