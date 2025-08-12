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

    // ★ GoogleFitAuthManagerと同じFitnessOptionsを定義
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
            Log.d("GoogleFitDataManager", "getSleepData開始: $date")

            // ★ 修正: 拡張アカウントを取得
            val account = GoogleSignIn.getAccountForExtension(context, fitnessOptions)
            Log.d("GoogleFitDataManager", "Googleアカウント: ${account?.email}")

            if (account == null) {
                Log.e("GoogleFitDataManager", "Googleアカウントがnull")
                return null
            }

            // ★ 権限チェックを追加
            if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                Log.e("GoogleFitDataManager", "Google Fitの権限がありません")
                return null
            }

            val startTime = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endTime = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            Log.d("GoogleFitDataManager", "睡眠データ時刻範囲: $startTime - $endTime")

            val request = DataReadRequest.Builder()
                .read(DataType.TYPE_SLEEP_SEGMENT)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()

            Log.d("GoogleFitDataManager", "睡眠データリクエスト送信中...")
            val response = Fitness.getHistoryClient(context, account).readData(request).await()
            Log.d("GoogleFitDataManager", "睡眠データレスポンス受信: ${response.dataSets.size} datasets")

            // レスポンスの詳細をログ出力
            response.dataSets.forEachIndexed { index, dataSet ->
                Log.d("GoogleFitDataManager", "睡眠データセット[$index]: ${dataSet.dataPoints.size} points")
                dataSet.dataPoints.forEach { point ->
                    val startTime = point.getStartTime(TimeUnit.MILLISECONDS)
                    val endTime = point.getEndTime(TimeUnit.MILLISECONDS)
                    Log.d("GoogleFitDataManager", "睡眠セグメント: ${java.util.Date(startTime)} - ${java.util.Date(endTime)}")
                }
            }

            // データ解析処理
            val result = parseSleepData(response)
            Log.d("GoogleFitDataManager", "睡眠解析結果: $result")

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
            Log.d("GoogleFitDataManager", "getActivityData開始: $date")

            // ★ 詳細デバッグを追加
            Log.d("GoogleFitDataManager", "=== GoogleFitDataManager デバッグ ===")
            val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(context)
            val extensionAccount = GoogleSignIn.getAccountForExtension(context, fitnessOptions)

            Log.d("GoogleFitDataManager", "lastSignedInAccount: $lastSignedInAccount")
            Log.d("GoogleFitDataManager", "extensionAccount: $extensionAccount")

            if (lastSignedInAccount != null) {
                Log.d("GoogleFitDataManager", "lastSignedIn email: ${lastSignedInAccount.email}")
                Log.d("GoogleFitDataManager", "lastSignedIn hasPermissions: ${GoogleSignIn.hasPermissions(lastSignedInAccount, fitnessOptions)}")
            }

            if (extensionAccount != null) {
                Log.d("GoogleFitDataManager", "extension email: ${extensionAccount.email}")
                Log.d("GoogleFitDataManager", "extension hasPermissions: ${GoogleSignIn.hasPermissions(extensionAccount, fitnessOptions)}")
            }

            val account = extensionAccount ?: lastSignedInAccount
            Log.d("GoogleFitDataManager", "使用するアカウント: $account")

            if (account == null) {
                Log.e("GoogleFitDataManager", "Googleアカウントがnull")
                return null
            }

            // ★ 権限チェックを追加
            if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                Log.e("GoogleFitDataManager", "Google Fitの権限がありません")
                return null
            }

            Log.d("GoogleFitDataManager", "=== データソース調査 ===")

            val dataSourcesTask = Fitness.getSensorsClient(context, account)
                .findDataSources(
                    DataSourcesRequest.Builder()
                        .setDataTypes(DataType.TYPE_STEP_COUNT_DELTA, DataType.TYPE_CALORIES_EXPENDED)
                        .setDataSourceTypes(DataSource.TYPE_RAW, DataSource.TYPE_DERIVED)
                        .build()
                )

            try {
                val dataSources = dataSourcesTask.await()
                Log.d("GoogleFitDataManager", "利用可能なデータソース数: ${dataSources.size}")
                dataSources.forEachIndexed { index, dataSource ->
                    Log.d("GoogleFitDataManager", "データソース[$index]: ${dataSource.streamName}")
                    Log.d("GoogleFitDataManager", "  - アプリ: ${dataSource.appPackageName}")
                    Log.d("GoogleFitDataManager", "  - デバイス: ${dataSource.device?.model}")
                    Log.d("GoogleFitDataManager", "  - タイプ: ${dataSource.dataType.name}")
                }
            } catch (e: Exception) {
                Log.e("GoogleFitDataManager", "データソース取得エラー", e)
            }

            val startTime = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endTime = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            Log.d("GoogleFitDataManager", "時刻範囲: $startTime - $endTime")
            Log.d("GoogleFitDataManager", "日付範囲: ${java.util.Date(startTime)} - ${java.util.Date(endTime)}")

            // ★ 集約データを使用するように修正
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

            Log.d("GoogleFitDataManager", "歩数データリクエスト送信中...")
            val stepsResponse = Fitness.getHistoryClient(context, account).readData(stepsRequest).await()
            Log.d("GoogleFitDataManager", "歩数データレスポンス受信: ${stepsResponse.buckets.size} buckets")

            Log.d("GoogleFitDataManager", "カロリーデータリクエスト送信中...")
            val caloriesResponse = Fitness.getHistoryClient(context, account).readData(caloriesRequest).await()
            Log.d("GoogleFitDataManager", "カロリーデータレスポンス受信: ${caloriesResponse.buckets.size} buckets")

            // ★ 集約データの解析方法に変更
            stepsResponse.buckets.forEachIndexed { index, bucket ->
                Log.d("GoogleFitDataManager", "歩数バケット[$index]: ${bucket.dataSets.size} datasets")
                bucket.dataSets.forEach { dataSet ->
                    Log.d("GoogleFitDataManager", "歩数データセット: ${dataSet.dataPoints.size} points")
                    dataSet.dataPoints.forEach { point ->
                        val steps = point.getValue(Field.FIELD_STEPS).asInt()
                        Log.d("GoogleFitDataManager", "歩数ポイント: $steps 歩")
                    }
                }
            }

            caloriesResponse.buckets.forEachIndexed { index, bucket ->
                Log.d("GoogleFitDataManager", "カロリーバケット[$index]: ${bucket.dataSets.size} datasets")
                bucket.dataSets.forEach { dataSet ->
                    Log.d("GoogleFitDataManager", "カロリーデータセット: ${dataSet.dataPoints.size} points")
                    dataSet.dataPoints.forEach { point ->
                        val calories = point.getValue(Field.FIELD_CALORIES).asFloat()
                        Log.d("GoogleFitDataManager", "カロリーポイント: $calories kcal")
                    }
                }
            }

            val result = parseAggregateActivityData(stepsResponse, caloriesResponse)
            Log.d("GoogleFitDataManager", "解析結果: $result")

            return result

        } catch (e: Exception) {
            Log.e("GoogleFitDataManager", "アクティビティデータ取得エラー", e)
            null
        }
    }

    // データクラス定義
    data class SleepData(
        val startTime: String,
        val endTime: String,
        val duration: String
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

            for (dataSet in dataSets) {
                for (dataPoint in dataSet.dataPoints) {
                    val sleepType = dataPoint.getValue(Field.FIELD_SLEEP_SEGMENT_TYPE).asInt()

                    // メイン睡眠のセグメントのみ対象
                    if (sleepType in listOf(1, 2, 3, 4)) { // 深い睡眠、浅い睡眠、レム睡眠、覚醒
                        val segmentStart = dataPoint.getStartTime(TimeUnit.MILLISECONDS)
                        val segmentEnd = dataPoint.getEndTime(TimeUnit.MILLISECONDS)

                        if (startTime == null || segmentStart < startTime) {
                            startTime = segmentStart
                        }
                        if (endTime == null || segmentEnd > endTime) {
                            endTime = segmentEnd
                        }
                    }
                }
            }

            if (startTime != null && endTime != null) {
                val start = Instant.ofEpochMilli(startTime).atZone(ZoneId.of("Asia/Tokyo"))
                val end = Instant.ofEpochMilli(endTime).atZone(ZoneId.of("Asia/Tokyo"))
                val duration = Duration.between(start, end)

                SleepData(
                    startTime = start.format(DateTimeFormatter.ofPattern("HH:mm")),
                    endTime = end.format(DateTimeFormatter.ofPattern("HH:mm")),
                    duration = "${duration.toHours()}h${duration.toMinutes() % 60}m"
                )
            } else null

        } catch (e: Exception) {
            Log.e("GoogleFit", "睡眠データ解析エラー", e)
            null
        }
    }

    private fun parseActivityData(stepsResponse: DataReadResponse, caloriesResponse: DataReadResponse): ActivityData? {
        return try {
            var totalSteps = 0
            var totalCalories = 0

            // 歩数データ
            for (dataSet in stepsResponse.dataSets) {
                for (dataPoint in dataSet.dataPoints) {
                    totalSteps += dataPoint.getValue(Field.FIELD_STEPS).asInt()
                }
            }

            // カロリーデータ
            for (dataSet in caloriesResponse.dataSets) {
                for (dataPoint in dataSet.dataPoints) {
                    totalCalories += dataPoint.getValue(Field.FIELD_CALORIES).asFloat().toInt()
                }
            }

            ActivityData(steps = totalSteps, calories = totalCalories)

        } catch (e: Exception) {
            Log.e("GoogleFit", "アクティビティデータ解析エラー", e)
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
            Log.e("GoogleFit", "集約アクティビティデータ解析エラー", e)
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
            Log.d("GoogleFitDataManager", "アカウント取得: $account")

            if (account == null) {
                Log.e("GoogleFitDataManager", "アカウントがnull")
                return false
            }

            // 権限チェック追加
            if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                Log.e("GoogleFitDataManager", "権限なし")
                return false
            }

            val startTime = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endTime = startTime + (60 * 60 * 1000)

            Log.d("GoogleFitDataManager", "時間範囲: $startTime - $endTime")
            Log.d("GoogleFitDataManager", "パッケージ名: ${context.packageName}")

            // テスト歩数データを作成
            val stepsDataSource = DataSource.Builder()
                .setAppPackageName(context.packageName)
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setType(DataSource.TYPE_RAW)
                .build()

            Log.d("GoogleFitDataManager", "データソース作成完了")

            val stepsDataPoint = DataPoint.builder(stepsDataSource)
                .setField(Field.FIELD_STEPS, 5000)
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()

            Log.d("GoogleFitDataManager", "データポイント作成完了")

            val dataSet = DataSet.builder(stepsDataSource)
                .add(stepsDataPoint)
                .build()

            Log.d("GoogleFitDataManager", "データセット作成完了")

            Log.d("GoogleFitDataManager", "データ投入開始...")
            Fitness.getHistoryClient(context, account).insertData(dataSet).await()
            Log.d("GoogleFitDataManager", "テストデータ投入完了")
            true

        } catch (e: Exception) {
            Log.e("GoogleFitDataManager", "テストデータ投入エラー: ${e.message}", e)
            false
        }
    }
}