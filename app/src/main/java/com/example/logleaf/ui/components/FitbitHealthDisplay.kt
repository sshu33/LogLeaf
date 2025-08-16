package com.example.logleaf.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yourpackage.logleaf.ui.components.UserFontText
import io.ktor.websocket.Frame

/**
 * Fitbit専用の健康データ表示コンポーネント（GoogleFitと同じUI）
 */
@Composable
fun FitbitHealthDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    Log.d("FitbitDisplay", "=== FitbitHealthDisplay呼ばれた！ ===")
    Log.d("FitbitDisplay", "postText: ${postText.take(100)}")

    when {
        postText.contains("💤 睡眠記録") -> {
            Log.d("FitbitDisplay", "睡眠記録マッチ！")
            FitbitSleepDisplay(postText = postText, modifier = modifier)
        }

        // ★ 修正：判定条件を変更
        postText.contains("📊 今日の健康データ") -> {  // 🏃 から 📊 に変更
            Log.d("FitbitDisplay", "アクティビティマッチ！")
            FitbitActivityDisplay(postText = postText, modifier = modifier)
        }

        else -> {
            Log.d("FitbitDisplay", "else節")
            Text("Fitbit表示エラー", color = Color.Red)
        }
    }
}

/**
 * Fitbit睡眠データ表示（GoogleFitと完全に同じUI）
 */
@Composable
fun FitbitSleepDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    // データ解析
    val startTime = extractValue(postText, "(\\d{2}:\\d{2})\\s*→") ?: "不明"
    val endTime = extractValue(postText, "→\\s*(\\d{2}:\\d{2})") ?: "不明"
    val duration = extractValue(postText, "\\(([^)]+)\\)") ?: "不明"

    // 既存のSleepDataDisplayコンポーネントを使用（GoogleFitと同じ）
    SleepDataDisplay(
        startTime = startTime,
        endTime = endTime,
        duration = duration,
        iconRes = HealthIcons.SLEEP,
        modifier = modifier
    )
}

/**
 * Fitbitアクティビティデータ表示（GoogleFitと同じスタイル）
 */
@Composable
fun FitbitActivityDisplay(
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