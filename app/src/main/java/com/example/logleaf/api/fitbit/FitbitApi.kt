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
     * 睡眠データを取得（詳細情報含む）
     */
    suspend fun getSleepData(accessToken: String, date: LocalDate): SleepData? {
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

            if (response.status.isSuccess()) {
                val responseText = response.body<String>()
                val fitbitResponse = Json { ignoreUnknownKeys = true }
                    .decodeFromString<FitbitSleepResponse>(responseText)

                Log.d("FitbitApi", "睡眠データ取得成功: $date")
                parseSleepData(fitbitResponse)
            } else {
                Log.e("FitbitApi", "睡眠データ取得エラー: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e("FitbitApi", "睡眠データ取得エラー: $date", e)
            null
        }
    }

    /**
     * アクティビティデータを取得
     */
    suspend fun getActivityData(accessToken: String, date: LocalDate): ActivityData? {
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

            if (response.status.isSuccess()) {
                val responseText = response.body<String>()
                val fitbitResponse = Json { ignoreUnknownKeys = true }
                    .decodeFromString<FitbitActivityResponse>(responseText)

                Log.d("FitbitApi", "アクティビティデータ取得成功: $date")
                parseActivityData(fitbitResponse)
            } else {
                Log.e("FitbitApi", "アクティビティデータ取得エラー: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e("FitbitApi", "アクティビティデータ取得エラー: $date", e)
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
        val duration: Long,
        val efficiency: Int,
        val startTime: String,
        val endTime: String,
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
        val summary: FitbitActivitySummary
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

    private fun parseActivityData(response: FitbitActivityResponse): ActivityData? {
        val summary = response.summary

        if (summary.steps == null && summary.caloriesOut == null) {
            return null
        }

        // 距離データ（総距離を取得）
        val totalDistance = summary.distances?.find { it.activity == "total" }?.distance

        return ActivityData(
            steps = summary.steps ?: 0,
            calories = summary.caloriesOut ?: 0,
            distance = totalDistance
        )
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
    ): Map<LocalDate, SleepData>? {
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
                parameter("beforeDate", endDateStr)
                parameter("afterDate", startDateStr)
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
                fitbitResponse.sleep.forEach { sleepRecord ->
                    val sleepData = parseSleepDataFromRecord(sleepRecord)
                    if (sleepData != null) {
                        val date = LocalDate.parse(sleepRecord.dateOfSleep)
                        sleepDataMap[date] = sleepData
                    }
                }

                sleepDataMap
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
    private fun parseSleepDataFromRecord(sleep: FitbitSleepData): SleepData? {
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
            val actualDuration = "${hours}h${minutes}m"

            SleepData(
                startTime = startTime,
                endTime = endTime,
                duration = actualDuration,
                deepSleep = deepSleep,
                lightSleep = lightSleep,
                remSleep = remSleep,
                awakeSleep = awakeSleep,
                efficiency = sleep.efficiency
            )
        } catch (e: Exception) {
            Log.e("FitbitApi", "睡眠データ解析エラー", e)
            null
        }
    }
}