package br.mev.gravador

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ícones de traço (desenhados aqui, sem emoji). Caminhos no padrão SVG 24x24. */
object Icones {
    private fun c(cx: Float, cy: Float, r: Float) = "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"
    private fun icone(nome: String, vararg caminhos: String): ImageVector {
        val b = ImageVector.Builder(nome, 24.dp, 24.dp, 24f, 24f)
        caminhos.forEach {
            b.addPath(addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
        return b.build()
    }
    val mic = icone("mic", "M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z", "M5 11a7 7 0 0 0 14 0M12 18v3")
    val fone = icone("fone", "M5 4h3.5l1.5 4-2 1.3a11 11 0 0 0 6.7 6.7L16 14l4 1.5V19a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z")
    val pasta = icone("pasta", "M3.5 7a2 2 0 0 1 2-2h4l2 2.5h7a2 2 0 0 1 2 2V17a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z")
    val busca = icone("busca", c(11f, 11f, 6.5f), "M20 20l-4.2-4.2")
    val filtro = icone("filtro", "M4 6h16M7 12h10M10 18h4")
    val ajustes = icone("ajustes", c(12f, 12f, 3f), c(12f, 12f, 6.5f),
        "M12 2.5v2.2M12 19.3v2.2M4.6 4.6l1.6 1.6M17.8 17.8l1.6 1.6M2.5 12h2.2M19.3 12h2.2M4.6 19.4l1.6-1.6M17.8 6.2l1.6-1.6")
    val calendario = icone("calendario", "M6 5h12a2.5 2.5 0 0 1 2.5 2.5v10A2.5 2.5 0 0 1 18 20H6a2.5 2.5 0 0 1-2.5-2.5v-10A2.5 2.5 0 0 1 6 5z", "M3.5 10h17M8 3v4M16 3v4")
    val relogio = icone("relogio", c(12f, 13f, 8f), "M12 9v4l2.5 2M9.5 2.5h5")
    val pessoa = icone("pessoa", c(12f, 8f, 4f), "M4.5 20a7.5 7.5 0 0 1 15 0")
    val fechar = icone("fechar", "M7 7l10 10M17 7L7 17")
    val lapis = icone("lapis", "M4 20h4L19 9a2.1 2.1 0 0 0-4-4L4 16z", "M13.5 6.5l4 4")
    val mover = icone("mover", "M3.5 7a2 2 0 0 1 2-2h4l2 2.5h7a2 2 0 0 1 2 2V17a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z", "M9 13h6M13 10.5l2.5 2.5-2.5 2.5")
    val compartilhar = icone("compartilhar", "M12 3v12M7.5 7.5L12 3l4.5 4.5", "M5 12v6.5A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5V12")
    val lixo = icone("lixo", "M4 7h16M9.5 7V4.5h5V7M6 7l1 12.5A1.5 1.5 0 0 0 8.5 21h7a1.5 1.5 0 0 0 1.5-1.5L18 7", "M10 11v6M14 11v6")
}

@Composable
fun Ic(v: ImageVector, cor: Color, tamanho: Dp = 18.dp, modifier: Modifier = Modifier) =
    Icon(v, contentDescription = null, tint = cor, modifier = modifier.size(tamanho))
