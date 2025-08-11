package com.example.logleaf.ui.components

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourpackage.logleaf.ui.components.UserFontText
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 健康データの詳細表示コンポーネント
 */
@Composable
fun HealthDetailView(
    postText: String,
    date: LocalDate,
    onGoogleFitClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 投稿テキストから健康データを解析
    val healthData = parseHealthData(postText)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            when (healthData) {
                is SleepData -> SleepDetailView(
                    sleepData = healthData,
                    date = date,
                    onGoogleFitClick = onGoogleFitClick
                )
                is ExerciseData -> ExerciseDetailView(
                    exerciseData = healthData,
                    onGoogleFitClick = onGoogleFitClick
                )
                is DailyData -> DailyDetailView(
                    dailyData = healthData,
                    onGoogleFitClick = onGoogleFitClick
                )
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
}

/**
 * 睡眠データの詳細表示
 */
@Composable
private fun SleepDetailView(
    sleepData: SleepData,
    date: LocalDate,
    onGoogleFitClick: () -> Unit
) {
    Column {
        // ヘッダー
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserFontText(
                text = "睡眠データ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Google Fitリンクボタン
            TextButton(onClick = onGoogleFitClick) {
                UserFontText(
                    text = "Google Fit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 睡眠時間表示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            UserFontText(
                text = "${sleepData.startTime} - ${sleepData.endTime}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            UserFontText(
                text = sleepData.totalDuration,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 睡眠バーグラフ
        SleepBarChart(
            deepSleep = sleepData.deepSleep,
            shallowSleep = sleepData.shallowSleep,
            remSleep = sleepData.remSleep,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 睡眠ステージ内訳
        SleepStageBreakdown(sleepData = sleepData)
    }
}

/**
 * 睡眠バーグラフ
 */
@Composable
private fun SleepBarChart(
    deepSleep: Int,
    shallowSleep: Int,
    remSleep: Int,
    modifier: Modifier = Modifier
) {
    val total = deepSleep + shallowSleep + remSleep
    if (total == 0) return

    // 色定義
    val deepColor = Color(0xFF4A90E2)     // 青 - 深い睡眠
    val shallowColor = Color(0xFF9B59B6)  // 紫 - 浅い睡眠
    val remColor = Color(0xFF2ECC71)      // 緑 - レム睡眠

    Canvas(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
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
 * 睡眠ステージ内訳表示
 */
@Composable
private fun SleepStageBreakdown(sleepData: SleepData) {
    val total = sleepData.deepSleep + sleepData.shallowSleep + sleepData.remSleep

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // 深い睡眠
        SleepStageItem(
            label = "深い睡眠",
            duration = "${sleepData.deepSleep}分",
            percentage = if (total > 0) (sleepData.deepSleep * 100 / total) else 0,
            color = Color(0xFF4A90E2)
        )

        // 浅い睡眠
        SleepStageItem(
            label = "浅い睡眠",
            duration = "${sleepData.shallowSleep}分",
            percentage = if (total > 0) (sleepData.shallowSleep * 100 / total) else 0,
            color = Color(0xFF9B59B6)
        )

        // レム睡眠
        SleepStageItem(
            label = "レム睡眠",
            duration = "${sleepData.remSleep}分",
            percentage = if (total > 0) (sleepData.remSleep * 100 / total) else 0,
            color = Color(0xFF2ECC71)
        )
    }
}

/**
 * 個別の睡眠ステージ表示
 */
@Composable
private fun SleepStageItem(
    label: String,
    duration: String,
    percentage: Int,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // カラーインジケーター
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(6.dp))
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 時間
        UserFontText(
            text = duration,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        // ラベル
        UserFontText(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        // パーセンテージ
        UserFontText(
            text = "${percentage}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

/**
 * 運動データの詳細表示
 */
@Composable
private fun ExerciseDetailView(
    exerciseData: ExerciseData,
    onGoogleFitClick: () -> Unit
) {
    Column {
        // ヘッダー
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserFontText(
                text = "運動データ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            TextButton(onClick = onGoogleFitClick) {
                UserFontText(
                    text = "Google Fit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 運動情報
        UserFontText(
            text = "${exerciseData.type} ${exerciseData.duration}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )

        if (exerciseData.distance != null) {
            Spacer(modifier = Modifier.height(8.dp))
            UserFontText(
                text = "距離: ${exerciseData.distance}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (exerciseData.calories != null) {
            Spacer(modifier = Modifier.height(8.dp))
            UserFontText(
                text = "カロリー: ${exerciseData.calories}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * デイリーデータの詳細表示
 */
@Composable
private fun DailyDetailView(
    dailyData: DailyData,
    onGoogleFitClick: () -> Unit
) {
    Column {
        // ヘッダー
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserFontText(
                text = "今日の健康データ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            TextButton(onClick = onGoogleFitClick) {
                UserFontText(
                    text = "Google Fit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (dailyData.steps != null) {
                DailyMetricItem(
                    label = "歩数",
                    value = "${dailyData.steps}歩"
                )
            }

            if (dailyData.calories != null) {
                DailyMetricItem(
                    label = "カロリー",
                    value = "${dailyData.calories}kcal"
                )
            }
        }
    }
}

@Composable
private fun DailyMetricItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        UserFontText(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        UserFontText(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

// データクラス定義
sealed class HealthData

data class SleepData(
    val startTime: String,
    val endTime: String,
    val totalDuration: String,
    val deepSleep: Int,
    val shallowSleep: Int,
    val remSleep: Int
) : HealthData()

data class ExerciseData(
    val type: String,
    val duration: String,
    val distance: String?,
    val calories: String?
) : HealthData()

data class DailyData(
    val steps: Int?,
    val calories: Int?
) : HealthData()

/**
 * 投稿テキストから健康データを解析
 */
private fun parseHealthData(postText: String): HealthData? {
    return when {
        // 睡眠データの解析
        postText.contains("→") && (postText.contains("🛏️") || postText.contains("深い睡眠")) -> {
            parseSleepData(postText)
        }

        // 運動データの解析
        postText.contains("🏃") || postText.contains("ランニング") -> {
            parseExerciseData(postText)
        }

        // デイリーデータの解析
        postText.contains("歩") && postText.contains("kcal") -> {
            parseDailyData(postText)
        }

        else -> null
    }
}

/**
 * 睡眠データの解析
 */
private fun parseSleepData(postText: String): SleepData? {
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

        SleepData(
            startTime = startTime,
            endTime = endTime,
            totalDuration = duration,
            deepSleep = deepSleep,
            shallowSleep = shallowSleep,
            remSleep = remSleep
        )
    } catch (e: Exception) {
        Log.e("HealthDetailView", "睡眠データ解析エラー", e)
        null
    }
}

/**
 * 運動データの解析
 */
private fun parseExerciseData(postText: String): ExerciseData? {
    return try {
        val lines = postText.lines()
        val firstLine = lines.firstOrNull() ?: return null

        // 運動タイプと時間: "🏃‍♂️ ランニング 30分"
        val exercisePattern = "🏃‍♂️\\s*(\\w+)\\s+(\\d+分?)".toRegex()
        val exerciseMatch = exercisePattern.find(firstLine) ?: return null
        val (type, duration) = exerciseMatch.destructured

// 時刻はダミーに戻す
        val startTime = "12:00"
        val endTime = "12:30"

        // 距離とカロリーを抽出
        val distancePattern = "距離:\\s*([\\d.]+km)".toRegex()
        val caloriesPattern = "カロリー:\\s*(\\d+kcal)".toRegex()

        val distance = distancePattern.find(postText)?.groupValues?.get(1)
        val calories = caloriesPattern.find(postText)?.groupValues?.get(1)

        ExerciseData(
            type = type,
            duration = duration,
            distance = distance,
            calories = calories
        )
    } catch (e: Exception) {
        Log.e("HealthDetailView", "運動データ解析エラー", e)
        null
    }
}

/**
 * デイリーデータの解析
 */
private fun parseDailyData(postText: String): DailyData? {
    return try {
        val stepsPattern = "([\\d,]+)歩".toRegex()
        val caloriesPattern = "([\\d,]+)kcal".toRegex()

        val stepsMatch = stepsPattern.find(postText)
        val caloriesMatch = caloriesPattern.find(postText)

        val steps = stepsMatch?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        val calories = caloriesMatch?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()

        DailyData(
            steps = steps,
            calories = calories
        )
    } catch (e: Exception) {
        Log.e("HealthDetailView", "デイリーデータ解析エラー", e)
        null
    }
}