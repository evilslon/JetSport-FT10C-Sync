package com.jetsport.ft10csync.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF245B78), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EAF4), onPrimaryContainer = Color(0xFF0B3044),
    secondary = Color(0xFF52636D), background = Color(0xFFF4F6F8),
    surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE8EDF1),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CCBE0), onPrimary = Color(0xFF003548),
    primaryContainer = Color(0xFF164B62), onPrimaryContainer = Color(0xFFD5EAF4),
    secondary = Color(0xFFB7C9D2), background = Color(0xFF10171B),
    surface = Color(0xFF182126), surfaceVariant = Color(0xFF29343B),
    error = Color(0xFFFFB4AB)
)

private val JetSportShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun JetSportTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = JetSportShapes,
        content = content
    )
}
