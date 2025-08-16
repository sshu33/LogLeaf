package com.example.logleaf.ui.components

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourpackage.logleaf.ui.components.UserFontText

/**
 * Fitbit専用の健康データ表示コンポーネント（GoogleFitと同じUI）
 */
@Composable
fun FitbitHealthDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    Log.d("FitbitDisplay", "Fitbit投稿テキスト: $postText")

    when {
        // Fitbit睡眠データ
        postText.contains("💤 睡眠記録") -> {
            FitbitSleepDisplay(postText = postText, modifier = modifier)
        }

        // Fitbitアクティビティデータ
        postText.contains("🏃 アクティビティ記録") -> {
            FitbitActivityDisplay(postText = postText, modifier = modifier)
        }

        // フォールバック
        else -> {
            UserFontText(
                text = postText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = modifier
            )
        }
    }
}

/**
 * Fitbit睡眠データ表示（GoogleFitと完全に同じUI）
 */
@Composable
private fun FitbitSleepDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    // データ解析
    val duration = extractValue(postText, "睡眠時間:\\s*([^\\n]+)") ?: "不明"
    val deepSleep = extractValue(postText, "深い睡眠:\\s*(\\d+)分")?.toIntOrNull() ?: 0
    val lightSleep = extractValue(postText, "浅い睡眠:\\s*(\\d+)分")?.toIntOrNull() ?: 0
    val remSleep = extractValue(postText, "レム睡眠:\\s*(\\d+)分")?.toIntOrNull() ?: 0

    // 既存のSleepDataDisplayコンポーネントを使用（GoogleFitと同じ）
    SleepDataDisplay(
        startTime = "就寝",
        endTime = "起床",
        duration = duration,
        iconRes = HealthIcons.SLEEP,
        modifier = modifier
    )
}

/**
 * Fitbitアクティビティデータ表示（GoogleFitと同じスタイル）
 */
@Composable
private fun FitbitActivityDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    val steps = extractValue(postText, "歩数:\\s*([\\d,]+)歩")?.replace(",", "")?.toIntOrNull() ?: 0
    val calories = extractValue(postText, "消費カロリー:\\s*(\\d+)kcal")?.toIntOrNull() ?: 0

    // 既存のStepsDataDisplayとCaloriesDataDisplayを使用（GoogleFitと同じ）
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (steps > 0) {
            StepsDataDisplay(steps = steps)
        }
        if (calories > 0) {
            CaloriesDataDisplay(calories = calories)
        }
    }
}

/**
 * 正規表現でテキストから値を抽出
 */
private fun extractValue(text: String, pattern: String): String? {
    return pattern.toRegex().find(text)?.groupValues?.get(1)
}