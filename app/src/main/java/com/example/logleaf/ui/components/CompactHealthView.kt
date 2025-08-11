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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourpackage.logleaf.ui.components.UserFontText

/**
 * 既存投稿と統一感のあるコンパクトな健康データ表示
 */
@Composable
fun CompactHealthView(
    postText: String,
    modifier: Modifier = Modifier
) {
    // 投稿テキストから健康データを解析
    val healthData = parseCompactHealthData(postText)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (healthData) {
            is CompactSleepData -> CompactSleepView(healthData)
            is CompactExerciseData -> CompactExerciseView(healthData)
            is CompactDailyData -> CompactDailyView(healthData)
            else -> {
                // フォールバック: 通常のテキスト表示
                UserFontText(
                    text = postText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * コンパクト睡眠データ表示
 */
@Composable
private fun CompactSleepView(sleepData: CompactSleepData) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 睡眠時間表示（アイコン付き）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HealthDataDisplay(
                iconRes = HealthIcons.SLEEP,
                text = "${sleepData.startTime} - ${sleepData.endTime}",
                modifier = Modifier
            )
            UserFontText(
                text = sleepData.totalDuration,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        // コンパクト睡眠バーグラフ
        CompactSleepBarChart(
            deepSleep = sleepData.deepSleep,
            shallowSleep = sleepData.shallowSleep,
            remSleep = sleepData.remSleep,
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        )

        // 簡潔な内訳
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CompactSleepStageItem("深い睡眠", "${sleepData.deepSleep}分", Color(0xFF899EAF))
            CompactSleepStageItem("浅い睡眠", "${sleepData.shallowSleep}分", Color(0xFFBDAAC6))
            CompactSleepStageItem("レム睡眠", "${sleepData.remSleep}分", Color(0xFFF2C5B6))
        }
    }
}

/**
 * コンパクト睡眠バーグラフ
 */
@Composable
private fun CompactSleepBarChart(
    deepSleep: Int,
    shallowSleep: Int,
    remSleep: Int,
    modifier: Modifier = Modifier
) {
    val total = deepSleep + shallowSleep + remSleep
    if (total == 0) return

    // 色定義（より落ち着いた青系統）
    val deepColor = Color(0xFF899EAF)     // くすんだブルーグレー
    val shallowColor = Color(0xFFBDAAC6)  // 優しいラベンダー
    val remColor = Color(0xFFF2C5B6)      // 柔らかいピーチ

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
        }
    }
}

/**
 * コンパクト睡眠ステージ表示
 */
@Composable
private fun CompactSleepStageItem(
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
 * コンパクト運動データ表示
 */
@Composable
private fun CompactExerciseView(exerciseData: CompactExerciseData) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 運動データ表示（アイコン付き）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HealthDataDisplay(
                iconRes = HealthIcons.EXERCISE,
                text = "${exerciseData.type} ${exerciseData.startTime} - ${exerciseData.endTime}",
                modifier = Modifier
            )
            UserFontText(
                text = exerciseData.duration.replace("分", "m"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // 詳細データ
        if (exerciseData.distance != null || exerciseData.calories != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (exerciseData.distance != null) {
                    UserFontText(
                        text = "距離: ${exerciseData.distance}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                if (exerciseData.calories != null) {
                    UserFontText(
                        text = "カロリー: ${exerciseData.calories}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * コンパクトデイリーデータ表示
 */
@Composable
private fun CompactDailyView(dailyData: CompactDailyData) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (dailyData.steps != null) {
            HealthDataDisplay(
                iconRes = HealthIcons.STEPS,
                text = "歩数 ${dailyData.steps}歩",
                modifier = Modifier
            )
        }
        if (dailyData.calories != null) {
            HealthDataDisplay(
                iconRes = HealthIcons.CALORIES,
                text = "消費カロリー ${dailyData.calories}kcal",
                modifier = Modifier
            )
        }
    }
}

@Composable
private fun CompactMetricItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        UserFontText(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        UserFontText(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

// コンパクト用データクラス
sealed class CompactHealthData

data class CompactSleepData(
    val startTime: String,
    val endTime: String,
    val totalDuration: String,
    val deepSleep: Int,
    val shallowSleep: Int,
    val remSleep: Int
) : CompactHealthData()

data class CompactExerciseData(
    val type: String,
    val startTime: String,
    val endTime: String,
    val duration: String,
    val distance: String?,
    val calories: String?
) : CompactHealthData()

data class CompactDailyData(
    val steps: Int?,
    val calories: Int?
) : CompactHealthData()

/**
 * 投稿テキストからコンパクト健康データを解析
 */
private fun parseCompactHealthData(postText: String): CompactHealthData? {
    return when {
        // 睡眠データの解析
        postText.contains("→") && (postText.contains("🛏️") || postText.contains("深い睡眠")) -> {
            parseCompactSleepData(postText)
        }

        // 運動データの解析
        postText.contains("🏃") || postText.contains("ランニング") -> {
            parseCompactExerciseData(postText)
        }

        // デイリーデータの解析
        postText.contains("歩") && postText.contains("kcal") -> {
            parseCompactDailyData(postText)
        }

        else -> null
    }
}

/**
 * コンパクト睡眠データの解析
 */
private fun parseCompactSleepData(postText: String): CompactSleepData? {
    return try {
        // 時間パターン: "🛏️ 21:06 → 05:25 (8h19m)"
        val timePattern = "🛏️\\s*(\\d{2}:\\d{2})\\s*→\\s*(\\d{2}:\\d{2})\\s*\\(([^)]+)\\)".toRegex()
        val timeMatch = timePattern.find(postText) ?: return null

        val (startTime, endTime, duration) = timeMatch.destructured

        // 詳細データパターン
        val deepPattern = "深い睡眠:\\s*(\\d+)分".toRegex()
        val shallowPattern = "浅い睡眠:\\s*(\\d+)分".toRegex()
        val remPattern = "レム睡眠:\\s*(\\d+)分".toRegex()

        val deepSleep = deepPattern.find(postText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val shallowSleep = shallowPattern.find(postText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val remSleep = remPattern.find(postText)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        CompactSleepData(
            startTime = startTime,
            endTime = endTime,
            totalDuration = duration,
            deepSleep = deepSleep,
            shallowSleep = shallowSleep,
            remSleep = remSleep
        )
    } catch (e: Exception) {
        Log.e("CompactHealthView", "睡眠データ解析エラー", e)
        null
    }
}

/**
 * コンパクト運動データの解析
 */
private fun parseCompactExerciseData(postText: String): CompactExerciseData? {
    return try {
        val lines = postText.lines()
        val firstLine = lines.firstOrNull() ?: return null

        // 時刻付きフォーマット用の正規表現
        val exercisePattern = "🏃‍♂️\\s*(\\w+)\\s+(\\d{2}:\\d{2})\\s*-\\s*(\\d{2}:\\d{2})\\s+(\\d+分?)".toRegex()
        val exerciseMatch = exercisePattern.find(firstLine) ?: return null

        val (type, startTime, endTime, duration) = exerciseMatch.destructured

        // 距離とカロリーを抽出
        val distancePattern = "距離:\\s*([\\d.]+km)".toRegex()
        val caloriesPattern = "カロリー:\\s*(\\d+kcal)".toRegex()

        val distance = distancePattern.find(postText)?.groupValues?.get(1)
        val calories = caloriesPattern.find(postText)?.groupValues?.get(1)

        CompactExerciseData(
            type = type,
            startTime = startTime,
            endTime = endTime,
            duration = duration,
            distance = distance,
            calories = calories
        )
    } catch (e: Exception) {
        Log.e("CompactHealthView", "運動データ解析エラー", e)
        null
    }
}

/**
 * コンパクトデイリーデータの解析
 */
private fun parseCompactDailyData(postText: String): CompactDailyData? {
    return try {

        val stepsPattern = "歩数:\\s*([\\d,]+)歩".toRegex()
        val caloriesPattern = "消費カロリー:\\s*([\\d,]+)kcal".toRegex()

        val stepsMatch = stepsPattern.find(postText)
        val caloriesMatch = caloriesPattern.find(postText)

        val steps = stepsMatch?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        val calories = caloriesMatch?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()

        CompactDailyData(
            steps = steps,
            calories = calories
        )
    } catch (e: Exception) {
        Log.e("CompactHealthView", "デイリーデータ解析エラー", e)
        null
    }
}