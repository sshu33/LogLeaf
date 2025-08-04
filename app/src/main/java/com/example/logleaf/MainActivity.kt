package com.example.logleaf

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.logleaf.data.font.FontSettingsManager
import com.example.logleaf.db.AppDatabase
import com.example.logleaf.ui.components.BottomNavigationBar
import com.example.logleaf.ui.screens.AccountScreen
import com.example.logleaf.ui.screens.AccountViewModel
import com.example.logleaf.ui.screens.BackupSettingsScreen
import com.example.logleaf.ui.screens.BlueskyViewModelFactory
import com.example.logleaf.ui.screens.CalendarScreen
import com.example.logleaf.ui.screens.FontSettingsScreen
import com.example.logleaf.ui.screens.LoginScreen
import com.example.logleaf.ui.screens.MastodonInstanceScreen
import com.example.logleaf.ui.screens.SearchScreen
import com.example.logleaf.ui.screens.SearchViewModel
import com.example.logleaf.ui.screens.SettingsScreen
import com.example.logleaf.ui.screens.SnsSelectScreen
import com.example.logleaf.ui.screens.TimelineScreen
import com.example.logleaf.ui.theme.LogLeafTheme
import com.example.logleaf.ui.widget.PostWidgetProvider
import com.leaf.logleaf.ui.entry.PostEntryDialog
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContent {

            // 1. contextとapplicationを、一度だけ取得
            val context = LocalContext.current
            val application = context.applicationContext as Application

            // 2. FontSettingsManagerを、一度だけ生成
            val fontSettingsManager = remember { FontSettingsManager(context) }

            // 3. FontSettingsViewModelを、ここで、一度だけ生成する！
            val fontSettingsViewModel: FontSettingsViewModel = viewModel(
                factory = FontSettingsViewModel.provideFactory(
                    application = application,
                    fontSettingsManager = fontSettingsManager
                )
            )

            val fontUiState by fontSettingsViewModel.uiState.collectAsState()

            LogLeafTheme(fontSettings = fontUiState) {
                AppEntry(
                    onLogout = {
                        val intent = packageManager.getLaunchIntentForPackage(packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}


@Composable
fun AppEntry(
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context.applicationContext) }

    MainScreen(
        sessionManager = sessionManager,
        onLogout = onLogout,
        onNavigateToLogin = { }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    sessionManager: SessionManager,
    onLogout: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val postDao = remember { db.postDao() }

    val application = LocalContext.current.applicationContext as Application // ◀◀◀ この行を追加
    val mainViewModel: MainViewModel = viewModel(
        factory = MainViewModel.provideFactory(
            application = application,
            blueskyApi = BlueskyApi(sessionManager),
            mastodonApi = MastodonApi(),
            sessionManager = sessionManager,
            postDao = postDao
        )
    )

    val activity = context as? ComponentActivity
    LaunchedEffect(Unit) {
        activity?.let { act ->
            if (act.intent?.action == PostWidgetProvider.ACTION_WIDGET_CLICK) {
                // ウィジェットから起動された場合、投稿ダイアログを表示
                mainViewModel.showPostEntrySheet()
                // intentを消費して、再度開かないようにする
                act.intent = null
            }
        }
    }

    val uiState by mainViewModel.uiState.collectAsState()
    val showSettingsBadge by mainViewModel.showSettingsBadge.collectAsState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5), // 最大5枚まで選択可能
        onResult = { uris ->
            // 複数画像が選択されたら、ViewModelに渡す
            mainViewModel.onMultipleImagesSelected(uris)
        }
    )

    val searchViewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.provideFactory(
            postDao = postDao,
            sessionManager = sessionManager
        )
    )


    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                showSettingsBadge = showSettingsBadge
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "calendar",
            modifier = Modifier.padding(innerPadding),

            enterTransition = { fadeIn(animationSpec = tween(150)) },
            exitTransition = { fadeOut(animationSpec = tween(150)) }

        ) {
            composable("timeline") {
                TimelineScreen(
                    uiState = uiState, // これでuiStateを渡せる
                    onRefresh = mainViewModel::refreshPosts,
                    navController = navController
                )
            }
            composable(
                route = "calendar?date={date}&postId={postId}",
                arguments = listOf(
                    navArgument("date") { type = NavType.StringType; nullable = true },
                    navArgument("postId") { type = NavType.StringType; nullable = true }
                )
            ) { backStackEntry ->
                val dateString = backStackEntry.arguments?.getString("date")
                val postId = backStackEntry.arguments?.getString("postId")
                CalendarScreen(
                    uiState = uiState,
                    initialDateString = dateString,
                    targetPostId = postId,
                    navController = navController,
                    onRefresh = mainViewModel::refreshPosts,
                    isRefreshing = uiState.isRefreshing,
                    onShowPostEntry = { mainViewModel.showPostEntrySheet() },
                    onDismissPostEntry = { mainViewModel.onCancel() },
                    onToggleShowHidden = { mainViewModel.toggleShowHiddenPosts() },
                    onStartEditingPost = { postWithTagsAndImages -> mainViewModel.startEditingPost(postWithTagsAndImages) },
                    onSetPostHidden = { postId, isHidden -> mainViewModel.setPostHidden(postId, isHidden) },
                    onDeletePost = { postId -> mainViewModel.deletePost(postId) },
                    scrollToTopEvent = mainViewModel.scrollToTopEvent.collectAsState().value,
                    onConsumeScrollToTopEvent = { mainViewModel.consumeScrollToTopEvent() }
                )
            }

            composable("font_settings") {
                val context = LocalContext.current
                val application = context.applicationContext as Application
                val fontSettingsManager = remember { FontSettingsManager(context) }
                val fontSettingsViewModel: FontSettingsViewModel = viewModel(
                    factory = FontSettingsViewModel.provideFactory(
                        application = application,
                        fontSettingsManager = fontSettingsManager
                    )
                )
                FontSettingsScreen(
                    viewModel = fontSettingsViewModel,
                    navController = navController
                )
            }

            composable("accounts") {
                val accountViewModel: AccountViewModel = viewModel(
                    factory = AccountViewModel.provideFactory(
                        sessionManager = sessionManager,
                        postDao = postDao // ★ この行を追加するだけ！
                    )
                )
                AccountScreen(viewModel = accountViewModel, navController = navController)
            }

            composable("settings",
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                }
            ) {
                SettingsScreen(
                    navController = navController,
                    mainViewModel = mainViewModel, // ← この行を追加
                    onLogout = onLogout,
                    showAccountBadge = showSettingsBadge,
                )
            }
            composable("sns_select") { SnsSelectScreen(navController = navController) }

            composable("login") {
                // SessionManagerはこのNavHostよりも上位のスコープで
                // 生成・保持されていることを想定しています。
                val blueskyApi = BlueskyApi(sessionManager)

                // ViewModelを生成します
                val viewModel: BlueskyLoginViewModel = viewModel(
                    factory = BlueskyViewModelFactory(blueskyApi)
                )

                // LoginScreenに生成したViewModelを渡します
                LoginScreen(
                    navController = navController,
                    viewModel = viewModel,
                    screenTitle = "Bluesky ログイン"
                )
            }

            composable(
                "mastodon_instance?instanceUrl={instanceUrl}",
                arguments = listOf(navArgument("instanceUrl") {
                    type = NavType.StringType; nullable = true
                })
            ) { backStackEntry ->
                val instanceUrl = backStackEntry.arguments?.getString("instanceUrl")
                val mastodonViewModel: MastodonInstanceViewModel = viewModel(
                    factory = MastodonInstanceViewModel.provideFactory(
                        mastodonApi = MastodonApi(),
                        sessionManager = sessionManager,
                        initialInstanceUrl = instanceUrl
                    )
                )
                MastodonInstanceScreen(
                    navController = navController,
                    viewModel = mastodonViewModel
                )
            }

            composable(
                route = "search?tag={tag}",
                arguments = listOf(
                    navArgument("tag") { type = NavType.StringType; nullable = true }
                )
            ) { backStackEntry ->
                val tagToSearch = backStackEntry.arguments?.getString("tag")

                // この LaunchedEffect が、ViewModel に「タグで検索しろ」と確実に伝えます
                LaunchedEffect(tagToSearch) {
                    if (tagToSearch != null) {
                        Log.d("TagSearchDebug", "2. [NavHost] Received tag: $tagToSearch") // ◀◀ この行を追加
                        searchViewModel.searchByTag(tagToSearch)
                    }
                }

                SearchScreen(
                    viewModel = searchViewModel,
                    onPostClick = { post ->
                        val localDate =
                            post.createdAt.withZoneSameInstant(ZoneId.systemDefault())
                                .toLocalDate()
                        val date = localDate.toString()
                        val postId = post.id
                        navController.navigate("calendar?date=$date&postId=$postId")
                    },
                )
            }
            composable("backup_settings") {
                BackupSettingsScreen(
                    navController = navController,
                    mainViewModel = mainViewModel
                )
            }
        }
    }
    if (uiState.isPostEntrySheetVisible) { // ViewModelの状態を監視
        Dialog(
            onDismissRequest = { mainViewModel.onCancel() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {

            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

            PostEntryDialog(
                postText = uiState.postText,
                onTextChange = { mainViewModel.onPostTextChange(it) },
                onPostSubmit = { unconfirmedTime, tagNames ->
                    mainViewModel.submitPost(unconfirmedTime, tagNames)
                },
                onDismissRequest = { mainViewModel.onCancel() },
                dateTime = uiState.editingDateTime,
                onDateTimeChange = { newDateTime -> mainViewModel.onDateTimeChange(newDateTime) },
                onRevertDateTime = { mainViewModel.revertDateTime() },
                currentTags = uiState.editingTags,
                onAddTag = { tagName ->
                    Log.d("TagDebug", "UI Event: Add tag -> $tagName") // ◀◀ 追加
                    mainViewModel.onAddTag(tagName)
                },
                onRemoveTag = { tag ->
                    Log.d("TagDebug", "UI Event: Remove tag -> ${tag.tagName}") // ◀◀ 追加
                    mainViewModel.onRemoveTag(tag)
                },

                favoriteTags = uiState.favoriteTags,
                onFavoriteTagReorder = mainViewModel::onFavoriteTagReorder,
                frequentlyUsedTags = uiState.frequentlyUsedTags,
                onToggleFavorite = mainViewModel::toggleTagFavoriteStatus,

                selectedImageUris = uiState.selectedImageUris,
                onLaunchPhotoPicker = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onImageSelected = { uri -> mainViewModel.onImageSelected(uri) },
                onImageRemoved = { index -> mainViewModel.onImageRemoved(index) },
                onImageReordered = { fromIndex, toIndex ->
                    mainViewModel.onImageReordered(fromIndex, toIndex)
                },
                onCreateCameraImageUri = { mainViewModel.createImageUri() },
                requestFocus = uiState.requestFocus,
                onFocusConsumed = { mainViewModel.consumeFocusRequest() }
            )
        }
    }
}
