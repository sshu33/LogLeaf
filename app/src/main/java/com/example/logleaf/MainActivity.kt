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
import androidx.compose.runtime.remember
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
import com.example.logleaf.ui.screens.BlueskyViewModelFactory
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
import java.time.ZoneId

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
    val searchViewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.provideFactory(postDao = postDao)
    )

    // ★★★ この一行が復活しました！ ★★★
    val uiState by mainViewModel.uiState.collectAsState()
    val showSettingsBadge by mainViewModel.showSettingsBadge.collectAsState()


    Scaffold(
        bottomBar = { BottomNavigationBar(navController = navController, showSettingsBadge = showSettingsBadge) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "calendar",
            modifier = Modifier.padding(innerPadding)
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
                    uiState = uiState, // これでuiStateを渡せる
                    initialDateString = dateString,
                    targetPostId = postId,
                    navController = navController,
                    onRefresh = mainViewModel::refreshPosts,
                    isRefreshing = uiState.isRefreshing
                )
            }
            composable("accounts") {
                val accountViewModel: AccountViewModel = viewModel(
                    factory = AccountViewModel.provideFactory(sessionManager = sessionManager)
                )
                AccountScreen(viewModel = accountViewModel, navController = navController)
            }
            composable("settings") {
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

            composable("mastodon_instance?instanceUrl={instanceUrl}", arguments = listOf(navArgument("instanceUrl") { type = NavType.StringType; nullable = true })) { backStackEntry ->
                val instanceUrl = backStackEntry.arguments?.getString("instanceUrl")
                val mastodonViewModel: MastodonInstanceViewModel = viewModel(
                    factory = MastodonInstanceViewModel.provideFactory(
                        mastodonApi = MastodonApi(),
                        sessionManager = sessionManager,
                        initialInstanceUrl = instanceUrl
                    )
                )
                MastodonInstanceScreen(navController = navController, viewModel = mastodonViewModel)
            }
            composable("search") {
                SearchScreen(
                    viewModel = searchViewModel,
                    // ★★★ ここに、正しい画面遷移の命令を書き戻します ★★★
                    onPostClick = { post ->
                        // ★★★ ここで、日本時間に変換してから日付を取り出す ★★★
                        val localDate = post.createdAt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                        val date = localDate.toString()
                        val postId = post.id
                        navController.navigate("calendar?date=$date&postId=$postId")
                    }
                )
            }
        }
    }
}