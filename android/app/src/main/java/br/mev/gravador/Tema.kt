package br.mev.gravador

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Cor {
    val fundo = Color(0xFF0B0F14)
    val cartao = Color(0xFF151C24)
    val cartao2 = Color(0xFF1B242E)
    val linha = Color(0xFF25303C)
    val texto = Color(0xFFE9EEF4)
    val texto2 = Color(0xFFD7DEE7)
    val sub = Color(0xFF8F9CAD)
    val azul = Color(0xFF4AA3FF)
    val azul2 = Color(0xFF1D3348)
    val verde = Color(0xFF2ECC71)
    val verde2 = Color(0xFF14301F)
    val amarelo = Color(0xFFFFC75A)
    val amarelo2 = Color(0xFF3A2E10)
    val laranja = Color(0xFFFFB066)
    val laranjaBorda = Color(0xFF5A3A20)
    val vermelho = Color(0xFFFF6B6B)
    val escuroBotao = Color(0xFF06121F)
    /** Cor fixa de cada pessoa, na ordem em que aparecem. */
    val vozes = listOf(Color(0xFF4AA3FF), Color(0xFFB48CFF), Color(0xFFFFC75A), Color(0xFF2ECC71), Color(0xFFFF6B8B), Color(0xFF5CE1E6), Color(0xFFFF9A3C), Color(0xFFC9D86A))
}

@Composable
fun Tema(conteudo: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cor.azul, onPrimary = Cor.escuroBotao, background = Cor.fundo, surface = Cor.cartao,
            onSurface = Cor.texto, onBackground = Cor.texto, surfaceVariant = Cor.cartao2, outline = Cor.linha,
            surfaceContainerHigh = Color(0xFF1F2228), surfaceContainerLow = Cor.cartao,
        ),
        content = conteudo,
    )
}
