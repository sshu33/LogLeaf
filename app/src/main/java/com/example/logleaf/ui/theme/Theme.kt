package com.example.logleaf.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.example.logleaf.ui.font.FontSettingsUiState

private val DarkColorScheme = darkColorScheme(
    primary = LimeGreen,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Orange,
    secondary = OrangeGray,
    tertiary = RedPink, // ← あなたのRedPink
    surfaceVariant = OrangeGray,
    surface = Color.White,
    background = Color.White,
    onSurface = Color.Black,
    onBackground = Color.Black

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

val LocalUserFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

@Composable
fun LogLeafTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    fontSettings: FontSettingsUiState = FontSettingsUiState(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // ★★★ 受け取ったフォント設定を元に、新しいTypographyを動的に生成 ★★★
    val customTypography = Typography(
        bodyLarge = TextStyle(
            fontFamily = fontSettings.selectedFontFamily,
            fontWeight = fontSettings.selectedFontWeight,
            fontSize = fontSettings.fontSize.sp,
            lineHeight = (fontSettings.fontSize * fontSettings.lineHeight).sp,
            letterSpacing = fontSettings.letterSpacing.sp
        ),
        // ★ 必要に応じて、他のスタイル(h1, h2, buttonなど)も、ここでカスタマイズ可能
        // 例: bodyLargeをベースに、少し小さめのスタイルを作る
        bodyMedium = TextStyle(
            fontFamily = fontSettings.selectedFontFamily,
            fontWeight = fontSettings.selectedFontWeight,
            fontSize = fontSettings.fontSize.sp,
            lineHeight = (fontSettings.fontSize * fontSettings.lineHeight).sp,
            letterSpacing = fontSettings.letterSpacing.sp
        ),
        titleLarge = TextStyle(
            fontFamily = fontSettings.selectedFontFamily,
            fontWeight = fontSettings.selectedFontWeight,
            fontSize = fontSettings.fontSize.sp,
            lineHeight = (fontSettings.fontSize * fontSettings.lineHeight).sp,
            letterSpacing = fontSettings.letterSpacing.sp
        )
    )

    CompositionLocalProvider(
        LocalUserFontFamily provides fontSettings.selectedFontFamily
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = customTypography,
            content = content
        )
    }
}

@Composable
fun WithUserFont(
    baseStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    content: @Composable () -> Unit
) {
    val userFontFamily = LocalUserFontFamily.current
    val finalStyle = baseStyle.copy(fontFamily = userFontFamily)

    CompositionLocalProvider(LocalTextStyle provides finalStyle) {
        content()
    }
}

@Composable
fun SettingsTheme(
    content: @Composable () -> Unit
) {
    // 1. 設定画面専用の、固定サイズのTypographyを定義する
    val settingsTypography = Typography(
        titleLarge = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
        titleMedium = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
        // (必要に応じて、他のスタイルもここで定義・上書きできます)
    )

    // 2. MaterialThemeを呼び出し、typographyだけを差し替える
    MaterialTheme(
        typography = settingsTypography,
        content = content
    )
}
