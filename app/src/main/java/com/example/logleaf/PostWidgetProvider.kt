package com.example.logleaf

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class PostWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_CLICK = "com.example.logleaf.WIDGET_CLICK"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 複数のウィジェットがある場合、それぞれを更新
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // 最初のウィジェットが作成された時の処理
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context) {
        // 最後のウィジェットが削除された時の処理
        super.onDisabled(context)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // ウィジェットのレイアウトを取得
        val views = RemoteViews(context.packageName, R.layout.widget_post)

        // ウィジェットがタップされた時のIntent
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_WIDGET_CLICK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ウィジェット全体にクリックリスナーを設定
        views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)

        // ウィジェットを更新
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}