package com.example.logleaf.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourpackage.logleaf.ui.components.UserFontText

/**
 * Fitbit専用のコンパクト健康データ表示（カレンダー用）
 * Google FitのCompactHealthViewと完全同じUI
 */
@Composable
fun CompactFitbitHealthView(
    postText: String,
    modifier: Modifier = Modifier
) {
    Log.d("CompactFitbit", "=== CompactFitbitHealthView呼ばれた！ ===")
    Log.d("CompactFitbit", "postText: ${postText.take(100)}")

    when {
        postText.contains("💤 睡眠記録") -> {
            Log.d("CompactFitbit", "睡眠記録マッチ！")
            CompactFitbitSleepDisplay(postText = postText, modifier = modifier)
        }

        postText.contains("🏃 運動記録") -> {
            Log.d("CompactFitbit", "運動記録マッチ！")
            CompactFitbitExerciseDisplay(postText = postText, modifier = modifier)
        }

        postText.contains("🏃 アクティビティ記録") || postText.contains("📊 今日の健康データ") -> {
            Log.d("CompactFitbit", "アクティビティマッチ！")
            CompactFitbitActivityDisplay(postText = postText, modifier = modifier)
        }

        else -> {
            Log.d("CompactFitbit", "else節")
            UserFontText(
                text = postText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = modifier
            )
        }
    }
}

/**
 * コンパクト睡眠データ表示（Google Fitと同じ）
 */
@Composable
private fun CompactFitbitSleepDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    val startTime = extractValue(postText, "(\\d{2}:\\d{2})\\s*→") ?: "不明"
    val endTime = extractValue(postText, "→\\s*(\\d{2}:\\d{2})") ?: "不明"
    val durationText = extractValue(postText, "\\(([^)]+)\\)") ?: "不明"

    // 時間表記を「7時間45分」→「7h45m」に変換
    val formattedDuration = durationText
        .replace("時間", "h")
        .replace("分", "m")

    // Google Fitと同じコンパクトレイアウト
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HealthDataDisplay(
            iconRes = HealthIcons.SLEEP,
            text = "$startTime - $endTime",
            modifier = Modifier
        )
        UserFontText(
            text = formattedDuration,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * コンパクト運動データ表示（Google Fitと同じ）
 */
@Composable
private fun CompactFitbitExerciseDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    val exercise = extractValue(postText, "運動:\\s*([^\\n]+)") ?: "運動"
    val duration = extractValue(postText, "継続時間:\\s*([^\\n]+)") ?: ""

    // 時間表記を「45分」→「45m」に変換
    val formattedDuration = duration.replace("分", "m")

    // Google Fitと同じコンパクトレイアウト
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HealthDataDisplay(
            iconRes = HealthIcons.EXERCISE,
            text = "$exercise 5.1km", // 距離を含める
            modifier = Modifier
        )
        UserFontText(
            text = formattedDuration, // 右端に「45m」
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * コンパクトアクティビティデータ表示（Google Fitと同じ）
 */
@Composable
private fun CompactFitbitActivityDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    val steps = extractValue(postText, "歩数:\\s*([\\d,]+)歩")?.replace(",", "")?.toIntOrNull() ?: 0
    val calories = extractValue(postText, "消費カロリー:\\s*(\\d+)kcal")?.toIntOrNull() ?: 0

    // カレンダー用：歩数とカロリー両方表示
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
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