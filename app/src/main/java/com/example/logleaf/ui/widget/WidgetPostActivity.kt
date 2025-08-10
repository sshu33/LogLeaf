package com.example.logleaf

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.logleaf.api.bluesky.BlueskyApi
import com.example.logleaf.api.github.GitHubApi
import com.example.logleaf.api.mastodon.MastodonApi
import com.example.logleaf.data.session.SessionManager
import com.example.logleaf.ui.font.FontSettingsManager
import com.example.logleaf.db.AppDatabase
import com.example.logleaf.ui.theme.LogLeafTheme
import com.example.logleaf.ui.widget.WidgetPostDialog
import com.example.logleaf.ui.font.FontSettingsViewModel
import kotlinx.coroutines.delay

class WidgetPostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            val context = LocalContext.current
            val application = context.applicationContext as android.app.Application
            val sessionManager = remember { SessionManager(context.applicationContext) }
            val db = remember { AppDatabase.getDatabase(context) }
            val postDao = remember { db.postDao() }
            val fontSettingsManager = remember { FontSettingsManager(context) }

            val fontSettingsViewModel: FontSettingsViewModel = viewModel(
                factory = FontSettingsViewModel.provideFactory(
                    application = application,
                    fontSettingsManager = fontSettingsManager
                )
            )

            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.provideFactory(
                    application = application,
                    blueskyApi = BlueskyApi(sessionManager),
                    mastodonApi = MastodonApi(sessionManager),
                    gitHubApi = GitHubApi(sessionManager), // ← 追加
                    sessionManager = sessionManager,
                    postDao = postDao
                )
            )

            val fontUiState by fontSettingsViewModel.uiState.collectAsState()
            val uiState by mainViewModel.uiState.collectAsState()

            var showDialog by remember { mutableStateOf(false) }

            // ダイアログを自動で表示
            LaunchedEffect(Unit) {
                delay(100)
                showDialog = true
            }

            LogLeafTheme(fontSettings = fontUiState) {
                // 完全透明な背景
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                )

                // シンプルなウィジェット専用ダイアログ
                WidgetPostDialog(
                    visible = showDialog,
                    postText = uiState.postText,
                    onTextChange = mainViewModel::onPostTextChange,
                    onPostSubmit = { unconfirmedTime, tagNames ->
                        mainViewModel.submitPost(unconfirmedTime, tagNames)
                        finish()
                    },
                    onDismiss = {
                        showDialog = false
                        finish()
                    },
                    dateTime = uiState.editingDateTime,
                    onDateTimeChange = mainViewModel::onDateTimeChange, // ← 追加
                    onRevertDateTime = mainViewModel::revertDateTime, // ← 追加
                    currentTags = uiState.editingTags,
                    onAddTag = mainViewModel::onAddTag,
                    onRemoveTag = mainViewModel::onRemoveTag,
                    favoriteTags = uiState.favoriteTags,
                    frequentlyUsedTags = uiState.frequentlyUsedTags,
                    onToggleFavorite = mainViewModel::toggleTagFavoriteStatus,
                    selectedImageUris = uiState.selectedImageUris,
                    onLaunchPhotoPicker = { /* 空 */ },
                    onImageSelected = mainViewModel::onImageSelected,
                    onImageRemoved = mainViewModel::onImageRemoved,
                    onImageReordered = mainViewModel::onImageReordered,
                    onCreateCameraImageUri = mainViewModel::createImageUri,
                    requestFocus = true, // ← 追加
                    onFocusConsumed = { /* 空 */ } // ← 追加
                )
            }
        }
    }
}