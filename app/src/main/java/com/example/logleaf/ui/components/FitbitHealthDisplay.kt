package com.example.logleaf.ui.components

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourpackage.logleaf.ui.components.UserFontText

// === データクラス ===
data class FitbitExerciseData(
    val type: String,
    val timeRange: String,
    val duration: String,
    val distance: String,
    val calories: Int
)

data class FitbitActivityData(
    val steps: Int,
    val calories: Int
)

/**
 * Fitbit専用の健康データ表示コンポーネント（実際のZepp対応版）
 */
@Composable
fun FitbitHealthDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    Log.d("FitbitDisplay", "=== FitbitHealthDisplay呼ばれた！ ===")
    Log.d("FitbitDisplay", "postText: ${postText.take(100)}")

    when {
        // 睡眠データ：Fitbit(💤)とZepp(🛏️)両方に対応
        postText.contains("💤 睡眠記録") || postText.contains("😴 仮眠記録") || postText.contains("🛏️") -> {
            Log.d("FitbitDisplay", "睡眠/仮眠記録マッチ！")
            FitbitSleepDisplay(postText = postText, modifier = modifier)
        }

        // 運動データ：Fitbit(🏃 運動記録)とZepp(🏃‍♂️)両方に対応
        postText.contains("🏃 運動記録") || postText.contains("🏃‍♂️") -> {
            Log.d("FitbitDisplay", "運動記録マッチ！")
            FitbitExerciseDisplay(postText = postText, modifier = modifier)
        }

        // アクティビティデータ：Fitbit(🏃 アクティビティ記録)とZepp(📊 今日の健康データ)両方に対応
        postText.contains("🏃 アクティビティ記録") || postText.contains("📊 今日の健康データ") -> {
            Log.d("FitbitDisplay", "アクティビティマッチ！")
            FitbitActivityDisplay(postText = postText, modifier = modifier)
        }

        else -> {
            Log.d("FitbitDisplay", "else節：未対応の健康データ形式")
            // 未対応の形式でもテキスト表示する
            UserFontText(
                text = postText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = modifier
            )
        }
    }
}

/**
 * Fitbit運動データ表示（実際のZepp対応版）
 */
@Composable
fun FitbitExerciseDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    // データ抽出
    val exerciseData = parseExerciseData(postText)

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        // メイン表示：「ランニング 13:27 - 14:45    78m」
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HealthDataDisplay(
                iconRes = HealthIcons.EXERCISE,
                text = "${exerciseData.type} ${exerciseData.timeRange}${if (exerciseData.distance.isNotEmpty()) " ${exerciseData.distance}" else ""}",
                modifier = Modifier
            )
            UserFontText(
                text = exerciseData.duration,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // 詳細情報：「カロリー: 420kcal」
        if (exerciseData.calories > 0) {
            UserFontText(
                text = "カロリー: ${exerciseData.calories}kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Fitbitアクティビティデータ表示（実際のZepp対応版）
 */
@Composable
fun FitbitActivityDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    // データ抽出
    val activityData = parseActivityData(postText)

    // ログビュー用：「歩数」「消費カロリー」ラベル付き
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (activityData.steps > 0) {
            HealthDataDisplay(
                iconRes = HealthIcons.STEPS,
                text = "歩数 ${formatNumber(activityData.steps)}歩",
                modifier = Modifier
            )
        }
        if (activityData.calories > 0) {
            HealthDataDisplay(
                iconRes = HealthIcons.CALORIES,
                text = "消費カロリー ${formatNumber(activityData.calories)}kcal",
                modifier = Modifier
            )
        }
    }
}

/**
 * Fitbit睡眠データ表示（Zepp対応版）
 */
@Composable
fun FitbitSleepDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    // FitbitとZeppの時刻抽出を統一
    val startTime = extractTimeFromSleepData(postText, isStartTime = true)
    val endTime = extractTimeFromSleepData(postText, isStartTime = false)
    val durationText = extractDurationFromSleepData(postText)

    // 時間表記を統一（「7時間45分」→「7h45m」、既に「7h39m」の場合はそのまま）
    val formattedDuration = formatDuration(durationText)

    // 詳細データ解析（FitbitとZepp両方に対応）
    val deepSleep = extractValue(postText, "深い睡眠:\\s*(\\d+)分")?.toIntOrNull() ?: 0
    val lightSleep = extractValue(postText, "浅い睡眠:\\s*(\\d+)分")?.toIntOrNull() ?: 0
    val remSleep = extractValue(postText, "レム睡眠:\\s*(\\d+)分")?.toIntOrNull() ?: 0
    val awake = extractValue(postText, "覚醒:\\s*(\\d+)分")?.toIntOrNull() ?: 0
    val efficiency = extractValue(postText, "睡眠効率:\\s*(\\d+)%")?.toIntOrNull() ?: 0

    // UI表示（既存のFitbitUIと同じ）
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        // メイン表示：「睡眠 22:30 - 06:45   7h45m」
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HealthDataDisplay(
                iconRes = HealthIcons.SLEEP,
                text = "睡眠 $startTime - $endTime",
                modifier = Modifier
            )
            UserFontText(
                text = formattedDuration,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // バーチャート（覚醒があるかどうかで表示調整）
        if (deepSleep > 0 || lightSleep > 0 || remSleep > 0) {
            FitbitSleepBarChart(
                deepSleep = deepSleep,
                shallowSleep = lightSleep,
                remSleep = remSleep,
                awakeSleep = awake, // Zeppの場合は0
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
            )
        }

        // 睡眠ステージ詳細（覚醒がない場合は表示しない）
        if (deepSleep > 0 || lightSleep > 0 || remSleep > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val stages = mutableListOf<@Composable () -> Unit>()

                if (deepSleep > 0) {
                    stages.add {
                        SleepStageItem(
                            label = "深い睡眠",
                            duration = "${deepSleep}分",
                            color = Color(0xFF899EAF)
                        )
                    }
                }

                if (lightSleep > 0) {
                    stages.add {
                        SleepStageItem(
                            label = "浅い睡眠",
                            duration = "${lightSleep}分",
                            color = Color(0xFFBDAAC6)
                        )
                    }
                }

                if (remSleep > 0) {
                    stages.add {
                        SleepStageItem(
                            label = "レム睡眠",
                            duration = "${remSleep}分",
                            color = Color(0xFFF2C5B6)
                        )
                    }
                }

                // 覚醒は0より大きい場合のみ表示（Zeppにはない）
                if (awake > 0) {
                    stages.add {
                        SleepStageItem(
                            label = "覚醒",
                            duration = "${awake}分",
                            color = Color(0xFFE8D8A0)
                        )
                    }
                }

                stages.forEachIndexed { index, stage ->
                    stage()
                    if (index < stages.size - 1) {
                        Spacer(modifier = Modifier.width(0.dp))
                    }
                }
            }
        }
    }
}

/**
 * 覚醒対応の睡眠バーチャート
 */
@Composable
private fun FitbitSleepBarChart(
    deepSleep: Int,
    shallowSleep: Int,
    remSleep: Int,
    awakeSleep: Int,
    modifier: Modifier = Modifier
) {
    val total = deepSleep + shallowSleep + remSleep + awakeSleep
    if (total == 0) return

    // 色定義
    val deepColor = Color(0xFF899EAF)     // 深い睡眠
    val shallowColor = Color(0xFFBDAAC6)  // 浅い睡眠
    val remColor = Color(0xFFF2C5B6)      // レム睡眠
    val awakeColor = Color(0xFFE8D8A0)    // 覚醒

    Canvas(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        val width = size.width
        val height = size.height

        // 各ステージの幅計算
        val deepWidth = (deepSleep.toFloat() / total) * width
        val shallowWidth = (shallowSleep.toFloat() / total) * width
        val remWidth = (remSleep.toFloat() / total) * width
        val awakeWidth = (awakeSleep.toFloat() / total) * width

        var currentX = 0f

        // 深い睡眠
        if (deepSleep > 0) {
            drawRect(
                color = deepColor,
                topLeft = androidx.compose.ui.geometry.Offset(currentX, 0f),
                size = androidx.compose.ui.geometry.Size(deepWidth, height)
            )
            currentX += deepWidth
        }

        // 浅い睡眠
        if (shallowSleep > 0) {
            drawRect(
                color = shallowColor,
                topLeft = androidx.compose.ui.geometry.Offset(currentX, 0f),
                size = androidx.compose.ui.geometry.Size(shallowWidth, height)
            )
            currentX += shallowWidth
        }

        // レム睡眠
        if (remSleep > 0) {
            drawRect(
                color = remColor,
                topLeft = androidx.compose.ui.geometry.Offset(currentX, 0f),
                size = androidx.compose.ui.geometry.Size(remWidth, height)
            )
            currentX += remWidth
        }

        // 覚醒
        if (awakeSleep > 0) {
            drawRect(
                color = awakeColor,
                topLeft = androidx.compose.ui.geometry.Offset(currentX, 0f),
                size = androidx.compose.ui.geometry.Size(awakeWidth, height)
            )
        }
    }
}

/**
 * 睡眠ステージアイテム表示
 */
@Composable
private fun SleepStageItem(
    label: String,
    duration: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp) // 最小スペース
    ) {
        Box(
            modifier = Modifier
                .size(8.dp) // サイズ縮小
                .background(color, RoundedCornerShape(3.dp))
        )
        UserFontText(
            text = when(label) {
                "深い睡眠" -> "深い睡眠 $duration"
                "浅い睡眠" -> "浅い睡眠 $duration"
                "レム睡眠" -> "レム睡眠 $duration"
                "覚醒" -> "覚醒 $duration"
                else -> "$label $duration"
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

// === データ解析関数 ===
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

private fun formatDuration(duration: String): String {
    return when {
        duration.contains("時間") -> duration.replace("時間", "h").replace("分", "m")
        duration.contains("分") -> duration.replace("分", "m")
        else -> duration
    }
}

private fun formatNumber(number: Int): String {
    return number.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")
}

// ヘルパー関数群
private fun extractTimeFromSleepData(postText: String, isStartTime: Boolean): String {
    return if (isStartTime) {
        extractValue(postText, "(\\d{2}:\\d{2})\\s*[→-]") ?: "不明"
    } else {
        extractValue(postText, "[→-]\\s*(\\d{2}:\\d{2})") ?: "不明"
    }
}

private fun extractDurationFromSleepData(postText: String): String {
    // Fitbit形式: (8時間15分) または Zepp形式: (7h39m) を抽出
    return extractValue(postText, "\\(([^)]+)\\)") ?: "不明"
}

/**
 * 正規表現でテキストから値を抽出
 */
private fun extractValue(text: String, pattern: String): String? {
    return pattern.toRegex().find(text)?.groupValues?.get(1)
}