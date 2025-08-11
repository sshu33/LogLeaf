package com.example.logleaf.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object GoogleFitUtils {

    /**
     * Google Fitアプリの特定日にDeepLinkで遷移
     */
    fun openGoogleFitForDate(context: Context, date: LocalDate) {
        try {
            // Google Fit のDeepLink URI（日付指定）
            val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val googleFitUri = "https://fit.google.com/fit/view/$dateString"

            // まずGoogle Fitアプリで開こうとする
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(googleFitUri)).apply {
                setPackage("com.google.android.apps.fitness") // Google Fitアプリのパッケージ名
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // Google Fitアプリがインストールされているかチェック
            if (appIntent.resolveActivity(context.packageManager) != null) {
                Log.d("GoogleFit", "Google Fitアプリで開く: $googleFitUri")
                context.startActivity(appIntent)
            } else {
                // アプリがない場合はブラウザで開く
                Log.d("GoogleFit", "ブラウザでGoogle Fitを開く: $googleFitUri")
                openGoogleFitInBrowser(context, dateString)
            }

        } catch (e: Exception) {
            Log.e("GoogleFit", "Google Fit開く際にエラー", e)
            // フォールバック：Google Fit ウェブ版のホームページ
            openGoogleFitHomepage(context)
        }
    }

    /**
     * ブラウザでGoogle Fit ウェブ版を開く
     */
    private fun openGoogleFitInBrowser(context: Context, dateString: String) {
        try {
            val webUri = "https://fit.google.com/fit/view/$dateString"
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUri)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            Log.e("GoogleFit", "ブラウザで開く際にエラー", e)
            openGoogleFitHomepage(context)
        }
    }

    /**
     * フォールバック：Google Fitホームページを開く
     */
    private fun openGoogleFitHomepage(context: Context) {
        try {
            val homeUri = "https://fit.google.com/"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(homeUri)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("GoogleFit", "ホームページ開く際にもエラー", e)
        }
    }

    /**
     * Google Fitアプリがインストールされているかチェック
     */
    fun isGoogleFitInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.google.android.apps.fitness", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Google Fitの運動データページを開く
     */
    fun openGoogleFitExercise(context: Context, date: LocalDate) {
        try {
            val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val exerciseUri = "https://fit.google.com/fit/exercise/$dateString"

            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(exerciseUri)).apply {
                setPackage("com.google.android.apps.fitness")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (appIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(appIntent)
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(exerciseUri)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
            }
        } catch (e: Exception) {
            Log.e("GoogleFit", "運動データ画面開く際にエラー", e)
            openGoogleFitForDate(context, date)
        }
    }

    /**
     * Google Fitの睡眠データページを開く
     */
    fun openGoogleFitSleep(context: Context, date: LocalDate) {
        try {
            val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val sleepUri = "https://fit.google.com/fit/sleep/$dateString"

            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(sleepUri)).apply {
                setPackage("com.google.android.apps.fitness")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (appIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(appIntent)
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(sleepUri)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
            }
        } catch (e: Exception) {
            Log.e("GoogleFit", "睡眠データ画面開く際にエラー", e)
            openGoogleFitForDate(context, date)
        }
    }
}