package com.example.logleaf

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.logleaf.data.font.FontSettingsManager
import com.example.logleaf.db.AppDatabase
import com.example.logleaf.ui.theme.LogLeafTheme
import com.leaf.logleaf.ui.entry.PostEntryDialog
import kotlinx.coroutines.delay

class WidgetPostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

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
                    mastodonApi = MastodonApi(),
                    sessionManager = sessionManager,
                    postDao = postDao
                )
            )

            val fontUiState by fontSettingsViewModel.uiState.collectAsState()
            val uiState by mainViewModel.uiState.collectAsState()

            // 投稿ダイアログを自動で表示
            LaunchedEffect(Unit) {
                delay(100)
                mainViewModel.showPostEntrySheet()
            }

            LogLeafTheme(fontSettings = fontUiState) {
                // 透明な背景
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { finish() }
                        )
                )

                if (uiState.isPostEntrySheetVisible) {
                    PostEntryDialog(
                        postText = uiState.postText,
                        onTextChange = mainViewModel::onPostTextChange,
                        onPostSubmit = { timeText, tagNames ->
                            mainViewModel.submitPost(timeText, tagNames)
                            finish()
                        },
                        onDismissRequest = {
                            mainViewModel.onCancel()
                            finish()
                        },
                        dateTime = uiState.editingDateTime,
                        onDateTimeChange = mainViewModel::onDateTimeChange,
                        onRevertDateTime = mainViewModel::revertDateTime,
                        currentTags = uiState.editingTags,
                        onAddTag = mainViewModel::onAddTag,
                        onRemoveTag = mainViewModel::onRemoveTag,
                        favoriteTags = uiState.favoriteTags,
                        frequentlyUsedTags = uiState.frequentlyUsedTags,
                        onToggleFavorite = mainViewModel::toggleTagFavoriteStatus,
                        selectedImageUris = uiState.selectedImageUris,
                        onLaunchPhotoPicker = { },
                        onImageSelected = mainViewModel::onImageSelected,
                        onImageRemoved = mainViewModel::onImageRemoved,
                        onImageReordered = mainViewModel::onImageReordered,
                        onCreateCameraImageUri = mainViewModel::createImageUri,
                        requestFocus = uiState.requestFocus,
                        onFocusConsumed = mainViewModel::consumeFocusRequest
                    )
                }
            }
        }
    }
}