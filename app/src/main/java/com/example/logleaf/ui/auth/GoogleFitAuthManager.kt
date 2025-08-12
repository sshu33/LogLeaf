package com.example.logleaf.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType

class GoogleFitAuthManager(private val context: Context) {

    private val fitnessOptions = FitnessOptions.builder()
        .addDataType(DataType.TYPE_SLEEP_SEGMENT, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
        .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_WRITE)
        .build()

    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(account, fitnessOptions)
    }

    // 修正: 元の実装に戻す（これが正しい）
    fun signIn(activity: Activity, onResult: (Boolean, String?) -> Unit) {
        if (isSignedIn()) {
            Log.d("GoogleFit", "既にサインイン済み")
            onResult(true, null)
            return
        }

        val account = GoogleSignIn.getAccountForExtension(context, fitnessOptions)
        if (GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            Log.d("GoogleFit", "権限取得済み")
            onResult(true, null)
        } else {
            Log.d("GoogleFit", "権限要求開始")
            GoogleSignIn.requestPermissions(
                activity,
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                account,
                fitnessOptions
            )
        }
    }

    fun handleSignInResult(requestCode: Int, resultCode: Int, onResult: (Boolean, String?) -> Unit) {
        if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d("GoogleFit", "権限取得成功")
                onResult(true, null)
            } else {
                Log.e("GoogleFit", "権限取得失敗")
                onResult(false, "Google Fit権限の取得に失敗しました")
            }
        }
    }

    companion object {
        const val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1001
    }

    fun debugAuthStatus() {
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(context)
        val extensionAccount = GoogleSignIn.getAccountForExtension(context, fitnessOptions)

        Log.d("GoogleFitDebug", "=== 認証状態デバッグ ===")
        Log.d("GoogleFitDebug", "lastSignedInAccount: $lastSignedInAccount")
        Log.d("GoogleFitDebug", "extensionAccount: $extensionAccount")

        if (lastSignedInAccount != null) {
            Log.d("GoogleFitDebug", "lastSignedInAccount email: ${lastSignedInAccount.email}")
            Log.d("GoogleFitDebug", "hasPermissions for lastSignedIn: ${GoogleSignIn.hasPermissions(lastSignedInAccount, fitnessOptions)}")
        }

        if (extensionAccount != null) {
            Log.d("GoogleFitDebug", "extensionAccount email: ${extensionAccount.email}")
            Log.d("GoogleFitDebug", "hasPermissions for extension: ${GoogleSignIn.hasPermissions(extensionAccount, fitnessOptions)}")
        }

        Log.d("GoogleFitDebug", "isSignedIn(): ${isSignedIn()}")
    }
}

