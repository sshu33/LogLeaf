package com.example.logleaf.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.logleaf.R
import com.yourpackage.logleaf.ui.components.UserFontText

/**
 * 健康データ用のアイコン文字コード（Material Icons）
 */
object HealthIcons {
    val EXERCISE = R.drawable.ic_exercise
    val SLEEP = R.drawable.ic_sleep
    val NAP = R.drawable.ic_nap
    val STEPS = R.drawable.ic_step
    val CALORIES = R.drawable.ic_burn
}

/**
 * 健康データのミニマル表示コンポーネント
 */
@Composable
fun HealthDataDisplay(
    iconRes: Int,  // ImageVector → Int (リソースID)
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier
                .size((MaterialTheme.typography.bodyMedium.fontSize.value * 1.2).dp)
                .offset(y = 1.dp), // 少し下に移動
            tint = MaterialTheme.colorScheme.onSurface // 文字色と同じ
        )

        Spacer(modifier = Modifier.width(4.dp))

        UserFontText(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 睡眠データ表示
 */
@Composable
fun SleepDataDisplay(
    startTime: String,
    endTime: String,
    duration: String,
    iconRes: Int = HealthIcons.SLEEP, // デフォルトは通常の睡眠アイコン
    modifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HealthDataDisplay(
            iconRes = iconRes,
            text = "$startTime - $endTime",
            modifier = Modifier
        )
        UserFontText(
            text = duration,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 運動データ表示
 */
@Composable
fun ExerciseDataDisplay(
    exerciseType: String,
    duration: String,
    distance: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HealthDataDisplay(
            iconRes = HealthIcons.EXERCISE,
            text = "$exerciseType ${distance ?: ""}",
            modifier = Modifier
        )
        UserFontText(
            text = duration.replace("分", "m"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 歩数データ表示
 */
@Composable
fun StepsDataDisplay(
    steps: Int,
    modifier: Modifier = Modifier
) {
    HealthDataDisplay(
        iconRes = HealthIcons.STEPS,
        text = "${steps.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")}歩",
        modifier = modifier
    )
}

/**
 * カロリーデータ表示
 */
@Composable
fun CaloriesDataDisplay(
    calories: Int,
    modifier: Modifier = Modifier
) {
    HealthDataDisplay(
        iconRes = HealthIcons.CALORIES,
        text = "${calories.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")}kcal",
        modifier = modifier
    )
}

/**
 * 健康データの投稿テキストから表示コンポーネントを生成
 */
@Composable
fun HealthPostDisplay(
    postText: String,
    modifier: Modifier = Modifier
) {
    Log.d("HealthDisplay", "投稿テキスト: $postText")

    when {
        // GoogleFit睡眠データの判定（既存のまま）
        postText.contains("→") && (postText.contains("🛏️") || postText.contains("深い睡眠")) -> {
            Log.d("HealthDisplay", "GoogleFit睡眠データとして判定")
            val timePattern = "(\\d{2}:\\d{2})\\s*→\\s*(\\d{2}:\\d{2})\\s*\\(([^)]+)\\)".toRegex()
            timePattern.find(postText)?.let { match ->
                val (startTime, endTime, duration) = match.destructured
                SleepDataDisplay(
                    startTime = startTime,
                    endTime = endTime,
                    duration = duration,
                    iconRes = HealthIcons.SLEEP,
                    modifier = modifier
                )
            }
        }

        // GoogleFit睡眠データの判定（既存のまま）
        postText.contains("→") && (postText.contains("🛏️") || postText.contains("深い睡眠")) -> {
            Log.d("HealthDisplay", "GoogleFit睡眠データとして判定")
            val timePattern = "(\\d{2}:\\d{2})\\s*→\\s*(\\d{2}:\\d{2})\\s*\\(([^)]+)\\)".toRegex()
            timePattern.find(postText)?.let { match ->
                val (startTime, endTime, duration) = match.destructured
                SleepDataDisplay(
                    startTime = startTime,
                    endTime = endTime,
                    duration = duration,
                    iconRes = HealthIcons.SLEEP,
                    modifier = modifier
                )
            } ?: run {
                Log.d("HealthDisplay", "睡眠パターンマッチ失敗")
                UserFontText(
                    text = postText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = modifier
                )
            }
        }

        // 仮眠データの判定（既存のまま）
        postText.contains("仮眠") -> {
            Log.d("HealthDisplay", "仮眠データとして判定")
            val napPattern = "(\\d{2}:\\d{2})\\s*→\\s*(\\d{2}:\\d{2})\\s*\\(([^)]+)\\)".toRegex()
            napPattern.find(postText)?.let { match ->
                val (startTime, endTime, duration) = match.destructured
                SleepDataDisplay(
                    startTime = startTime,
                    endTime = endTime,
                    duration = duration,
                    iconRes = HealthIcons.NAP,
                    modifier = modifier
                )
            }
        }

        // 運動データの判定（既存のまま）
        postText.contains("ランニング") || postText.contains("🏃") -> {
            Log.d("HealthDisplay", "運動データとして判定")
            val lines = postText.lines()
            val firstLine = lines.firstOrNull() ?: ""

            val exercisePattern = "🏃‍♂️\\s*(\\w+)\\s+\\d{2}:\\d{2}\\s*-\\s*\\d{2}:\\d{2}\\s+(\\d+分?)".toRegex()
            val distancePattern = "距離:\\s*([\\d.]+km)".toRegex()

            exercisePattern.find(firstLine)?.let { match ->
                val (exerciseType, duration) = match.destructured
                val distance = distancePattern.find(postText)?.groupValues?.get(1)

                ExerciseDataDisplay(
                    exerciseType = exerciseType,
                    duration = duration,
                    distance = distance,
                    modifier = modifier
                )
            }
        }

        // デイリー健康データの判定（既存のまま）
        postText.contains("歩") && postText.contains("kcal") -> {
            Log.d("HealthDisplay", "デイリー健康データとして判定")

            val stepsPattern = "歩数:\\s*([\\d,]+)歩".toRegex()
            val caloriesPattern = "消費カロリー:\\s*([\\d,]+)kcal".toRegex()

            val stepsMatch = stepsPattern.find(postText)
            val caloriesMatch = caloriesPattern.find(postText)

            if (stepsMatch != null && caloriesMatch != null) {
                val stepsStr = stepsMatch.groupValues[1].replace(",", "")
                val caloriesStr = caloriesMatch.groupValues[1].replace(",", "")
                val steps = stepsStr.toIntOrNull() ?: 0
                val calories = caloriesStr.toIntOrNull() ?: 0

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StepsDataDisplay(steps = steps)
                    CaloriesDataDisplay(calories = calories)
                }
            } else {
                // パターンマッチ失敗時はデフォルト表示
                UserFontText(
                    text = postText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = modifier
                )
            }
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