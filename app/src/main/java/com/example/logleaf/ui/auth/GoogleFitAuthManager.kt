package com.example.logleaf.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
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

    fun signIn(activity: Activity, onResult: (Boolean, String?) -> Unit) {
        if (isSignedIn()) {
            onResult(true, null)
            return
        }

        val account = GoogleSignIn.getAccountForExtension(context, fitnessOptions)
        if (GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            onResult(true, null)
        } else {
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
                onResult(true, null)
            } else {
                Log.e("GoogleFit", "権限取得失敗: resultCode=$resultCode")
                onResult(false, "Google Fit権限の取得に失敗しました")
            }
        }
    }

    /**
     * Google Fit認証情報をクリアする
     */
    fun clearAuthentication() {
        try {
            val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .addExtension(fitnessOptions)
                .build()

            val signInClient = GoogleSignIn.getClient(context, signInOptions)
            signInClient.signOut()

            Log.d("GoogleFit", "認証情報をクリアしました")
        } catch (e: Exception) {
            Log.e("GoogleFit", "認証クリア中にエラー", e)
        }
    }

    companion object {
        const val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1001
    }
}
