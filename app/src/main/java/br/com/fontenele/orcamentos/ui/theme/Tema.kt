package br.com.fontenele.orcamentos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.fontenele.orcamentos.R

/** Cores tiradas do logo e do site editorafontenele.com.br. */
object Cores {
    val Marinho = Color(0xFF272F42)     // títulos, botões, bordas e valores
    val Amarelo = Color(0xFFF5BA1A)     // amarelo do logo
    val Grafite = Color(0xFF3C3C3C)     // textos
    val Cinza = Color(0xFF5E6068)       // textos secundários
    val Linha = Color(0xFFD3D6DD)       // caixas das listas e divisores
    val CinzaClaro = Color(0xFFF5F5F5)
    val CinzaFundo = Color(0xFFEFEFEF)
}

/**
 * Fonte do app inteiro: Arimo, desenhada com as mesmas medidas da Arial (a Arial não pode ser
 * distribuída dentro do app; a Arimo tem licença livre SIL OFL, arquivo na raiz do projeto).
 */
val FonteArial = FontFamily(
    Font(R.font.arimo_regular, FontWeight.Normal),
    Font(R.font.arimo_bold, FontWeight.Bold),
)

private val Claro = lightColorScheme(
    primary = Cores.Marinho,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E7EE),
    onPrimaryContainer = Cores.Marinho,
    secondary = Cores.Amarelo,
    onSecondary = Cores.Marinho,
    secondaryContainer = Color(0xFFFFF0C4),
    onSecondaryContainer = Cores.Marinho,
    tertiary = Cores.Cinza,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4E7EE),
    onTertiaryContainer = Cores.Marinho,
    background = Color.White,
    onBackground = Cores.Grafite,
    surface = Color.White,
    onSurface = Cores.Grafite,
    surfaceVariant = Cores.CinzaFundo,
    onSurfaceVariant = Cores.Cinza,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Cores.CinzaClaro,
    surfaceContainer = Cores.CinzaFundo,
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE2E2E2),
    outline = Color(0xFF8E9099),
    outlineVariant = Cores.Linha,
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E7),
    onErrorContainer = Color(0xFF7F1D1D),
)

private val Escuro = darkColorScheme(
    primary = Color(0xFFC9D1E3),
    onPrimary = Cores.Marinho,
    primaryContainer = Color(0xFF39445C),
    onPrimaryContainer = Color.White,
    secondary = Cores.Amarelo,
    onSecondary = Cores.Marinho,
    secondaryContainer = Color(0xFF5A4810),
    onSecondaryContainer = Color(0xFFFFF0C4),
    tertiary = Color(0xFFC3C6CF),
    onTertiary = Cores.Marinho,
    tertiaryContainer = Color(0xFF39445C),
    onTertiaryContainer = Color.White,
    background = Color(0xFF161B27),
    onBackground = Color(0xFFE8E9ED),
    surface = Color(0xFF161B27),
    onSurface = Color(0xFFE8E9ED),
    surfaceVariant = Color(0xFF2C3446),
    onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainerLowest = Color(0xFF11151F),
    surfaceContainerLow = Color(0xFF1E2433),
    surfaceContainer = Color(0xFF232A3A),
    surfaceContainerHigh = Color(0xFF2B3344),
    surfaceContainerHighest = Color(0xFF343C4F),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF3B4357),
)

private val base = Typography()

/** Tudo em Arial (Arimo): títulos em negrito, textos normais. Sem fonte decorativa. */
private val Tipografia = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = FonteArial),
    displayMedium = base.displayMedium.copy(fontFamily = FonteArial),
    displaySmall = base.displaySmall.copy(fontFamily = FonteArial, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = FonteArial, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = FonteArial, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = FonteArial, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = FonteArial, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = base.titleMedium.copy(fontFamily = FonteArial, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    titleSmall = base.titleSmall.copy(fontFamily = FonteArial, fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.copy(fontFamily = FonteArial),
    bodyMedium = base.bodyMedium.copy(fontFamily = FonteArial),
    bodySmall = base.bodySmall.copy(fontFamily = FonteArial),
    labelLarge = base.labelLarge.copy(fontFamily = FonteArial, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    labelMedium = base.labelMedium.copy(fontFamily = FonteArial),
    labelSmall = base.labelSmall.copy(fontFamily = FonteArial, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
)

/** Cantos quase retos, como os botões e blocos do site. */
private val Formas = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(3.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(6.dp),
)

@Composable
fun TemaFontenele(escuro: Boolean = isSystemInDarkTheme(), conteudo: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (escuro) Escuro else Claro,
        typography = Tipografia,
        shapes = Formas,
        content = conteudo,
    )
}
