package com.example.logleaf

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.logleaf.db.AppDatabase
import com.example.logleaf.ui.components.BottomNavigationBar
import com.example.logleaf.ui.screens.AccountScreen
import com.example.logleaf.ui.screens.AccountViewModel
import com.example.logleaf.ui.screens.CalendarScreen
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LogLeafTheme {
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
    var isLoggedIn by remember { mutableStateOf(sessionManager.getAccounts().isNotEmpty()) }

    if (isLoggedIn) {
        MainScreen(
            sessionManager = sessionManager,
            onLogout = onLogout
        )
    } else {
        LoginScreen(
            onLoginSuccess = { isLoggedIn = true },
            blueskyApi = BlueskyApi(sessionManager)
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    sessionManager: SessionManager,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()

    // ↓↓↓ ここで一度だけ、dbとdaoを生成します ↓↓↓
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val postDao = remember { db.postDao() }

    // ★★★ MainViewModelの生成部分を修正 ★★★
    val mainViewModel: MainViewModel = viewModel(
        factory = MainViewModel.provideFactory(
            blueskyApi = BlueskyApi(sessionManager),
            mastodonApi = MastodonApi(),
            sessionManager = sessionManager,
            postDao = postDao // ← ここで postDao を渡す！
        )
    )
    val uiState by mainViewModel.uiState.collectAsState()
    val showSettingsBadge by mainViewModel.showSettingsBadge.collectAsState()

    Scaffold(
        bottomBar = { BottomNavigationBar(navController = navController, showSettingsBadge = showSettingsBadge) }
    ) { innerPadding ->
        // NavHostにはViewModelを渡さない
        NavHost(
            navController = navController,
            startDestination = "calendar",
            modifier = Modifier.padding(innerPadding)
        ) {

            composable("timeline") {
                TimelineScreen(
                    uiState = uiState,
                    onRefresh = mainViewModel::refreshPosts, // ★ NEW: この行を追加
                    navController = navController
                )
            }

            composable(
                route = "calendar?date={date}&postId={postId}",
                arguments = listOf(
                    navArgument("date") {
                        type = NavType.StringType
                        nullable = true
                    },
                    // ★★★ postIdの定義を追加 ★★★
                    navArgument("postId") {
                        type = NavType.StringType
                        nullable = true
                    }
                )
            ) { backStackEntry ->
                val dateString = backStackEntry.arguments?.getString("date")
                val postId = backStackEntry.arguments?.getString("postId") // ★★★ postIdを取得 ★★★
                CalendarScreen(
                    uiState = uiState,
                    initialDateString = dateString,
                    // ★★★ postIdを渡す ★★★
                    targetPostId = postId,
                    navController = navController,
                    onRefresh = mainViewModel::refreshPosts,
                    isRefreshing = uiState.isRefreshing
                )
            }

            composable("accounts") {
                val accountViewModel: AccountViewModel = viewModel(
                    factory = AccountViewModel.provideFactory(
                        sessionManager = sessionManager // ★ 上で受け取ったSessionManagerを渡す
                    )
                )
                AccountScreen(
                    viewModel = accountViewModel,
                    navController = navController
                )
            }
            composable("settings") {
                // ★★★ ここで、NavHostの外で定義した状態(showSettingsBadge)を渡す ★★★
                SettingsScreen(
                    navController = navController,
                    onLogout = onLogout,
                    showAccountBadge = showSettingsBadge
                )
            }

            composable("sns_select") {
                SnsSelectScreen(navController = navController)
            }

            composable("login") {
                LoginScreen(
                    onLoginSuccess = { navController.popBackStack() },
                    blueskyApi = BlueskyApi(sessionManager), // ★ 上で受け取ったSessionManagerを渡す
                    navController = navController,
                    screenTitle = "Bluesky ログイン"
                )
            }

            composable("mastodon_instance?instanceUrl={instanceUrl}", arguments = listOf(navArgument("instanceUrl") { type = NavType.StringType; nullable = true })) { backStackEntry ->
                val instanceUrl = backStackEntry.arguments?.getString("instanceUrl")
                val mastodonViewModel: MastodonInstanceViewModel = viewModel(
                    factory = MastodonInstanceViewModel.provideFactory(
                        mastodonApi = MastodonApi(),
                        sessionManager = sessionManager, // ★ 上で受け取ったSessionManagerを渡す
                        initialInstanceUrl = instanceUrl
                    )
                )
                MastodonInstanceScreen(
                    navController = navController,
                    viewModel = mastodonViewModel
                )
            }

            composable("search") {
                val searchViewModel: SearchViewModel = viewModel(
                    factory = SearchViewModel.provideFactory(
                        postDao = postDao
                    )
                )
                // ★★★ ここからが修正箇所です ★★★
                SearchScreen(
                    viewModel = searchViewModel,
                    onPostClick = { post ->
                        val date = post.createdAt.toLocalDate().toString()
                        val postId = post.id // ★ postIdを取得
                        // ★ postIdも一緒に渡すように修正
                        navController.navigate("calendar?date=$date&postId=$postId")
                    }
                )
            }
        }
    }
}