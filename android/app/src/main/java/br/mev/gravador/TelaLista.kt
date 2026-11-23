package br.mev.gravador

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TelaLista(
    gravacoes: List<Gravacao>,
    temPasta: Boolean,
    onEscolherPasta: () -> Unit,
    onAbrir: (Gravacao) -> Unit,
    onAjustes: () -> Unit,
) {
    val ctx = LocalContext.current
    val estados by Tarefas.estados.collectAsState()
    val versao by Banco.versao.collectAsState()
    var busca by remember { mutableStateOf("") }
    val duracoes = remember { mutableStateMapOf<String, Double>() }

    LaunchedEffect(gravacoes) {
        withContext(Dispatchers.IO) {
            gravacoes.take(80).forEach { g -> if (g.chave !in duracoes) duracoes[g.chave] = Pasta.duracao(ctx, g) }
        }
    }

    val filtradas = remember(gravacoes, busca, versao) {
        val q = semAcento(busca.trim())
        if (q.isEmpty()) gravacoes else gravacoes.filter { g ->
            val c = Banco.ler(g.chave)
            semAcento(g.nome).contains(q) || (c != null && (semAcento(c.titulo).contains(q) || c.falas.any { semAcento(it.texto).contains(q) }))
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(50)).padding(0.dp)
                ) {
                    Cartao(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("⌕", color = Cor.sub, fontSize = 15.sp); Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f)) {
                                if (busca.isEmpty()) Text("Buscar nos textos", color = Cor.sub, fontSize = 14.sp)
                                BasicTextField(busca, { busca = it }, singleLine = true, textStyle = TextStyle(color = Cor.texto, fontSize = 14.sp),
                                    cursorBrush = SolidColor(Cor.azul), modifier = Modifier.fillMaxWidth())
                            }
                            if (busca.isNotEmpty()) Text("✕", color = Cor.sub, modifier = Modifier.clickable { busca = "" }.padding(start = 6.dp))
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Redondo("⚙", onClick = onAjustes)
            }
        }
        item {
            Espaco(14)
            Titulo("Gravador", if (temPasta) "${Pasta.nomePasta().ifEmpty { "Recordings/Voice Recorder" }} · mais recentes primeiro" else null)
            Espaco(6)
        }
        if (!temPasta) {
            item {
                Cartao(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Libere a pasta do gravador", color = Cor.texto, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("É uma vez só. O seletor já abre em Recordings › Voice Recorder: é só tocar em \"Usar esta pasta\" e depois em \"Permitir\".", color = Cor.sub, fontSize = 13.5.sp)
                        BotaoAzul("Escolher pasta", onClick = onEscolherPasta)
                    }
                }
            }
        }
        if (temPasta && Prefs.chave.isBlank() && !Prefs.demo) {
            item {
                Cartao(Modifier.fillMaxWidth(), borda = Cor.laranjaBorda) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Falta colar a chave da Mistral", color = Cor.laranja, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                        BotaoAzul("Ajustes", onClick = onAjustes)
                    }
                }
            }
        }
        if (temPasta && gravacoes.isEmpty()) {
            item { Text("Nenhuma gravação nessa pasta.", color = Cor.sub, fontSize = 14.sp, modifier = Modifier.padding(8.dp)) }
        }
        if (busca.isNotBlank() && filtradas.isEmpty() && gravacoes.isNotEmpty()) {
            item { Text("Nada encontrado.", color = Cor.sub, fontSize = 14.sp, modifier = Modifier.padding(8.dp)) }
        }
        items(filtradas, key = { it.chave }) { g ->
            val c = remember(versao, g.chave) { Banco.ler(g.chave) }
            val est = estados[g.chave] ?: Estado.Nada
            val dur = c?.duracao?.takeIf { it > 0 } ?: duracoes[g.chave] ?: 0.0
            Cartao(Modifier.fillMaxWidth().clickable(enabled = c != null) { onAbrir(g) }) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val titulo = c?.titulo?.takeIf { it.isNotBlank() && it != g.titulo }
                        Text(titulo ?: g.titulo, color = Cor.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = buildString {
                            append(diaCurto(g.quando)); if (dur > 0) append(" · ").append(duracaoCurta(dur))
                            if (titulo != null) append(" · ").append(g.titulo)
                        }
                        Text(sub, color = Cor.sub, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (est is Estado.Erro) Text(est.msg, color = Cor.vermelho, fontSize = 12.sp, maxLines = 2)
                    }
                    Spacer(Modifier.width(10.dp))
                    when {
                        est is Estado.Transcrevendo -> Etiqueta("transcrevendo…", Cor.amarelo, Cor.amarelo2)
                        c != null -> Etiqueta("pronto · ${c.pessoas.size} ${if (c.pessoas.size == 1) "voz" else "vozes"}", Cor.verde, Cor.verde2)
                        est is Estado.Erro -> BotaoAzul("Tentar de novo", forte = false) { Tarefas.transcrever(ctx, g) }
                        else -> BotaoAzul("Transcrever", forte = false) { Tarefas.transcrever(ctx, g) }
                    }
                }
            }
        }
    }
}
