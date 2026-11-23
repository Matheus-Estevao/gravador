package br.mev.gravador

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelaLista(
    pastas: List<Pair<Pastinha, List<Gravacao>>>,
    temPasta: Boolean,
    precisaRecordings: Boolean,
    onEscolherPasta: () -> Unit,
    onAbrir: (Gravacao) -> Unit,
    onAjustes: () -> Unit,
    onMudou: () -> Unit,
) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val estados by Tarefas.estados.collectAsState()
    val versao by Banco.versao.collectAsState()
    var busca by remember { mutableStateOf("") }
    var abaNome by rememberSaveable { mutableStateOf("Voice Recorder") }
    val aba = pastas.firstOrNull { it.first.nome == abaNome } ?: pastas.firstOrNull()
    val gravacoes = aba?.second ?: emptyList()
    val sel = remember { mutableStateListOf<String>() }
    val selecionando = sel.isNotEmpty()
    var renomeando by remember { mutableStateOf<Gravacao?>(null) }
    var movendo by remember { mutableStateOf(false) }
    var novaPasta by remember { mutableStateOf(false) }
    var confirmarApagar by remember { mutableStateOf<List<Gravacao>?>(null) }
    val lista = rememberLazyListState()

    LaunchedEffect(abaNome) { sel.clear() }
    // some da seleção o que não existe mais
    LaunchedEffect(gravacoes) { val ex = gravacoes.map { it.chave }.toSet(); sel.retainAll { it in ex } }
    BackHandler(enabled = selecionando) { sel.clear() }

    fun avisar(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()
    fun selecionadas() = gravacoes.filter { it.chave in sel }
    fun emDemo(): Boolean { if (Prefs.demo) avisar("No modo demonstração não mexe nos arquivos"); return Prefs.demo }

    val lixeira = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) { avisar("Foi pra lixeira (dá pra recuperar por 30 dias)"); sel.clear() }
        onMudou()
    }

    fun apagar(gs: List<Gravacao>) {
        if (emDemo()) return
        escopo.launch {
            val uris = withContext(Dispatchers.IO) { if (Build.VERSION.SDK_INT >= 30) Arquivos.paraLixeira(ctx, gs) else null }
            val pedido = if (uris != null && Build.VERSION.SDK_INT >= 30) try {
                MediaStore.createTrashRequest(ctx.contentResolver, uris, true)
            } catch (e: Exception) { null } else null
            if (pedido != null) lixeira.launch(IntentSenderRequest.Builder(pedido.intentSender).build())
            else confirmarApagar = gs
        }
    }

    fun compartilhar(gs: List<Gravacao>) {
        val uris = ArrayList<Uri>(gs.map { it.uri })
        val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        i.type = "audio/*"
        i.clipData = ClipData.newRawUri("", uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, null))
    }

    fun mover(destino: Pastinha) {
        val gs = selecionadas()
        escopo.launch {
            val n = withContext(Dispatchers.IO) { Arquivos.mover(ctx, gs, destino) }
            avisar(if (n == gs.size) "Movido pra ${destino.rotulo.drop(2).trim()}" else "Movi $n de ${gs.size}")
            sel.clear(); onMudou()
        }
    }

    val filtradas = remember(gravacoes, busca, versao) {
        val q = semAcento(busca.trim())
        if (q.isEmpty()) gravacoes else gravacoes.filter { g ->
            val c = Banco.ler(g.chave)
            semAcento(g.nome).contains(q) || (c != null && (semAcento(c.titulo).contains(q) || c.falas.any { semAcento(it.texto).contains(q) }))
        }
    }

    Column(Modifier.fillMaxSize()) {
        // topo: busca + ajustes, ou barra de seleção
        Box(Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp)) {
            if (selecionando) {
                Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Redondo("✕") { sel.clear() }
                    Spacer(Modifier.width(12.dp))
                    Text("${sel.size} ${if (sel.size == 1) "selecionada" else "selecionadas"}", color = Cor.texto, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    val todas = sel.size == filtradas.size
                    Pilula(if (todas) "Nenhuma" else "Todas", cor = Cor.azul) { if (todas) sel.clear() else { sel.clear(); sel.addAll(filtradas.map { it.chave }) } }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Cartao(Modifier.weight(1f)) {
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
                    Spacer(Modifier.width(10.dp))
                    Redondo("⚙", onClick = onAjustes)
                }
            }
        }

        // abas: uma por pasta de Recordings
        if (pastas.size > 1) {
            val poucas = pastas.size <= 2
            Row(
                Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cor.cartao)
                    .border(1.dp, Cor.linha, RoundedCornerShape(14.dp)).then(if (poucas) Modifier else Modifier.horizontalScroll(rememberScrollState())).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pastas.forEach { (p, gs) ->
                    val ativa = p.nome == aba?.first?.nome
                    Box(
                        (if (poucas) Modifier.weight(1f) else Modifier.padding(horizontal = 2.dp)).clip(RoundedCornerShape(11.dp)).background(if (ativa) Cor.azul2 else Color.Transparent)
                            .clickable { abaNome = p.nome; escopo.launch { lista.scrollToItem(0) } }.padding(vertical = 9.dp, horizontal = if (poucas) 0.dp else 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${p.rotulo}  ${gs.size}", color = if (ativa) Cor.texto else Cor.sub, fontSize = 13.5.sp,
                            fontWeight = if (ativa) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = lista,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!temPasta || precisaRecordings) {
                item {
                    Cartao(Modifier.fillMaxWidth(), borda = if (temPasta) Cor.laranjaBorda else Cor.linha) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (temPasta) "Libere a pasta Recordings" else "Libere a pasta das gravações", color = Cor.texto, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                (if (temPasta) "Pra ver as chamadas e poder renomear, mover e apagar. " else "") +
                                    "O seletor já abre em Recordings: é só tocar em \"Usar esta pasta\" e depois em \"Permitir\".",
                                color = Cor.sub, fontSize = 13.5.sp,
                            )
                            BotaoAzul("Liberar Recordings", onClick = onEscolherPasta)
                        }
                    }
                }
            }
            item { PainelAtualizacao(sempre = false) }
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
            if (temPasta && aba != null && gravacoes.isEmpty()) {
                item { Text("Nenhuma gravação nessa pasta.", color = Cor.sub, fontSize = 14.sp, modifier = Modifier.padding(8.dp)) }
            }
            if (busca.isNotBlank() && filtradas.isEmpty() && gravacoes.isNotEmpty()) {
                item { Text("Nada encontrado.", color = Cor.sub, fontSize = 14.sp, modifier = Modifier.padding(8.dp)) }
            }
            items(filtradas, key = { it.chave }) { g ->
                val c = remember(versao, g.chave) { Banco.ler(g.chave) }
                val est = estados[g.chave] ?: Estado.Nada
                var dur by remember(g.chave) { mutableStateOf(c?.duracao?.takeIf { it > 0 } ?: Pasta.duracaoGuardada(g) ?: 0.0) }
                LaunchedEffect(g.chave) { if (dur <= 0 && !Prefs.demo) dur = withContext(Dispatchers.IO) { Pasta.duracao(ctx, g) } }
                if (Prefs.demo && dur <= 0) dur = 350.0
                val marcada = g.chave in sel
                fun alternar() { if (marcada) sel.remove(g.chave) else sel.add(g.chave) }
                Cartao(
                    Modifier.fillMaxWidth().combinedClickable(
                        onClick = { if (selecionando) alternar() else if (c != null) onAbrir(g) },
                        onLongClick = { alternar() },
                    ),
                    borda = if (marcada) Cor.azul else Cor.linha,
                    fundo = if (marcada) Cor.azul2 else Cor.cartao,
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (selecionando) {
                            Box(
                                Modifier.size(22.dp).clip(CircleShape).background(if (marcada) Cor.azul else Color.Transparent)
                                    .border(2.dp, if (marcada) Cor.azul else Cor.sub, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { if (marcada) Text("✓", color = Cor.escuroBotao, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            val titulo = c?.titulo?.takeIf { it.isNotBlank() && it != g.titulo }
                            Text(titulo ?: g.exibicao, color = Cor.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = buildString {
                                append(diaCurto(g.quando)); if (dur > 0) append(" · ").append(duracaoCurta(dur))
                                if (titulo != null) append(" · ").append(g.exibicao)
                            }
                            Text(sub, color = Cor.sub, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (est is Estado.Erro) Text(est.msg, color = Cor.vermelho, fontSize = 12.sp, maxLines = 2)
                        }
                        if (!selecionando) {
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

        // barra de ações da seleção
        if (selecionando) {
            Row(
                Modifier.fillMaxWidth().background(Cor.cartao2).border(1.dp, Cor.linha).padding(vertical = 8.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Acao("✎", "Renomear", ativo = sel.size == 1) { if (!emDemo()) renomeando = selecionadas().firstOrNull() }
                Acao("⇄", "Mover") { if (!emDemo()) movendo = true }
                Acao("⇪", "Compartilhar") { compartilhar(selecionadas()) }
                Acao("🗑", "Apagar", cor = Cor.vermelho) { apagar(selecionadas()) }
            }
        }
    }

    renomeando?.let { g ->
        DialogoTexto("Renomear", g.titulo, onOk = { novo ->
            escopo.launch {
                val r = withContext(Dispatchers.IO) { try { Arquivos.renomear(ctx, g, novo) } catch (e: Exception) { null } }
                if (r == null && novo != g.titulo) avisar("Não deu pra renomear") else if (r != null) avisar("Renomeado")
                sel.clear(); onMudou()
            }
        }, dica = "O arquivo muda de nome na pasta. A transcrição continua junto.") { renomeando = null }
    }

    if (movendo) {
        val atual = aba?.first
        AlertDialog(
            onDismissRequest = { movendo = false },
            containerColor = Cor.cartao2,
            title = { Text("Mover ${sel.size} pra…", color = Cor.texto) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pastas.map { it.first }.filter { it.uri != atual?.uri }.forEach { p ->
                        Pilula(p.rotulo, Modifier.fillMaxWidth()) { movendo = false; mover(p) }
                    }
                    Pilula("＋ Nova pasta em Recordings", Modifier.fillMaxWidth(), cor = Cor.azul) { movendo = false; novaPasta = true }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ movendo = false }) { Text("Cancelar", color = Cor.sub) } },
        )
    }

    if (novaPasta) {
        DialogoTexto("Nova pasta", "", onOk = { nome ->
            if (nome.isNotBlank()) escopo.launch {
                val p = withContext(Dispatchers.IO) { Arquivos.novaPasta(ctx, nome) }
                if (p == null) avisar("Não deu pra criar a pasta") else mover(p)
            }
        }, dica = "Fica dentro de Recordings, ao lado de Call e Voice Recorder.") { novaPasta = false }
    }

    confirmarApagar?.let { gs ->
        AlertDialog(
            onDismissRequest = { confirmarApagar = null },
            containerColor = Cor.cartao2,
            title = { Text("Apagar ${gs.size} ${if (gs.size == 1) "gravação" else "gravações"}?", color = Cor.texto) },
            text = { Text("Esse arquivo não está na lixeira do Android, então some de vez da pasta.", color = Cor.sub) },
            confirmButton = {
                TextButton({
                    confirmarApagar = null
                    escopo.launch {
                        val n = withContext(Dispatchers.IO) { Arquivos.apagarDeVez(ctx, gs) }
                        avisar("Apaguei $n"); sel.clear(); onMudou()
                    }
                }) { Text("Apagar", color = Cor.vermelho) }
            },
            dismissButton = { TextButton({ confirmarApagar = null }) { Text("Cancelar", color = Cor.sub) } },
        )
    }
}

@Composable
private fun Acao(icone: String, texto: String, ativo: Boolean = true, cor: Color = Cor.texto, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = ativo, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val c = if (ativo) cor else Cor.sub.copy(alpha = 0.45f)
        Text(icone, color = c, fontSize = 20.sp)
        Text(texto, color = c, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
