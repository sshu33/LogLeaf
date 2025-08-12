package com.example.logleaf.api.googlefit

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataReadRequest
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import com.google.android.gms.fitness.data.*
import com.google.android.gms.fitness.request.DataSourcesRequest
import com.google.android.gms.fitness.result.DataReadResponse
import java.time.*
import java.time.format.DateTimeFormatter

class GoogleFitDataManager(private val context: Context) {

    private val fitnessOptions = FitnessOptions.builder()
        .addDataType(DataType.TYPE_SLEEP_SEGMENT, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
        .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_WRITE)
        .build()

    /**
     * 指定した日の睡眠データを取得
     */
    suspend fun getSleepData(date: LocalDate): SleepData? {
        return try {
            Log.d("GoogleFitDataManager", "睡眠データ取得開始: $date")

            val account = GoogleSignIn.getAccountForExtension(context, fitnessOptions)
            if (account == null) {
                Log.e("GoogleFitDataManager", "Googleアカウントがnull")
                return null
            }

            if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                Log.e("GoogleFitDataManager", "Google Fitの権限がありません")
                return null
            }

            val startTime = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endTime = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val request = DataReadRequest.Builder()
                .read(DataType.TYPE_SLEEP_SEGMENT)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()

            val response = Fitness.getHistoryClient(context, account).readData(request).await()
            val result = parseSleepData(response)

            Log.d("GoogleFitDataManager", "睡眠データ取得完了: $result")
            return result

        } catch (e: Exception) {
            Log.e("GoogleFitDataManager", "睡眠データ取得エラー", e)
            null
        }
    }

    /**
     * 指定した日のアクティビティデータを取得
     */
    suspend fun getActivityData(date: LocalDate): ActivityData? {
        return try {
            Log.d("GoogleFitDataManager", "アクティビティデータ取得開始: $date")

            val account = GoogleSignIn.getAccountForExtension(context, fitnessOptions)
            if (account == null) {
                Log.e("GoogleFitDataManager", "Googleアカウントがnull")
                return null
            }

            if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                Log.e("GoogleFitDataManager", "Google Fitの権限がありません")
                return null
            }

            val startTime = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endTime = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val stepsRequest = DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()

            val caloriesRequest = DataReadRequest.Builder()
                .aggregate(DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()

            val stepsResponse = Fitness.getHistoryClient(context, account).readData(stepsRequest).await()
            val caloriesResponse = Fitness.getHistoryClient(context, account).readData(caloriesRequest).await()

            val result = parseAggregateActivityData(stepsResponse, caloriesResponse)
            Log.d("GoogleFitDataManager", "アクティビティデータ取得完了: $result")
            return result

        } catch (e: Exception) {
            Log.e("GoogleFitDataManager", "アクティビティデータ取得エラー", e)
            null
        }
    }

    /**
     * エミュレータ用のテストデータを投入
     */
    suspend fun insertTestData(date: LocalDate): Boolean {
        return try {
            Log.d("GoogleFitDataManager", "テストデータ投入開始: $date")

            val account = GoogleSignIn.getAccountForExtension(context, fitnessOptions)
            if (account == null) {
                Log.e("GoogleFitDataManager", "アカウントがnull")
                return false
            }

            if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                Log.e("GoogleFitDataManager", "権限なし")
                return false
            }

            val startTime = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endTime = startTime + (60 * 60 * 1000)

            val stepsDataSource = DataSource.Builder()
                .setAppPackageName(context.packageName)
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setType(DataSource.TYPE_RAW)
                .build()

            val stepsDataPoint = DataPoint.builder(stepsDataSource)
                .setField(Field.FIELD_STEPS, 5000)
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()

            val dataSet = DataSet.builder(stepsDataSource)
                .add(stepsDataPoint)
                .build()

            Fitness.getHistoryClient(context, account).insertData(dataSet).await()
            Log.d("GoogleFitDataManager", "テストデータ投入完了")
            true

        } catch (e: Exception) {
            Log.e("GoogleFitDataManager", "テストデータ投入エラー", e)
            false
        }
    }

    // データクラス定義
    data class SleepData(
        val startTime: String,
        val endTime: String,
        val duration: String,
        val deepSleep: Int = 0,      // 深い睡眠（分）
        val shallowSleep: Int = 0,   // 浅い睡眠（分）
        val remSleep: Int = 0,       // レム睡眠（分）
        val awakeSleep: Int = 0      // 覚醒時間（分）
    )


    data class ActivityData(
        val steps: Int,
        val calories: Int
    )

    private fun parseSleepData(response: DataReadResponse): SleepData? {
        return try {
            val dataSets = response.dataSets
            if (dataSets.isEmpty()) return null

            var startTime: Long? = null
            var endTime: Long? = null

            // 睡眠ステージ別の時間を蓄積（ミリ秒）
            var deepSleepMillis: Long = 0
            var lightSleepMillis: Long = 0
            var remSleepMillis: Long = 0
            var awakeMillis: Long = 0

            for (dataSet in dataSets) {
                for (dataPoint in dataSet.dataPoints) {
                    val sleepType = dataPoint.getValue(Field.FIELD_SLEEP_SEGMENT_TYPE).asInt()
                    val segmentStart = dataPoint.getStartTime(TimeUnit.MILLISECONDS)
                    val segmentEnd = dataPoint.getEndTime(TimeUnit.MILLISECONDS)
                    val segmentDuration = segmentEnd - segmentStart

                    // 全体の開始・終了時刻を計算
                    if (startTime == null || segmentStart < startTime) {
                        startTime = segmentStart
                    }
                    if (endTime == null || segmentEnd > endTime) {
                        endTime = segmentEnd
                    }

                    // 睡眠ステージ別に時間を蓄積
                    when (sleepType) {
                        1 -> awakeMillis += segmentDuration      // 覚醒
                        2 -> lightSleepMillis += segmentDuration // 浅い睡眠
                        3 -> deepSleepMillis += segmentDuration  // 深い睡眠
                        4 -> remSleepMillis += segmentDuration   // レム睡眠
                        // 他の値は無視
                    }
                }
            }

            if (startTime != null && endTime != null) {
                val start = Instant.ofEpochMilli(startTime).atZone(ZoneId.of("Asia/Tokyo"))
                val end = Instant.ofEpochMilli(endTime).atZone(ZoneId.of("Asia/Tokyo"))
                val duration = Duration.between(start, end)

                // ミリ秒を分に変換
                val deepSleepMinutes = (deepSleepMillis / (1000 * 60)).toInt()
                val lightSleepMinutes = (lightSleepMillis / (1000 * 60)).toInt()
                val remSleepMinutes = (remSleepMillis / (1000 * 60)).toInt()
                val awakeMinutes = (awakeMillis / (1000 * 60)).toInt()

                SleepData(
                    startTime = start.format(DateTimeFormatter.ofPattern("HH:mm")),
                    endTime = end.format(DateTimeFormatter.ofPattern("HH:mm")),
                    duration = "${duration.toHours()}h${duration.toMinutes() % 60}m",
                    deepSleep = deepSleepMinutes,
                    shallowSleep = lightSleepMinutes,
                    remSleep = remSleepMinutes,
                    awakeSleep = awakeMinutes
                )
            } else null

        } catch (e: Exception) {
            Log.e("GoogleFit", "睡眠データ解析エラー", e)
            null
        }
    }

    private fun parseAggregateActivityData(stepsResponse: DataReadResponse, caloriesResponse: DataReadResponse): ActivityData? {
        return try {
            var totalSteps = 0
            var totalCalories = 0

            // 歩数データ（バケット形式）
            for (bucket in stepsResponse.buckets) {
                for (dataSet in bucket.dataSets) {
                    for (dataPoint in dataSet.dataPoints) {
                        totalSteps += dataPoint.getValue(Field.FIELD_STEPS).asInt()
                    }
                }
            }

            // カロリーデータ（バケット形式）
            for (bucket in caloriesResponse.buckets) {
                for (dataSet in bucket.dataSets) {
                    for (dataPoint in dataSet.dataPoints) {
                        totalCalories += dataPoint.getValue(Field.FIELD_CALORIES).asFloat().toInt()
                    }
                }
            }

            ActivityData(steps = totalSteps, calories = totalCalories)

        } catch (e: Exception) {
            Log.e("GoogleFitDataManager", "集約アクティビティデータ解析エラー", e)
            null
        }
    }
}