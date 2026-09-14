package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = KairoPrimary,
    secondary = KairoMuted,
    tertiary = KairoPrimary,
    background = KairoBackground,
    surface = KairoSurface,
    surfaceVariant = KairoSurfaceVariant,
    onPrimary = KairoBackground,
    onSecondary = KairoText,
    onBackground = KairoText,
    onSurface = KairoText,
    onSurfaceVariant = KairoOnSurfaceVariant
  )

private val LightColorScheme = DarkColorScheme

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  // Dynamic color disabled to retain Kairo brand palette
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

@Composable
fun KairoTheme(content: @Composable () -> Unit) {
  MyApplicationTheme(darkTheme = true, dynamicColor = false, content = content)
}
