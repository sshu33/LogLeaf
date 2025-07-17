package com.example.logleaf

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.logleaf.data.font.FontSettingsManager
import com.example.logleaf.db.AppDatabase
import com.example.logleaf.ui.components.BottomNavigationBar
import com.example.logleaf.ui.entry.PostEntrySheet
import com.example.logleaf.ui.screens.AccountScreen
import com.example.logleaf.ui.screens.AccountViewModel
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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

            // 4. ViewModelが持つ、最新のUiStateを、ここで監視する！
            val fontUiState by fontSettingsViewModel.uiState.collectAsState()

            // 5. 監視したUiStateを、丸ごと、LogLeafThemeに渡す！
            LogLeafTheme(
                fontSettings = fontUiState
            ) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null && uri.scheme == "logleaf" && uri.host == "callback") {
                @Suppress("DEPRECATION")
                GlobalScope.launch {
                    MastodonAuthHolder.postUri(uri)
                }
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

    // AppEntryの役割は、MainScreenを呼び出すだけになる
    MainScreen(
        sessionManager = sessionManager,
        onLogout = onLogout,
        onNavigateToLogin = { /* この命令はMainScreenの中で処理される */ }
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

    val mainViewModel: MainViewModel = viewModel(
        factory = MainViewModel.provideFactory(
            blueskyApi = BlueskyApi(sessionManager),
            mastodonApi = MastodonApi(),
            sessionManager = sessionManager,
            postDao = postDao
        )
    )

    val uiState by mainViewModel.uiState.collectAsState()
    val showSettingsBadge by mainViewModel.showSettingsBadge.collectAsState()

    val searchViewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.provideFactory(
            postDao = postDao,
            sessionManager = sessionManager
        )
    )

    var showPostEntrySheet by remember { mutableStateOf(false) }

    // 2. UI全体をBoxで囲みます。これが新しい土台です。
    Box(modifier = Modifier.fillMaxSize()) {

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
                        isRefreshing = uiState.isRefreshing, // ← この行も、正しく含まれています
                        onAddPost = { showPostEntrySheet = true }
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

                composable(
                    "settings",
                    enterTransition = {
                        // 右側から画面の全幅分スライドインしてくる
                        slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300))
                    },
                    exitTransition = {
                        // 右側へ画面の全幅分スライドアウトしていく
                        slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                    }
                ) {
                    SettingsScreen(
                        navController = navController,
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

                composable("search") {
                    SearchScreen(
                        viewModel = searchViewModel, // ★ 外で作った、長生きのViewModelを渡すだけ！
                        onPostClick = { post ->
                            val localDate =
                                post.createdAt.withZoneSameInstant(ZoneId.systemDefault())
                                    .toLocalDate()
                            val date = localDate.toString()
                            val postId = post.id
                            navController.navigate("calendar?date=$date&postId=$postId")
                        }
                    )
                }
            }
        }
        if (showPostEntrySheet) {
            PostEntrySheet(
                isVisible = true,
                onDismissRequest = { showPostEntrySheet = false }
            )
        }
    }
}
