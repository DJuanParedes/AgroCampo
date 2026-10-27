package pe.upeu.agrosierra.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colores = lightColorScheme(primary = Color(0xFF245B3B), secondary = Color(0xFF766435),
    tertiary = Color(0xFF3B6570), background = Color(0xFFF7F8F2), surface = Color(0xFFF7F8F2),
    primaryContainer = Color(0xFFD4EBD6))
@Composable fun AgroTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = colores, content = content) }
