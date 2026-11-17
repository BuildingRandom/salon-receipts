package ca.home.salonreceipts.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF8A2D5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E3EC),
    onPrimaryContainer = Color(0xFF3A0F25),
    secondary = Color(0xFF6F6470),
    background = Color(0xFFF6F3F5),
    onBackground = Color(0xFF221A20),
    surface = Color.White,
    onSurface = Color(0xFF221A20),
    surfaceVariant = Color(0xFFEFE8EE),
    onSurfaceVariant = Color(0xFF6F6470),
    outline = Color(0xFFD9D0D7),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE58AB6),
    onPrimary = Color(0xFF2A0D1B),
    primaryContainer = Color(0xFF3A2230),
    onPrimaryContainer = Color(0xFFF7DCEA),
    secondary = Color(0xFFA99BA6),
    background = Color(0xFF171215),
    onBackground = Color(0xFFF2EAF0),
    surface = Color(0xFF221B20),
    onSurface = Color(0xFFF2EAF0),
    surfaceVariant = Color(0xFF2E252B),
    onSurfaceVariant = Color(0xFFA99BA6),
    outline = Color(0xFF3A2F37),
    error = Color(0xFFFF8A80),
)

val Good = Color(0xFF2F7A4F)
val Warn = Color(0xFFA86414)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
