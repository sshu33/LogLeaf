package com.example.logleaf.ui.auth

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn // ★★★ 1. これを追加 ★★★
import androidx.compose.foundation.lazy.items     // ★★★ 2. これを追加 ★★★
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment // ★★★ 3. これを追加 ★★★
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.graphics.Color
import com.example.logleaf.ui.components.ListCard
import com.example.logleaf.R
import com.example.logleaf.ui.theme.SnsType

private data class SnsProvider(
    val name: String,
    val iconResId: Int,
    val route: String?,
    val color: Color,
    val isImplemented: Boolean = true

)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnsSelectScreen(navController: NavController) {
    val context = LocalContext.current

    val snsProviders = listOf(
        SnsProvider("Bluesky", R.drawable.ic_bluesky, "login", SnsType.BLUESKY.brandColor),
        SnsProvider("Mastodon", R.drawable.ic_mastodon, "mastodon_instance", SnsType.MASTODON.brandColor),
        SnsProvider("GitHub", R.drawable.ic_github, "github_login", SnsType.GITHUB.brandColor, isImplemented = true),
        SnsProvider("Google Fit", R.drawable.ic_googlefit, "google_fit_login", SnsType.GOOGLEFIT.brandColor, isImplemented = false) // 🏃‍♂️ Google Fit追加
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("アカウントを追加") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp), // 上下左右に16dpの余白
            verticalArrangement = Arrangement.spacedBy(8.dp) // カード間のスペースを8dpに
        ) {
            items(snsProviders) { sns ->
                ListCard(
                    onClick = {
                        if (sns.isImplemented && sns.route != null) {
                            navController.navigate(sns.route)
                        } else {
                            Toast.makeText(context, "${sns.name}の連携は開発中です", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(id = sns.iconResId),
                        contentDescription = null,
                        tint = sns.color,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = sns.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier.size(48.dp), // IconButtonのデフォルトのタッチ領域サイズに合わせる
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}