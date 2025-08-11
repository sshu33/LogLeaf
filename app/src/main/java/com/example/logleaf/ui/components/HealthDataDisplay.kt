package com.example.logleaf.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
    HealthDataDisplay(
        iconRes = iconRes,
        text = "$startTime - $endTime ($duration)",
        modifier = modifier
    )
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
    val displayText = if (distance != null) {
        "$exerciseType $duration $distance"
    } else {
        "$exerciseType $duration"
    }

    HealthDataDisplay(
        iconRes = HealthIcons.EXERCISE,
        text = displayText,
        modifier = modifier
    )
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
    // デバッグログ追加
    Log.d("HealthDisplay", "投稿テキスト: $postText")

    // 投稿テキストを解析して適切なコンポーネントを表示
    when {
        // 睡眠データの判定
        postText.contains("→") && (postText.contains("睡眠") || postText.contains("🌙")) -> {
            Log.d("HealthDisplay", "睡眠データとして判定")
            // 例: "🌙 22:30 → 06:45 (8h15m)"
            val timePattern = "(\\d{2}:\\d{2})\\s*→\\s*(\\d{2}:\\d{2})\\s*\\(([^)]+)\\)".toRegex()
            timePattern.find(postText)?.let { match ->
                val (startTime, endTime, duration) = match.destructured
                SleepDataDisplay(
                    startTime = startTime,
                    endTime = endTime,
                    duration = duration,
                    iconRes = HealthIcons.SLEEP, // ← ここも追加
                    modifier = modifier
                )
            } ?: run {
                Log.d("HealthDisplay", "睡眠パターンマッチ失敗")
                // パターンマッチしない場合はデフォルト表示
                UserFontText(
                    text = postText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = modifier
                )
            }
        }

        // 仮眠データの判定（「仮眠」文字なしで表示）
        postText.contains("仮眠") -> {
            Log.d("HealthDisplay", "仮眠データとして判定")
            val napPattern = "(\\d{2}:\\d{2})\\s*→\\s*(\\d{2}:\\d{2})\\s*\\(([^)]+)\\)".toRegex()
            napPattern.find(postText)?.let { match ->
                val (startTime, endTime, duration) = match.destructured
                SleepDataDisplay(
                    startTime = startTime,
                    endTime = endTime,
                    duration = duration,
                    iconRes = HealthIcons.NAP, // ← ここを追加
                    modifier = modifier
                )
            }
        }

        // 運動データの判定
        postText.contains("ランニング") || postText.contains("🏃") -> {
            Log.d("HealthDisplay", "運動データとして判定")
            // 例: "🏃‍♂️ ランニング 30分\n距離: 2.5km"
            val lines = postText.lines()
            val firstLine = lines.firstOrNull() ?: ""

            val exercisePattern = "(\\w+)\\s+(\\d+分?)".toRegex()
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

        // デイリー健康データの判定（歩数 + カロリー）
        postText.contains("歩") && postText.contains("kcal") -> {
            Log.d("HealthDisplay", "デイリー健康データとして判定")
            // 例: "📊 今日の健康データ\n👟 歩数: 8,542歩\n🔥 消費カロリー: 1,850kcal"
            val stepsPattern = "([\\d,]+)歩".toRegex()
            val caloriesPattern = "([\\d,]+)kcal".toRegex()

            val stepsMatch = stepsPattern.find(postText)
            val caloriesMatch = caloriesPattern.find(postText)

            if (stepsMatch != null && caloriesMatch != null) {
                val stepsStr = stepsMatch.groupValues[1].replace(",", "")
                val caloriesStr = caloriesMatch.groupValues[1].replace(",", "")
                val steps = stepsStr.toIntOrNull() ?: 0
                val calories = caloriesStr.toIntOrNull() ?: 0

                // 歩数とカロリーを並べて表示
                Row(
                    modifier = modifier,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StepsDataDisplay(
                        steps = steps,
                        modifier = Modifier
                    )
                    CaloriesDataDisplay(
                        calories = calories,
                        modifier = Modifier
                    )
                }
            }
        }

        // 歩数データのみの判定
        postText.contains("歩") -> {
            Log.d("HealthDisplay", "歩数データとして判定")
            val stepsPattern = "([\\d,]+)歩".toRegex()
            stepsPattern.find(postText)?.let { match ->
                val stepsStr = match.groupValues[1].replace(",", "")
                val steps = stepsStr.toIntOrNull() ?: 0
                StepsDataDisplay(
                    steps = steps,
                    modifier = modifier
                )
            }
        }

        // カロリーデータのみの判定
        postText.contains("kcal") -> {
            Log.d("HealthDisplay", "カロリーデータとして判定")
            val caloriesPattern = "([\\d,]+)kcal".toRegex()
            caloriesPattern.find(postText)?.let { match ->
                val caloriesStr = match.groupValues[1].replace(",", "")
                val calories = caloriesStr.toIntOrNull() ?: 0
                CaloriesDataDisplay(
                    calories = calories,
                    modifier = modifier
                )
            }
        }

        // デフォルト: 通常のテキスト表示
        else -> {
            Log.d("HealthDisplay", "通常テキストとして表示")
            UserFontText(
                text = postText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = modifier
            )
        }
    }
}
