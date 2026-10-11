package ai.jarvis.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** The user's setting is supplied at the top-level and applied throughout navigation. */
internal val LocalJarvisDark = compositionLocalOf { false }

internal object JarvisPalette {
    val background: Color @Composable get() =
        if (LocalJarvisDark.current) Color(0xFF080E19) else Color(0xFFF7FAFF)
    val panel: Color @Composable get() =
        if (LocalJarvisDark.current) Color(0xFF182334) else Color.White
    val mainText: Color @Composable get() =
        if (LocalJarvisDark.current) Color(0xFFF1F5FA) else Color(0xFF08142E)
    val subtleText: Color @Composable get() =
        if (LocalJarvisDark.current) Color(0xFFB5C3D8) else Color(0xFF596981)
    val accent: Color @Composable get() =
        if (LocalJarvisDark.current) Color(0xFF60D5E5) else Color(0xFF075FF1)
}
