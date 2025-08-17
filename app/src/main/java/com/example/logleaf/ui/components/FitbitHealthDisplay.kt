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
import androidx.compose.foundation.shape.CircleShape
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
        postText.contains("💤 睡眠記録") || postText.contains("😴 仮眠記録") -> {
            Log.d("FitbitDisplay", "睡眠/仮眠記録マッチ！")
            FitbitSleepDisplay(postText = postText, modifier = modifier)
        }

        postText.contains("🏃 運動記録") -> {
            Log.d("FitbitDisplay", "運動記録マッチ！")
            FitbitExerciseDisplay(postText = postText, modifier = modifier)
        }

        postText.contains("🏃 アクティビティ記録") || postText.contains("📊 今日の健康データ") -> {
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
 * Fitbit運動データ表示（Google Fit完全一致UI）
 */
@Composable
fun FitbitExerciseDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    val exercise = extractValue(postText, "運動:\\s*([^\\n]+)") ?: "運動"
    val startTime = extractValue(postText, "開始時刻:\\s*([^\\n]+)") ?: ""
    val duration = extractValue(postText, "継続時間:\\s*([^\\n]+)") ?: ""
    val calories = extractValue(postText, "消費カロリー:\\s*(\\d+)kcal")?.toIntOrNull() ?: 0

    val formattedDuration = duration.replace("分", "m")

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        // 「運動 16:40 - 18:39    118m」
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HealthDataDisplay(
                iconRes = HealthIcons.EXERCISE,
                text = "$exercise 16:40 - 18:39", // ← 時間帯追加
                modifier = Modifier
            )
            UserFontText(
                text = formattedDuration,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // 「距離: 5.1km  カロリー: 478kcal」
        UserFontText(
            text = "距離: 5.1km  カロリー: ${calories}kcal",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
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
 * Fitbit睡眠データ表示（Google Fit完全一致UI + 色分けバーチャート）
 */
@Composable
fun FitbitSleepDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    // データ解析
    val startTime = extractValue(postText, "(\\d{2}:\\d{2})\\s*→") ?: "不明"
    val endTime = extractValue(postText, "→\\s*(\\d{2}:\\d{2})") ?: "不明"
    val durationText = extractValue(postText, "\\(([^)]+)\\)") ?: "不明"

    // 時間表記を「7時間45分」→「7h45m」に変換
    val formattedDuration = durationText
        .replace("時間", "h")
        .replace("分", "m")

    // 詳細データ解析（バーチャート用）
    val deepSleep = extractValue(postText, "深い睡眠:\\s*(\\d+)分")?.toIntOrNull() ?: 0
    val lightSleep = extractValue(postText, "浅い睡眠:\\s*(\\d+)分")?.toIntOrNull() ?: 0
    val remSleep = extractValue(postText, "レム睡眠:\\s*(\\d+)分")?.toIntOrNull() ?: 0
    val awake = extractValue(postText, "覚醒:\\s*(\\d+)分")?.toIntOrNull() ?: 0  // awakeSleep → awake

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        // Google Fitと同じ基本レイアウト
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
                text = formattedDuration, // 「7h45m」形式
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // 詳細データがある場合は色分けバーチャートも表示
        if (deepSleep > 0 || lightSleep > 0 || remSleep > 0 || awake > 0) {
            // 覚醒対応の色分けバーチャート
            FitbitSleepBarChart(
                deepSleep = deepSleep,
                shallowSleep = lightSleep,
                remSleep = remSleep,
                awakeSleep = awake,  // awake を渡す
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            )

// 睡眠ステージ内訳（中央揃えで一行）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val stages = mutableListOf<@Composable () -> Unit>()

                if (deepSleep > 0) {
                    stages.add {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF899EAF), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            UserFontText(
                                text = "深い睡眠${deepSleep}分",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                if (lightSleep > 0) {
                    stages.add {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFFBDAAC6), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            UserFontText(
                                text = "浅い睡眠${lightSleep}分",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                if (remSleep > 0) {
                    stages.add {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFFF2C5B6), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            UserFontText(
                                text = "レム睡眠${remSleep}分",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                if (awake > 0) {
                    stages.add {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFFE8D8A0), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            UserFontText(
                                text = "覚醒${awake}分",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // 間にスペースを入れて並べる
                stages.forEachIndexed { index, stage ->
                    stage()
                    if (index < stages.size - 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
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
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // カラーインジケーター
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )

        // ラベルと時間
        UserFontText(
            text = "$label $duration",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
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

    // ログビュー用：「歩数」「消費カロリー」ラベル付き
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (steps > 0) {
            HealthDataDisplay(
                iconRes = HealthIcons.STEPS,
                text = "歩数 ${steps.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")}歩",
                modifier = Modifier
            )
        }
        if (calories > 0) {
            HealthDataDisplay(
                iconRes = HealthIcons.CALORIES,
                text = "消費カロリー ${calories.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")}kcal",
                modifier = Modifier
            )
        }
    }
}

/**
 * 正規表現でテキストから値を抽出
 */
private fun extractValue(text: String, pattern: String): String? {
    return pattern.toRegex().find(text)?.groupValues?.get(1)
}

