package com.example.logleaf.data.credentials

import android.content.Context
import android.content.SharedPreferences

class CredentialsManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "logleaf_credentials",
        Context.MODE_PRIVATE
    )

    // Bluesky認証情報保存
    fun saveBlueskyCredentials(handle: String, password: String) {
        sharedPreferences.edit()
            .putString("bluesky_handle", handle)
            .putString("bluesky_password", password)
            .apply()
    }

    // Bluesky認証情報取得
    fun getBlueskyCredentials(): Pair<String, String>? {
        val handle = sharedPreferences.getString("bluesky_handle", null)
        val password = sharedPreferences.getString("bluesky_password", null)
        return if (handle != null && password != null) {
            Pair(handle, password)
        } else null
    }

    // Bluesky認証情報削除
    fun clearBlueskyCredentials() {
        sharedPreferences.edit()
            .remove("bluesky_handle")
            .remove("bluesky_password")
            .apply()
    }

    // Mastodon認証情報保存
    fun saveMastodonCredentials(instanceUrl: String, accessToken: String) {
        sharedPreferences.edit()
            .putString("mastodon_instance_url", instanceUrl)
            .putString("mastodon_access_token", accessToken)
            .apply()
    }

    // Mastodon認証情報取得
    fun getMastodonCredentials(): Pair<String, String>? {
        val instanceUrl = sharedPreferences.getString("mastodon_instance_url", null)
        val accessToken = sharedPreferences.getString("mastodon_access_token", null)
        return if (instanceUrl != null && accessToken != null) {
            Pair(instanceUrl, accessToken)
        } else null
    }

    // Mastodon認証情報削除
    fun clearMastodonCredentials() {
        sharedPreferences.edit()
            .remove("mastodon_instance_url")
            .remove("mastodon_access_token")
            .apply()
    }

    // GitHub認証情報保存
    fun saveGitHubCredentials(username: String, token: String) {
        sharedPreferences.edit()
            .putString("github_username", username)
            .putString("github_token", token)
            .apply()
    }

    // GitHub認証情報取得
    fun getGitHubCredentials(): Pair<String, String>? {
        val username = sharedPreferences.getString("github_username", null)
        val token = sharedPreferences.getString("github_token", null)
        return if (username != null && token != null) {
            Pair(username, token)
        } else null
    }

    // GitHub認証情報削除
    fun clearGitHubCredentials() {
        sharedPreferences.edit()
            .remove("github_username")
            .remove("github_token")
            .apply()
    }

    // Fitbit認証情報保存
    fun saveFitbitCredentials(clientId: String, clientSecret: String) {
        sharedPreferences.edit()
            .putString("fitbit_client_id", clientId)
            .putString("fitbit_client_secret", clientSecret)
            .apply()
    }

    // Fitbit認証情報取得
    fun getFitbitCredentials(): Pair<String, String>? {
        val clientId = sharedPreferences.getString("fitbit_client_id", null)
        val clientSecret = sharedPreferences.getString("fitbit_client_secret", null)
        return if (clientId != null && clientSecret != null) {
            Pair(clientId, clientSecret)
        } else null
    }

    // Fitbit認証情報削除
    fun clearFitbitCredentials() {
        sharedPreferences.edit()
            .remove("fitbit_client_id")
            .remove("fitbit_client_secret")
            .apply()
    }
}