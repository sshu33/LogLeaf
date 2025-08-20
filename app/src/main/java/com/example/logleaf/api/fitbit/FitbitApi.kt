package com.example.logleaf.api.fitbit

import android.util.Log
import com.example.logleaf.data.model.Account
import com.example.logleaf.data.session.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FitbitApi(private val sessionManager: SessionManager) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            logger = Logger.ANDROID
            level = LogLevel.INFO
        }
    }

    companion object {
        private const val BASE_URL = "https://api.fitbit.com"
        private const val TOKEN_URL = "https://api.fitbit.com/oauth2/token"
        private const val AUTH_URL = "https://www.fitbit.com/oauth2/authorize"
    }

    /**
     * OAuth認証URLを生成
     */
    fun getAuthorizationUrl(clientId: String, redirectUri: String): String {
        val scope = "activity heartrate location nutrition profile settings sleep social weight"
        val encodedRedirectUri = URLEncoder.encode(redirectUri, "UTF-8")

        return "$AUTH_URL?response_type=code&client_id=$clientId&redirect_uri=$encodedRedirectUri&scope=$scope"
    }

    /**
     * 認証コードをアクセストークンに交換
     */
    suspend fun exchangeCodeForToken(
        clientId: String,
        clientSecret: String,
        code: String,
        redirectUri: String
    ): FitbitTokenResponse? {
        return try {
            Log.d("FitbitApi", "トークン交換開始")

            val credentials = "$clientId:$clientSecret"
            val encodedCredentials = android.util.Base64.encodeToString(
                credentials.toByteArray(),
                android.util.Base64.NO_WRAP
            )

            val httpResponse = client.post(TOKEN_URL) {
                headers {
                    append(HttpHeaders.Authorization, "Basic $encodedCredentials")
                    append(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                }
                parameter("grant_type", "authorization_code")
                parameter("code", code)
                parameter("redirect_uri", redirectUri)
            }

            if (httpResponse.status.isSuccess()) {
                val response = Json { ignoreUnknownKeys = true }
                    .decodeFromString<FitbitTokenResponse>(httpResponse.body())
                Log.d("FitbitApi", "トークン取得成功: userId=${response.user_id}")
                response
            } else {
                Log.e("FitbitApi", "HTTP エラー: ${httpResponse.status}")
                null
            }
        } catch (e: Exception) {
            Log.e("FitbitApi", "トークン取得エラー", e)
            null
        }
    }

    /**
     * アカウント情報を保存
     */
    suspend fun saveAccount(tokenResponse: FitbitTokenResponse, period: String): Boolean {
        return try {
            val fitbitAccount = Account.Fitbit(
                accessToken = tokenResponse.access_token,
                refreshToken = tokenResponse.refresh_token,
                fitbitUserId = tokenResponse.user_id,
                period = period,
                needsReauthentication = false,
                isVisible = true,  // ← 「Boolean =」を削除
                lastSyncedAt = null
            )

            sessionManager.saveAccount(fitbitAccount)
            Log.d("FitbitApi", "Fitbitアカウント保存成功")
            true
        } catch (e: Exception) {
            Log.e("FitbitApi", "アカウント保存エラー", e)
            false
        }
    }

    /**
     * リフレッシュトークンを使ってアクセストークンを更新
     */
    /**
     * リフレッシュトークンを使ってアクセストークンを更新
     */
    suspend fun refreshAccessToken(refreshToken: String): FitbitTokenResponse? {
        return try {
            Log.d("FitbitApi", "トークンリフレッシュ開始")

            // FitbitAuthHolderからクライアント認証情報を取得
            val clientId = com.example.logleaf.api.fitbit.FitbitAuthHolder.clientId
            val clientSecret = com.example.logleaf.api.fitbit.FitbitAuthHolder.clientSecret

            if (clientId == null || clientSecret == null) {
                Log.e("FitbitApi", "クライアント認証情報が見つかりません")
                return null
            }

            // Basic認証のためのクレデンシャル
            val credentials = "$clientId:$clientSecret"
            val encodedCredentials = android.util.Base64.encodeToString(
                credentials.toByteArray(),
                android.util.Base64.NO_WRAP
            )

            val httpResponse = client.post(TOKEN_URL) {
                headers {
                    append(HttpHeaders.Authorization, "Basic $encodedCredentials")
                    append(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                }
                parameter("grant_type", "refresh_token")
                parameter("refresh_token", refreshToken)
            }

            if (httpResponse.status.isSuccess()) {
                val response = Json { ignoreUnknownKeys = true }
                    .decodeFromString<FitbitTokenResponse>(httpResponse.body())
                Log.d("FitbitApi", "トークンリフレッシュ成功")
                response
            } else {
                val errorBody = httpResponse.body<String>()
                Log.e("FitbitApi", "トークンリフレッシュ失敗: ${httpResponse.status}")
                Log.e("FitbitApi", "エラーレスポンス: $errorBody")
                null
            }
        } catch (e: Exception) {
            Log.e("FitbitApi", "トークンリフレッシュエラー", e)
            null
        }
    }

    /**
     * アクセストークンを更新してアカウント情報を保存
     */
    suspend fun updateAccountToken(newTokenResponse: FitbitTokenResponse, period: String): Boolean {
        return try {
            val fitbitAccount = Account.Fitbit(
                accessToken = newTokenResponse.access_token,
                refreshToken = newTokenResponse.refresh_token,
                fitbitUserId = newTokenResponse.user_id,
                period = period,
                needsReauthentication = false,
                isVisible = true,
                lastSyncedAt = null
            )

            sessionManager.saveAccount(fitbitAccount)
            Log.d("FitbitApi", "トークン更新完了")
            true
        } catch (e: Exception) {
            Log.e("FitbitApi", "トークン更新エラー", e)
            false
        }
    }

    /**
     * 401エラー時の処理（トークンリフレッシュを試行）
     */
    private suspend fun handle401Error(fitbitAccount: Account.Fitbit): String? {
        Log.d("FitbitApi", "401エラー処理開始")

        // リフレッシュトークンを試行
        val newTokenResponse = refreshAccessToken(fitbitAccount.refreshToken)

        return if (newTokenResponse != null) {
            // トークン更新成功
            val updateSuccess = updateAccountToken(newTokenResponse, fitbitAccount.period)
            if (updateSuccess) {
                Log.d("FitbitApi", "トークン更新成功、新しいアクセストークンを返却")
                newTokenResponse.access_token
            } else {
                Log.e("FitbitApi", "トークン更新後の保存に失敗")
                sessionManager.markAccountForReauthentication(fitbitAccount.userId)
                null
            }
        } else {
            // トークンリフレッシュ失敗 → 再認証が必要
            Log.e("FitbitApi", "トークンリフレッシュ失敗、再認証が必要")
            sessionManager.markAccountForReauthentication(fitbitAccount.userId)
            null
        }
    }

    /**
     * 睡眠データを取得（詳細情報含む）
     */
    suspend fun getSleepData(accessToken: String, date: LocalDate, fitbitAccount: Account.Fitbit): Pair<SleepData?, NapData?>? {
        return try {
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            Log.d("FitbitApi", "睡眠データ取得開始")
            Log.d("FitbitApi", "リクエストURL: $BASE_URL/1.2/user/-/sleep/date/$dateStr.json")
            Log.d("FitbitApi", "アクセストークン先頭: ${accessToken.take(10)}...")

            val response = client.get("$BASE_URL/1.2/user/-/sleep/date/$dateStr.json") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            }

            Log.d("FitbitApi", "睡眠データHTTPステータス: ${response.status}")

            when {
                response.status.isSuccess() -> {
                    // 成功時の処理（既存のコードと同じ）
                    val responseText = response.body<String>()
                    val fitbitResponse = Json { ignoreUnknownKeys = true }
                        .decodeFromString<FitbitSleepResponse>(responseText)

                    Log.d("FitbitApi", "睡眠データ取得成功: $date")
                    if (fitbitResponse.sleep.isNotEmpty()) {
                        parseSleepDataFromRecord(fitbitResponse.sleep.first())
                    } else {
                        Pair(null, null)
                    }
                }

                response.status.value == 401 -> {
                    Log.w("FitbitApi", "401エラー検出、トークンリフレッシュを試行")
                    val newAccessToken = handle401Error(fitbitAccount)

                    if (newAccessToken != null) {
                        Log.d("FitbitApi", "新しいトークンで睡眠データ取得をリトライ")
                        getSleepData(newAccessToken, date, fitbitAccount)
                    } else {
                        Log.e("FitbitApi", "トークンリフレッシュ失敗")
                        null
                    }
                }

                else -> {
                    Log.e("FitbitApi", "睡眠データ取得エラー: ${response.status}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("FitbitApi", "睡眠データ取得エラー: $date", e)
            null
        }
    }

    /**
     * アクティビティデータを取得
     */
    suspend fun getActivityData(accessToken: String, date: LocalDate, fitbitAccount: Account.Fitbit): Pair<ActivityData?, List<ExerciseData>>? {
        return try {
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            Log.d("FitbitApi", "アクティビティデータ取得開始")
            Log.d("FitbitApi", "リクエストURL: $BASE_URL/1/user/-/activities/date/$dateStr.json")
            Log.d("FitbitApi", "アクセストークン先頭: ${accessToken.take(10)}...")

            val response = client.get("$BASE_URL/1/user/-/activities/date/$dateStr.json") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            }

            Log.d("FitbitApi", "アクティビティHTTPステータス: ${response.status}")

            when {
                response.status.isSuccess() -> {
                    // 成功時の処理（既存のコードと同じ）
                    val responseText = response.body<String>()
                    Log.d("FitbitApi", "アクティビティレスポンス内容: $responseText")

                    val fitbitResponse = Json { ignoreUnknownKeys = true }
                        .decodeFromString<FitbitActivityResponse>(responseText)

                    Log.d("FitbitApi", "steps: ${fitbitResponse.summary.steps}")
                    Log.d("FitbitApi", "calories: ${fitbitResponse.summary.caloriesOut}")

                    parseActivityData(fitbitResponse)
                }

                response.status.value == 401 -> {
                    Log.w("FitbitApi", "401エラー検出、トークンリフレッシュを試行")
                    val newAccessToken = handle401Error(fitbitAccount)

                    if (newAccessToken != null) {
                        Log.d("FitbitApi", "新しいトークンでアクティビティデータ取得をリトライ")
                        getActivityData(newAccessToken, date, fitbitAccount)
                    } else {
                        Log.e("FitbitApi", "トークンリフレッシュ失敗")
                        null
                    }
                }

                else -> {
                    Log.e("FitbitApi", "アクティビティデータ取得エラー: ${response.status}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("FitbitApi", "アクティビティデータ取得例外", e)
            null
        }
    }

    // データクラス
    @Serializable
    data class FitbitTokenResponse(
        val access_token: String,
        val refresh_token: String,
        val user_id: String,
        val expires_in: Int
    )

    @Serializable
    data class FitbitSleepResponse(
        val sleep: List<FitbitSleepData>
    )

    @Serializable
    data class FitbitSleepData(
        val dateOfSleep: String,
        val startTime: String,
        val endTime: String,
        val duration: Long,
        val efficiency: Int,
        val isMainSleep: Boolean,  // ← 追加
        val levels: FitbitSleepLevels? = null
    )
    @Serializable
    data class FitbitSleepLevels(
        val summary: FitbitSleepSummary? = null
    )

    @Serializable
    data class FitbitSleepSummary(
        val deep: FitbitSleepStage? = null,
        val light: FitbitSleepStage? = null,
        val rem: FitbitSleepStage? = null,
        val wake: FitbitSleepStage? = null
    )

    @Serializable
    data class FitbitSleepStage(
        val minutes: Int
    )

    @Serializable
    data class FitbitActivityResponse(
        val summary: FitbitActivitySummary,
        val activities: List<FitbitExercise> = emptyList()  // ← 追加
    )


    @Serializable
    data class FitbitActivitySummary(
        val steps: Int? = null,
        val caloriesOut: Int? = null,
        val distances: List<FitbitDistance>? = null
    )

    @Serializable
    data class FitbitDistance(
        val activity: String,
        val distance: Double
    )

    @Serializable
    data class FitbitExercise(
        val activityName: String,
        val startTime: String,
        val duration: Long,
        val calories: Int? = null,
        val distance: Double? = null,
        val logType: String? = null
    )

    // 運動データ用のデータクラス
    data class ExerciseData(
        val name: String,
        val startTime: String,
        val duration: String,
        val calories: Int? = null,
        val distance: Double? = null
    )

    // 仮眠データ用のデータクラス
    data class NapData(
        val startTime: String,
        val endTime: String,
        val duration: String,
        val deepSleep: Int = 0,
        val lightSleep: Int = 0,
        val remSleep: Int = 0,
        val awakeSleep: Int = 0,
        val efficiency: Int = 0
    )

    // シンプルなデータクラス
    data class SleepData(
        val startTime: String,
        val endTime: String,
        val duration: String,
        val deepSleep: Int = 0,
        val lightSleep: Int = 0,
        val remSleep: Int = 0,
        val awakeSleep: Int = 0,
        val efficiency: Int = 0
    )

    data class ActivityData(
        val steps: Int,
        val calories: Int,
        val distance: Double? = null
    )

    private fun parseSleepData(response: FitbitSleepResponse): SleepData? {
        if (response.sleep.isEmpty()) return null

        val sleep = response.sleep.first()

        // 時間をフォーマット
        val startTime = formatTime(sleep.startTime)
        val endTime = formatTime(sleep.endTime)

        // 睡眠ステージ情報
        val levels = sleep.levels?.summary
        val deepSleep = levels?.deep?.minutes ?: 0
        val lightSleep = levels?.light?.minutes ?: 0
        val remSleep = levels?.rem?.minutes ?: 0
        val awakeSleep = levels?.wake?.minutes ?: 0

        // ★ 実際の睡眠時間 = 深い + 浅い + レム（覚醒時間は除く）
        val actualSleepMinutes = deepSleep + lightSleep + remSleep
        val hours = actualSleepMinutes / 60
        val minutes = actualSleepMinutes % 60
        val actualDuration = "${hours}h${minutes}m"

        return SleepData(
            startTime = startTime,
            endTime = endTime,
            duration = actualDuration,  // ★ 実際の睡眠時間を使用
            deepSleep = deepSleep,
            lightSleep = lightSleep,
            remSleep = remSleep,
            awakeSleep = awakeSleep,
            efficiency = sleep.efficiency
        )
    }

    private fun parseActivityData(response: FitbitActivityResponse): Pair<ActivityData?, List<ExerciseData>> {
        Log.d("FitbitApi", "parseActivityData呼出")
        Log.d("FitbitApi", "steps: ${response.summary.steps}")
        Log.d("FitbitApi", "activities count: ${response.activities.size}")

        val summary = response.summary
        val totalDistance = summary.distances?.find { it.activity == "total" }?.distance

        val steps = summary.steps ?: 0
        val calories = summary.caloriesOut ?: 0

        // 健康データ処理（既存ロジック）
        val activityData = if (steps > 0) {
            Log.d("FitbitApi", "ActivityData作成")
            ActivityData(
                steps = steps,
                calories = calories,
                distance = totalDistance
            )
        } else {
            Log.d("FitbitApi", "歩数0以下なのでActivityDataはnull")
            null
        }

        // 運動データ処理（新規追加）
        val exerciseData = response.activities.map { exercise ->
            Log.d("FitbitApi", "運動データ: ${exercise.activityName} at ${exercise.startTime}")
            ExerciseData(
                name = exercise.activityName,
                startTime = exercise.startTime,
                duration = formatExerciseDuration(exercise.duration),
                calories = exercise.calories,
                distance = exercise.distance
            )
        }

        Log.d("FitbitApi", "運動データ${exerciseData.size}件作成")
        return Pair(activityData, exerciseData)
    }

    private fun formatExerciseDuration(milliseconds: Long): String {
        val totalMinutes = milliseconds / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}時間${minutes}分" else "${minutes}分"
    }

    private fun formatTime(timeString: String): String {
        return try {
            val time = timeString.substring(11, 16) // HH:mm部分を抽出
            time
        } catch (e: Exception) {
            timeString
        }
    }

    private fun formatDuration(milliseconds: Long): String {
        val totalMinutes = milliseconds / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}時間${minutes}分"
    }

    /**
     * 睡眠データを期間指定で取得（Fitbit Sleep Log List API使用）
     */
    suspend fun getSleepDataRange(
        accessToken: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Pair<Map<LocalDate, SleepData>, Map<LocalDate, NapData>>? {

        return try {
            val startDateStr = startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val endDateStr = endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            Log.d("FitbitApi", "睡眠データ期間取得開始")
            Log.d("FitbitApi", "期間: $startDateStr ～ $endDateStr")
            Log.d("FitbitApi", "リクエストURL: $BASE_URL/1.2/user/-/sleep/list.json")

            val response = client.get("$BASE_URL/1.2/user/-/sleep/list.json") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
                parameter("afterDate", startDateStr)  // 開始日のみ指定
                parameter("sort", "asc")
                parameter("limit", "100")
            }

            Log.d("FitbitApi", "睡眠データ期間取得HTTPステータス: ${response.status}")

            if (response.status.isSuccess()) {
                val responseText = response.body<String>()
                val fitbitResponse = Json { ignoreUnknownKeys = true }
                    .decodeFromString<FitbitSleepResponse>(responseText)

                Log.d("FitbitApi", "睡眠データ期間取得成功: ${fitbitResponse.sleep.size}件")

                // 日付ごとにマップ化
                val sleepDataMap = mutableMapOf<LocalDate, SleepData>()
                val napDataMap = mutableMapOf<LocalDate, NapData>()

                fitbitResponse.sleep.forEach { sleepRecord ->
                    val (sleepData, napData) = parseSleepDataFromRecord(sleepRecord)
                    val date = LocalDate.parse(sleepRecord.dateOfSleep)

                    if (sleepData != null) {
                        sleepDataMap[date] = sleepData
                    }

                    // 仮眠データの処理（実装）
                    if (napData != null) {
                        napDataMap[date] = napData  // ← 仮眠データも保存
                        Log.d("FitbitApi", "仮眠データ検出: $date")
                    }
                }

                Pair(sleepDataMap, napDataMap)
            } else {
                Log.e("FitbitApi", "睡眠データ期間取得エラー: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e("FitbitApi", "睡眠データ期間取得エラー", e)
            null
        }
    }

    /**
     * 個別の睡眠記録からSleepDataを生成
     */
    private fun parseSleepDataFromRecord(sleep: FitbitSleepData): Pair<SleepData?, NapData?> {
        return try {
            // 時間をフォーマット
            val startTime = formatTime(sleep.startTime)
            val endTime = formatTime(sleep.endTime)

            // 睡眠ステージ情報
            val levels = sleep.levels?.summary
            val deepSleep = levels?.deep?.minutes ?: 0
            val lightSleep = levels?.light?.minutes ?: 0
            val remSleep = levels?.rem?.minutes ?: 0
            val awakeSleep = levels?.wake?.minutes ?: 0

            // 実際の睡眠時間 = 深い + 浅い + レム（覚醒時間は除く）
            val actualSleepMinutes = deepSleep + lightSleep + remSleep
            val hours = actualSleepMinutes / 60
            val minutes = actualSleepMinutes % 60
            val actualDuration = "${hours}時間${minutes}分"

            if (sleep.isMainSleep) {
                // メイン睡眠
                val sleepData = SleepData(
                    startTime = startTime,
                    endTime = endTime,
                    duration = actualDuration,
                    deepSleep = deepSleep,
                    lightSleep = lightSleep,
                    remSleep = remSleep,
                    awakeSleep = awakeSleep,
                    efficiency = sleep.efficiency
                )
                Pair(sleepData, null)
            } else {
                // 仮眠
                val napData = NapData(
                    startTime = startTime,
                    endTime = endTime,
                    duration = actualDuration,
                    deepSleep = deepSleep,
                    lightSleep = lightSleep,
                    remSleep = remSleep,
                    awakeSleep = awakeSleep,
                    efficiency = sleep.efficiency
                )
                Pair(null, napData)
            }
        } catch (e: Exception) {
            Log.e("FitbitApi", "睡眠データ解析エラー", e)
            Pair(null, null)
        }
    }
}