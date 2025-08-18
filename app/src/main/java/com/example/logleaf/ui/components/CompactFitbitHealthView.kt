package com.example.logleaf.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourpackage.logleaf.ui.components.UserFontText

/**
 * Fitbit専用のコンパクト健康データ表示（実際のZepp対応版）
 */
@Composable
fun CompactFitbitHealthView(
    postText: String,
    modifier: Modifier = Modifier
) {
    Log.d("CompactFitbit", "=== CompactFitbitHealthView呼ばれた！ ===")
    Log.e("CompactFitbit", "postText: ${postText.take(100)}")

    when {
        // 睡眠データ：Fitbit(💤)とZepp(🛏️)両方に対応
        postText.contains("💤 睡眠記録") || postText.contains("😴 仮眠記録") || postText.contains("🛏️") -> {
            Log.d("CompactFitbit", "睡眠/仮眠記録マッチ！")
            CompactFitbitSleepDisplay(postText = postText, modifier = modifier)
        }

        // 運動データ：Fitbit(🏃 運動記録)とZepp(🏃‍♂️)両方に対応
        postText.contains("🏃 運動記録") || postText.contains("🏃‍♂️") -> {
            Log.d("CompactFitbit", "運動記録マッチ！")
            CompactFitbitExerciseDisplay(postText = postText, modifier = modifier)
        }

        // アクティビティデータ：Fitbit(🏃 アクティビティ記録)とZepp(📊 今日の健康データ)両方に対応
        postText.contains("🏃 アクティビティ記録") || postText.contains("📊 今日の健康データ") -> {
            Log.d("CompactFitbit", "アクティビティマッチ！")
            CompactFitbitActivityDisplay(postText = postText, modifier = modifier)
        }

        else -> {
            Log.d("CompactFitbit", "else節：未対応の形式")
            UserFontText(
                text = postText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = modifier
            )
        }
    }
}

/**
 * コンパクト睡眠データ表示（Zepp対応版）
 */
@Composable
private fun CompactFitbitSleepDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    // FitbitとZeppの時刻抽出を統一
    val startTime = extractTimeFromSleepData(postText, isStartTime = true)
    val endTime = extractTimeFromSleepData(postText, isStartTime = false)
    val durationText = extractDurationFromSleepData(postText)

    // 時間表記を統一
    val formattedDuration = formatDuration(durationText)

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
 * コンパクト運動データ表示（実際のZepp対応版）
 */
@Composable
private fun CompactFitbitExerciseDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    // データ抽出
    val exerciseData = parseExerciseData(postText)

    // Google Fitと同じコンパクトレイアウト
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HealthDataDisplay(
            iconRes = HealthIcons.EXERCISE,
            text = "${exerciseData.type}${if (exerciseData.distance.isNotEmpty()) " ${exerciseData.distance}" else ""}",
            modifier = Modifier
        )
        UserFontText(
            text = exerciseData.duration,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * コンパクトアクティビティデータ表示（実際のZepp対応版）
 */
@Composable
private fun CompactFitbitActivityDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    // データ抽出
    val activityData = parseActivityData(postText)

    // カレンダー用：歩数とカロリー両方表示
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (activityData.steps > 0) {
            StepsDataDisplay(steps = activityData.steps)
        }
        if (activityData.calories > 0) {
            CaloriesDataDisplay(calories = activityData.calories)
        }
    }
}

// === データ解析関数（FitbitHealthDisplay.ktの関数を使用） ===
private fun parseExerciseData(postText: String): FitbitExerciseData {
    return when {
        // Fitbit形式：🏃 運動記録
        postText.contains("🏃 運動記録") -> {
            FitbitExerciseData(
                type = extractValue(postText, "運動:\\s*([^\\n]+)") ?: "運動",
                timeRange = extractValue(postText, "開始時刻:\\s*([^\\n]+)") ?: "",
                duration = formatDuration(extractValue(postText, "継続時間:\\s*([^\\n]+)") ?: ""),
                distance = extractValue(postText, "距離:\\s*([\\d.]+km)") ?: "",
                calories = extractValue(postText, "消費カロリー:\\s*(\\d+)kcal")?.toIntOrNull() ?: 0
            )
        }

        // Zepp形式：🏃‍♂️ ランニング 13:27 - 14:45 78分
        postText.contains("🏃‍♂️") -> {
            val fullLine = postText.lines().find { it.contains("🏃‍♂️") } ?: ""
            // "🏃‍♂️ ランニング 13:27 - 14:45 78分" のパターン
            val pattern = "🏃‍♂️\\s+(\\S+)\\s+(\\d{2}:\\d{2})\\s*-\\s*(\\d{2}:\\d{2})\\s+(\\d+)分".toRegex()
            val match = pattern.find(fullLine)

            val type = match?.groupValues?.get(1) ?: "運動"
            val startTime = match?.groupValues?.get(2) ?: ""
            val endTime = match?.groupValues?.get(3) ?: ""
            val minutes = match?.groupValues?.get(4) ?: "0"

            FitbitExerciseData(
                type = type,
                timeRange = if (startTime.isNotEmpty() && endTime.isNotEmpty()) "$startTime - $endTime" else "",
                duration = "${minutes}m",
                distance = extractValue(postText, "距離:\\s*([\\d.]+km)") ?: "",
                calories = extractValue(postText, "カロリー:\\s*(\\d+)kcal")?.toIntOrNull() ?: 0
            )
        }

        else -> FitbitExerciseData("運動", "", "", "", 0)
    }
}

private fun parseActivityData(postText: String): FitbitActivityData {
    val steps = extractValue(postText, "歩数:\\s*([\\d,]+)歩")?.replace(",", "")?.toIntOrNull() ?: 0
    val calories = extractValue(postText, "消費カロリー:\\s*(\\d+)kcal")?.toIntOrNull() ?: 0

    return FitbitActivityData(steps, calories)
}

// ヘルパー関数（FitbitHealthDisplay.ktと共通）
private fun extractTimeFromSleepData(postText: String, isStartTime: Boolean): String {
    return if (isStartTime) {
        extractValue(postText, "(\\d{2}:\\d{2})\\s*[→-]") ?: "不明"
    } else {
        extractValue(postText, "[→-]\\s*(\\d{2}:\\d{2})") ?: "不明"
    }
}

private fun extractDurationFromSleepData(postText: String): String {
    return extractValue(postText, "\\(([^)]+)\\)") ?: "不明"
}

private fun formatDuration(durationText: String): String {
    return if (durationText.contains("時間")) {
        durationText.replace("時間", "h").replace("分", "m")
    } else {
        durationText
    }
}

/**
 * 正規表現でテキストから値を抽出
 */
private fun extractValue(text: String, pattern: String): String? {
    return pattern.toRegex().find(text)?.groupValues?.get(1)
}