package br.mev.gravador

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun corDe(c: Conversa, quem: String): Color {
    val i = c.pessoas.indexOf(quem)
    return Cor.vozes[(if (i < 0) c.pessoas.size else i) % Cor.vozes.size]
}

/** Estado do arraste da linha do áudio. */
class Cabeca { var arrastando by mutableStateOf(false); var posArraste by mutableStateOf(0.0) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaConversa(
    chave: String,
    tocador: Tocador,
    abrirCompartilhar: Boolean = false,
    onVoltar: () -> Unit,
    onEditar: (List<Int>) -> Unit,
) {
    val ctx = LocalContext.current
    val versao by Banco.versao.collectAsState()
    val bruta = remember(versao, chave) { Banco.ler(chave) }
    if (bruta == null) { LaunchedEffect(Unit) { onVoltar() }; return }
    val c = remember(bruta) { bruta.unificada() }
    val itens = remember(c) { turnos(blocos(c.falas)) }
    val cabeca = remember { Cabeca() }
    var renomearTitulo by remember { mutableStateOf(false) }
    var renomearPessoa by remember { mutableStateOf<String?>(null) }
    var compartilhar by remember { mutableStateOf(abrirCompartilhar) }

    LaunchedEffect(c.uri) { tocador.carregar(c.uri) }
    LaunchedEffect(tocador.tocando) { while (tocador.tocando) { withFrameNanos { }; tocador.atualizar() } }

    val duracao = if (tocador.duracao > 0) tocador.duracao else c.duracao
    val pos = if (cabeca.arrastando) cabeca.posArraste else tocador.posicao

    // rolagem automática acompanhando o áudio (para por 3,5 s se você rolar com o dedo)
    val lista = rememberLazyListState()
    var dedoEm by remember { mutableLongStateOf(0L) }
    var auto by remember { mutableStateOf(false) }
    LaunchedEffect(lista) { snapshotFlow { lista.isScrollInProgress }.collect { if (it && !auto) dedoEm = System.currentTimeMillis() } }
    val alvo = remember(pos, itens) { itens.indexOfFirst { it.fim > pos }.let { if (it < 0) itens.lastIndex else it } }
    val acompanhar = tocador.tocando || cabeca.arrastando
    LaunchedEffect(alvo, acompanhar) {
        if (!acompanhar || alvo < 0) return@LaunchedEffect
        if (System.currentTimeMillis() - dedoEm < 3500 && !cabeca.arrastando) return@LaunchedEffect
        val info = lista.layoutInfo
        val v = info.visibleItemsInfo.firstOrNull { it.index == alvo }
        // mantém a fala atual no terço de cima da tela
        val limite = info.viewportStartOffset + (info.viewportEndOffset - info.viewportStartOffset) / 3
        if (v == null || v.offset > limite || v.offset + v.size < info.viewportStartOffset + 40) {
            auto = true
            try { if (cabeca.arrastando) lista.scrollToItem(alvo, -40) else lista.animateScrollToItem(alvo, -40) } finally { auto = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // cabeçalho compacto
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Redondo("‹", tamanho = 38.dp, onClick = onVoltar)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp).clickable { renomearTitulo = true }) {
                Text(c.titulo.ifBlank { c.arquivo }, color = Cor.texto, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${diaCurto(c.quando)} · ${duracaoCurta(duracao)} · ${c.pessoas.size} ${if (c.pessoas.size == 1) "voz" else "vozes"}", color = Cor.sub, fontSize = 12.sp, maxLines = 1)
            }
            Redondo("⇪", tamanho = 38.dp, forte = true) { compartilhar = true }
        }
        Espaco(10)
        Box(Modifier.padding(horizontal = 12.dp)) { Tocadorzinho(c, tocador, cabeca, duracao, pos) }
        Espaco(8)
        // pessoas numa linha só, rolando pro lado
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(c.pessoas) { p ->
                val falando = c.falas.any { it.quem == p && pos >= it.ini && pos < it.fim }
                Pilula(c.nome(p), ponto = corDe(c, p), borda = if (falando) corDe(c, p) else Cor.linha) { renomearPessoa = p }
            }
            item { Pilula("✎ nomes", cor = Cor.sub, fundo = Color.Transparent) { renomearPessoa = c.pessoas.firstOrNull { c.nomes[it].isNullOrBlank() } ?: c.pessoas.firstOrNull() } }
        }
        Espaco(6)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = lista,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(itens, key = { b -> b.falas.first().id }) { b ->
                when (b) {
                    is Turno -> TurnoView(c, b, pos, { tocador.irPara(it) }) { onEditar(b.falas.map { it.id }) }
                    is Bloco.Juntas -> BlocoJuntas(c, b, pos, { tocador.irPara(it) }) { onEditar(b.falas.map { it.id }) }
                    is Bloco.Uma -> TurnoView(c, Turno(b.fala.quem, listOf(b.fala)), pos, { tocador.irPara(it) }) { onEditar(listOf(b.fala.id)) }
                }
            }
            if (c.falas.isEmpty()) item { Text("A Mistral não achou fala nessa gravação.", color = Cor.sub, fontSize = 14.sp) }
        }
    }

    if (renomearTitulo) DialogoTexto("Nome da conversa", c.titulo, { if (it.isNotBlank()) Banco.salvar(bruta.copy(titulo = it)) }) { renomearTitulo = false }
    renomearPessoa?.let { p ->
        DialogoTexto("Quem é ${c.nome(p)}?", c.nomes[p] ?: "", { Banco.salvar(bruta.copy(nomes = bruta.nomes + (p to it))) }, dica = "Dica: dar o mesmo nome a duas pessoas junta as duas numa só.") { renomearPessoa = null }
    }
    if (compartilhar) {
        ModalBottomSheet(onDismissRequest = { compartilhar = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF1F2228)) {
            Compartilhar(ctx, bruta) { compartilhar = false }
        }
    }
}

private const val JANELA = 60.0

@Composable
fun Tocadorzinho(c: Conversa, tocador: Tocador, cabeca: Cabeca, duracao: Double, pos: Double) {
    Cartao(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
            Faixas(c, cabeca, duracao, pos, Modifier.fillMaxWidth()) { tocador.irPara(it) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(hora(pos), color = Cor.texto, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(" / ${hora(duracao)}", color = Cor.sub, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Pulo("↺5") { tocador.irPara((tocador.posicao - 5).coerceAtLeast(0.0)) }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Cor.azul).clickable { tocador.alternar() },
                    contentAlignment = Alignment.Center,
                ) { Text(if (tocador.tocando) "❚❚" else "▶", color = Cor.escuroBotao, fontSize = if (tocador.tocando) 15.sp else 18.sp) }
                Spacer(Modifier.width(8.dp))
                Pulo("5↻") { tocador.irPara((tocador.posicao + 5).coerceAtMost(duracao)) }
                Spacer(Modifier.weight(1f))
                val vs = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
                Pilula(("%.2f".format(java.util.Locale.US, tocador.velocidade)).trimEnd('0').trimEnd('.').replace('.', ',') + "x") {
                    tocador.mudarVelocidade(vs[(vs.indexOf(tocador.velocidade) + 1) % vs.size])
                }
            }
        }
    }
}

@Composable
private fun Pulo(t: String, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(t, color = Cor.sub, fontSize = 13.sp)
    }
}

/**
 * Uma faixa por pessoa. Em gravação longa mostra só 1 minuto em volta da linha (dá pra ver as palavras de perto),
 * com a gravação inteira numa faixa fininha embaixo. Tocar ou arrastar move a linha e o texto vai junto.
 */
@Composable
fun Faixas(c: Conversa, cabeca: Cabeca, duracao: Double, pos: Double, modifier: Modifier = Modifier, corFn: (String) -> Color = { corDe(c, it) }, irPara: (Double) -> Unit) {
    val pessoas = c.pessoas.ifEmpty { listOf("speaker_1") }
    val dens = LocalDensity.current
    val n = pessoas.size
    val altFaixa = when { n <= 3 -> 10.dp; n <= 5 -> 8.dp; else -> 6.dp }
    val vao = if (n <= 5) 5.dp else 4.dp
    val zoom = duracao > JANELA * 1.5
    var jIni by remember { mutableDoubleStateOf(0.0) }
    val jTam = if (zoom) JANELA else duracao.coerceAtLeast(1.0)
    // a janela anda junto quando a linha chega perto da borda
    if (zoom && !cabeca.arrastando) {
        if (pos > jIni + jTam * 0.85 || pos < jIni) jIni = (pos - jTam * 0.15).coerceIn(0.0, (duracao - jTam).coerceAtLeast(0.0))
    } else if (!zoom) jIni = 0.0
    var ultimoSeek by remember { mutableLongStateOf(0L) }
    fun seek(t: Double, forcar: Boolean = false) {
        val agora = System.currentTimeMillis()
        if (forcar || agora - ultimoSeek > 50) { ultimoSeek = agora; irPara(t) }
    }
    val alt = (altFaixa + vao) * n + 6.dp
    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(alt)
                .pointerInput(duracao, zoom) {
                    detectTapGestures { o -> seek(jIni + (o.x / size.width).coerceIn(0f, 1f) * jTam, true) }
                }
                .pointerInput(duracao, zoom) {
                    detectDragGestures(
                        onDragStart = { o -> cabeca.arrastando = true; cabeca.posArraste = jIni + (o.x / size.width).coerceIn(0f, 1f) * jTam },
                        onDragEnd = { seek(cabeca.posArraste, true); cabeca.arrastando = false },
                        onDragCancel = { seek(cabeca.posArraste, true); cabeca.arrastando = false },
                    ) { ch, _ ->
                        cabeca.posArraste = (jIni + (ch.position.x / size.width).coerceIn(0f, 1f) * jTam).coerceIn(0.0, duracao)
                        seek(cabeca.posArraste)
                    }
                }
        ) {
            val w = size.width
            val fh = with(dens) { altFaixa.toPx() }; val g = with(dens) { vao.toPx() }
            val raio = CornerRadius(fh / 2, fh / 2)
            fun x(t: Double) = ((t - jIni) / jTam * w).toFloat()
            pessoas.forEachIndexed { i, p ->
                val y = 3f + i * (fh + g)
                drawRoundRect(Color(0x12FFFFFF), Offset(0f, y), Size(w, fh), raio)
                val cor = corFn(p)
                c.falas.forEach { f ->
                    if (f.quem != p || f.fim < jIni || f.ini > jIni + jTam) return@forEach
                    val x0 = x(f.ini).coerceAtLeast(0f); val x1 = x(f.fim).coerceAtMost(w).coerceAtLeast(x0 + fh * 0.7f)
                    val agora = pos >= f.ini && pos < f.fim
                    drawRoundRect(cor.copy(alpha = if (agora) 1f else if (f.fim <= pos) 0.8f else 0.4f), Offset(x0, y), Size(x1 - x0, fh), raio)
                    if (agora) drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(x0, y), Size(x1 - x0, fh), raio, style = Stroke(1.5f))
                }
            }
            val px = x(pos).coerceIn(0f, w)
            drawRect(Cor.texto, Offset(px - 1.5f, 0f), Size(3f, size.height))
            drawCircle(Cor.texto, if (cabeca.arrastando) 10f else 6f, Offset(px, 3f))
        }
        if (zoom) {
            Espaco(8)
            // visão geral: a gravação inteira; tocar ou arrastar pula direto
            Canvas(
                Modifier.fillMaxWidth().height(14.dp)
                    .pointerInput(duracao) { detectTapGestures { o -> seek((o.x / size.width).coerceIn(0f, 1f) * duracao, true) } }
                    .pointerInput(duracao) {
                        detectDragGestures(
                            onDragStart = { o -> cabeca.arrastando = true; cabeca.posArraste = (o.x / size.width).coerceIn(0f, 1f) * duracao },
                            onDragEnd = { seek(cabeca.posArraste, true); cabeca.arrastando = false; jIni = (cabeca.posArraste - jTam * 0.15).coerceIn(0.0, (duracao - jTam).coerceAtLeast(0.0)) },
                            onDragCancel = { seek(cabeca.posArraste, true); cabeca.arrastando = false },
                        ) { ch, _ ->
                            cabeca.posArraste = (ch.position.x / size.width).coerceIn(0f, 1f) * duracao
                            jIni = (cabeca.posArraste - jTam * 0.15).coerceIn(0.0, (duracao - jTam).coerceAtLeast(0.0))
                            seek(cabeca.posArraste)
                        }
                    }
            ) {
                val w = size.width; val h = size.height
                val y = h / 2 - 2f
                drawRoundRect(Color(0x12FFFFFF), Offset(0f, y), Size(w, 4f), CornerRadius(2f, 2f))
                c.falas.forEach { f ->
                    val x0 = (f.ini / duracao * w).toFloat(); val x1 = (f.fim / duracao * w).toFloat().coerceAtLeast(x0 + 2f)
                    drawRect(corFn(f.quem).copy(alpha = 0.7f), Offset(x0, y), Size(x1 - x0, 4f))
                }
                val a = (jIni / duracao * w).toFloat(); val b = ((jIni + jTam) / duracao * w).toFloat()
                drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(a, 0f), Size(b - a, h), CornerRadius(4f, 4f))
                drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(a, 0f), Size(b - a, h), CornerRadius(4f, 4f), style = Stroke(1.5f))
                val px = (pos / duracao * w).toFloat()
                drawRect(Cor.texto, Offset(px - 1f, 0f), Size(2f, h))
            }
        }
    }
}

/** Texto em que cada palavra é tocável e a palavra que está tocando fica marcada. */
@Composable
fun TextoPalavras(f: Fala, cor: Color, pos: Double, irPara: (Double) -> Unit, tamanho: Float = 15.5f) {
    val palavras = remember(f) { f.palavras }
    val tempos = remember(f) { f.tempos() }
    val ativa = if (pos >= f.ini && pos < f.fim) tempos.indexOfLast { it <= pos } else -1
    val inicios = remember(palavras) { var acc = 0; palavras.map { w -> val s = acc; acc += w.length + 1; s } }
    val texto = buildAnnotatedString {
        palavras.forEachIndexed { i, w ->
            if (i == ativa) withStyle(SpanStyle(background = cor.copy(alpha = 0.35f), color = Color.White)) { append(w) } else append(w)
            if (i < palavras.lastIndex) append(' ')
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val falando = pos >= f.ini && pos < f.fim
    Text(
        texto, color = if (falando) Cor.texto else Cor.texto2, fontSize = tamanho.sp, lineHeight = (tamanho * 1.45f).sp,
        onTextLayout = { layout = it },
        modifier = Modifier.pointerInput(f) {
            detectTapGestures { o ->
                val l = layout ?: return@detectTapGestures
                val off = l.getOffsetForPosition(o)
                val i = inicios.indexOfLast { it <= off }.coerceAtLeast(0)
                tempos.getOrNull(i)?.let(irPara)
            }
        },
    )
}

/** Falas seguidas de uma pessoa: nome em cima, texto embaixo usando a largura toda. */
@Composable
fun TurnoView(c: Conversa, t: Turno, pos: Double, irPara: (Double) -> Unit, onEditar: () -> Unit) {
    val cor = corDe(c, t.quem)
    val atual = pos >= t.ini && pos < t.fim
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(cor.copy(alpha = if (atual) 1f else 0.55f)))
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                .background(if (atual) Cor.cartao else Color.Transparent)
                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.nome(t.quem), color = cor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { irPara(t.ini) })
                Text("  ${hora(t.ini)}", color = Cor.sub, fontSize = 12.sp, modifier = Modifier.clickable { irPara(t.ini) })
                if (t.falas.any { it.revisado || it.manual }) Text("  · editado", color = Cor.sub, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text("✎", color = Cor.sub, fontSize = 14.sp, modifier = Modifier.clip(CircleShape).clickable { onEditar() }.padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                t.falas.forEach { f -> TextoPalavras(f, cor, pos, irPara) }
            }
        }
    }
}

@Composable
fun BlocoJuntas(c: Conversa, b: Bloco.Juntas, pos: Double, irPara: (Double) -> Unit, onEditar: () -> Unit) {
    val atual = pos >= b.ini && pos < b.fim
    Cartao(Modifier.fillMaxWidth(), borda = if (atual) Cor.laranja else Cor.laranjaBorda, fundo = Color(0x12FF9A3C)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚡ falando junto · ${hora(b.ini)}–${hora(b.fim)}", color = Cor.laranja, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (b.revisado) Etiqueta("revisado", Cor.verde, Cor.verde2)
            }
            b.falas.forEach { f ->
                val cor = corDe(c, f.quem)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(cor))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("${c.nome(f.quem)}  ${hora(f.ini)}", color = cor, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { irPara(f.ini) })
                        TextoPalavras(f, cor, pos, irPara, 15f)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!b.revisado) Text("pode faltar fala de alguém", color = Cor.sub, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
                Pilula("✎ editar trecho", cor = Cor.laranja, borda = Cor.laranjaBorda, fundo = Color.Transparent) { onEditar() }
            }
        }
    }
}

@Composable
fun Compartilhar(ctx: Context, c: Conversa, fechar: () -> Unit) {
    val escopo = rememberCoroutineScope()
    var resumindo by remember { mutableStateOf(false) }
    fun enviarTexto(t: String, assunto: String) {
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, t); putExtra(Intent.EXTRA_SUBJECT, assunto) }
        ctx.startActivity(Intent.createChooser(i, "Compartilhar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 18.dp).navigationBarsPadding()) {
        Text("Compartilhar", color = Cor.texto, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Espaco(6)
        Opcao("¶", "Texto com nomes", "[hora] Nome: fala · do jeito que a IA entende") { enviarTexto(textoParaCopiar(c), c.titulo); fechar() }
        Opcao("✦", if (resumindo) "Resumindo…" else "Resumo", "só os pontos principais, pela Mistral") {
            if (resumindo) return@Opcao
            if (Prefs.chave.isBlank()) { Toast.makeText(ctx, "Falta a chave da Mistral", Toast.LENGTH_SHORT).show(); return@Opcao }
            resumindo = true
            escopo.launch {
                val r = try { withContext(Dispatchers.IO) { Mistral.resumir(c) } } catch (e: Exception) { null }
                resumindo = false
                if (r == null) Toast.makeText(ctx, "Não consegui resumir agora", Toast.LENGTH_SHORT).show()
                else { enviarTexto("Resumo: ${c.titulo}\n\n$r", "Resumo: ${c.titulo}"); fechar() }
            }
        }
        Opcao("♪", "Áudio original", c.arquivo) {
            try {
                val i = Intent(Intent.ACTION_SEND).apply { type = "audio/*"; putExtra(Intent.EXTRA_STREAM, Uri.parse(c.uri)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                ctx.startActivity(Intent.createChooser(i, "Compartilhar áudio").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
                fechar()
            } catch (e: Exception) { Toast.makeText(ctx, "Não deu pra compartilhar o áudio", Toast.LENGTH_SHORT).show() }
        }
        Opcao("⧉", "Copiar texto", null, ultimo = true) {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(c.titulo, textoParaCopiar(c)))
            Toast.makeText(ctx, "Texto copiado", Toast.LENGTH_SHORT).show(); fechar()
        }
        if (resumindo) Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Cor.azul, modifier = Modifier.size(22.dp), strokeWidth = 2.dp) }
    }
}

@Composable
private fun Opcao(icone: String, titulo: String, sub: String?, ultimo: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Cor.cartao2), contentAlignment = Alignment.Center) { Text(icone, color = Cor.texto, fontSize = 16.sp) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(titulo, color = Cor.texto, fontSize = 15.5.sp)
            if (sub != null) Text(sub, color = Cor.sub, fontSize = 12.sp, maxLines = 1)
        }
    }
    if (!ultimo) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2C3036)))
}
