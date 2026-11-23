package br.mev.gravador

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Pilula(texto: String, modifier: Modifier = Modifier, cor: Color = Cor.texto, fundo: Color = Cor.cartao, borda: Color = Cor.linha, ponto: Color? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(fundo)
            .border(1.dp, borda, RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ponto != null) { Box(Modifier.size(9.dp).clip(CircleShape).background(ponto)); Spacer(Modifier.width(6.dp)) }
        Text(texto, color = cor, fontSize = 13.sp)
    }
}

@Composable
fun Etiqueta(texto: String, cor: Color, fundo: Color, onClick: (() -> Unit)? = null) {
    Text(
        texto, color = cor, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(fundo)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun BotaoAzul(texto: String, modifier: Modifier = Modifier, forte: Boolean = true, onClick: () -> Unit) {
    Text(
        texto, color = if (forte) Cor.escuroBotao else Cor.azul, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(50)).background(if (forte) Cor.azul else Cor.azul2)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun Redondo(simbolo: String, modifier: Modifier = Modifier, tamanho: Dp = 40.dp, forte: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.size(tamanho).clip(CircleShape).background(if (forte) Cor.azul else Cor.cartao)
            .then(if (forte) Modifier else Modifier.border(1.dp, Cor.linha, CircleShape))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(simbolo, color = if (forte) Cor.escuroBotao else Cor.texto, fontSize = 17.sp) }
}

@Composable
fun Cartao(modifier: Modifier = Modifier, borda: Color = Cor.linha, fundo: Color = Cor.cartao, conteudo: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(fundo).border(1.dp, borda, RoundedCornerShape(16.dp))) { conteudo() }
}

@Composable
fun campoCores() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cor.azul, unfocusedBorderColor = Cor.linha, focusedContainerColor = Cor.cartao,
    unfocusedContainerColor = Cor.cartao, cursorColor = Cor.azul, focusedTextColor = Cor.texto, unfocusedTextColor = Cor.texto,
)

@Composable
fun DialogoTexto(titulo: String, inicial: String, onOk: (String) -> Unit, onFechar: () -> Unit) {
    var t by remember { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = Cor.cartao2,
        title = { Text(titulo, color = Cor.texto) },
        text = {
            OutlinedTextField(t, { t = it }, singleLine = true, colors = campoCores(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton({ onOk(t.trim()); onFechar() }) { Text("Salvar", color = Cor.azul) } },
        dismissButton = { TextButton(onFechar) { Text("Cancelar", color = Cor.sub) } },
    )
}

@Composable
fun Titulo(texto: String, sub: String? = null) {
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(texto, color = Cor.texto, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = Cor.sub, fontSize = 13.sp)
    }
}

@Composable
fun Espaco(h: Int) = Spacer(Modifier.height(h.dp))
