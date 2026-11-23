package br.mev.gravador

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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

/** Posição que a tela mostra: a do arraste (se estiver arrastando) ou a do áudio. */
class Cabeca { var arrastando by mutableStateOf(false); var posArraste by mutableStateOf(0.0) }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val c = remember(versao, chave) { Banco.ler(chave) }
    if (c == null) { LaunchedEffect(Unit) { onVoltar() }; return }
    val blocos = remember(c) { blocos(c.falas) }
    val cabeca = remember { Cabeca() }
    var renomearTitulo by remember { mutableStateOf(false) }
    var renomearPessoa by remember { mutableStateOf<String?>(null) }
    var compartilhar by remember { mutableStateOf(abrirCompartilhar) }

    LaunchedEffect(c.uri) { tocador.carregar(c.uri) }
    // posição lisa: atualiza a cada quadro enquanto toca
    LaunchedEffect(tocador.tocando) { while (tocador.tocando) { withFrameNanos { }; tocador.atualizar() } }

    val duracao = if (tocador.duracao > 0) tocador.duracao else c.duracao
    val pos = if (cabeca.arrastando) cabeca.posArraste else tocador.posicao

    // rolagem automática acompanhando o áudio, a menos que você tenha rolado com o dedo há pouco
    val lista = rememberLazyListState()
    var dedoEm by remember { mutableLongStateOf(0L) }
    var auto by remember { mutableStateOf(false) }
    LaunchedEffect(lista) { snapshotFlow { lista.isScrollInProgress }.collect { if (it && !auto) dedoEm = System.currentTimeMillis() } }
    val alvo = remember(pos, blocos) { blocos.indexOfFirst { it.fim > pos }.let { if (it < 0) blocos.lastIndex else it } }
    val acompanhar = tocador.tocando || cabeca.arrastando
    LaunchedEffect(alvo, acompanhar) {
        if (!acompanhar || alvo < 0) return@LaunchedEffect
        if (System.currentTimeMillis() - dedoEm < 3500 && !cabeca.arrastando) return@LaunchedEffect
        val vis = lista.layoutInfo.visibleItemsInfo
        val idx = alvo + 1 // item 0 é o cabeçalho
        val dentro = vis.any { it.index == idx && it.offset >= lista.layoutInfo.viewportStartOffset && it.offset + it.size <= lista.layoutInfo.viewportEndOffset }
        if (!dentro) {
            auto = true
            try { if (cabeca.arrastando) lista.scrollToItem(idx, -80) else lista.animateScrollToItem(idx, -80) } finally { auto = false }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Redondo("‹", onClick = onVoltar)
            Spacer(Modifier.weight(1f))
            Redondo("✎") { renomearTitulo = true }
            Spacer(Modifier.width(8.dp))
            Redondo("⇪", forte = true) { compartilhar = true }
        }
        Espaco(10)
        Titulo(c.titulo.ifBlank { c.arquivo }, "${diaCurto(c.quando)} · ${duracaoCurta(duracao)}")
        Espaco(10)
        Tocadorzinho(c, tocador, cabeca, duracao, pos)
        Espaco(8)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            c.pessoas.forEach { p -> Pilula(c.nome(p) + if (c.nomes[p].isNullOrBlank()) " ✎" else "", ponto = corDe(c, p)) { renomearPessoa = p } }
        }
        Espaco(6)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = lista,
            contentPadding = PaddingValues(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(2.dp)) }
            itemsIndexed(blocos, key = { _, b -> b.falas.first().id }) { _, b ->
                when (b) {
                    is Bloco.Uma -> LinhaFala(c, b.fala, pos, { tocador.irPara(it) }) { onEditar(listOf(b.fala.id)) }
                    is Bloco.Juntas -> BlocoJuntas(c, b, pos, { tocador.irPara(it) }) { onEditar(b.falas.map { it.id }) }
                }
            }
            if (c.falas.isEmpty()) item { Text("A Mistral não achou fala nessa gravação.", color = Cor.sub, fontSize = 14.sp) }
        }
    }

    if (renomearTitulo) DialogoTexto("Nome da conversa", c.titulo, { if (it.isNotBlank()) Banco.salvar(c.copy(titulo = it)) }) { renomearTitulo = false }
    renomearPessoa?.let { p ->
        DialogoTexto("Quem é ${c.nome(p)}?", c.nomes[p] ?: "", { Banco.salvar(c.copy(nomes = c.nomes + (p to it))) }) { renomearPessoa = null }
    }
    if (compartilhar) {
        ModalBottomSheet(onDismissRequest = { compartilhar = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF1F2228)) {
            Compartilhar(ctx, c) { compartilhar = false }
        }
    }
}

@Composable
fun Tocadorzinho(c: Conversa, tocador: Tocador, cabeca: Cabeca, duracao: Double, pos: Double) {
    Cartao(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Cor.azul).clickable { tocador.alternar() },
                    contentAlignment = Alignment.Center,
                ) { Text(if (tocador.tocando) "❚❚" else "▶", color = Cor.escuroBotao, fontSize = if (tocador.tocando) 14.sp else 17.sp) }
                Spacer(Modifier.width(10.dp))
                Faixas(c, cabeca, duracao, pos, Modifier.weight(1f)) { tocador.irPara(it) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${hora(pos)} / ${hora(duracao)}", color = Cor.sub, fontSize = 12.5.sp)
                Spacer(Modifier.weight(1f))
                val vs = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
                Pilula(("%.2f".format(tocador.velocidade)).trimEnd('0').trimEnd('.', ',').replace('.', ',') + "x") {
                    tocador.mudarVelocidade(vs[(vs.indexOf(tocador.velocidade) + 1) % vs.size])
                }
            }
        }
    }
}

/** Uma faixa por pessoa. Tocar ou arrastar move a linha do áudio, e o texto vai junto. */
@Composable
fun Faixas(c: Conversa, cabeca: Cabeca, duracao: Double, pos: Double, modifier: Modifier = Modifier, corFn: (String) -> Color = { corDe(c, it) }, irPara: (Double) -> Unit) {
    val pessoas = c.pessoas.ifEmpty { listOf("speaker_1") }
    val dens = LocalDensity.current
    val altFaixa = 10.dp; val vao = 5.dp
    val alt = (altFaixa + vao) * pessoas.size + 8.dp
    var ultimoSeek by remember { mutableLongStateOf(0L) }
    Canvas(
        modifier.height(alt)
            .pointerInput(duracao) {
                detectTapGestures { o -> irPara((o.x / size.width).coerceIn(0f, 1f) * duracao) }
            }
            .pointerInput(duracao) {
                detectDragGestures(
                    onDragStart = { o -> cabeca.arrastando = true; cabeca.posArraste = (o.x / size.width).coerceIn(0f, 1f) * duracao },
                    onDragEnd = { irPara(cabeca.posArraste); cabeca.arrastando = false },
                    onDragCancel = { irPara(cabeca.posArraste); cabeca.arrastando = false },
                ) { ch, _ ->
                    cabeca.posArraste = (ch.position.x / size.width).coerceIn(0f, 1f) * duracao
                    val agora = System.currentTimeMillis()
                    if (agora - ultimoSeek > 60) { ultimoSeek = agora; irPara(cabeca.posArraste) }
                }
            }
    ) {
        val w = size.width
        val fh = with(dens) { altFaixa.toPx() }; val g = with(dens) { vao.toPx() }
        val raio = CornerRadius(fh / 2, fh / 2)
        val ativos = c.falas.filter { pos >= it.ini && pos < it.fim }.map { it.quem }.toSet()
        pessoas.forEachIndexed { i, p ->
            val y = 4f + i * (fh + g)
            drawRoundRect(Color(0x0FFFFFFF), Offset(0f, y), Size(w, fh), raio)
            val cor = corFn(p)
            c.falas.filter { it.quem == p }.forEach { f ->
                if (duracao <= 0) return@forEach
                val x0 = (f.ini / duracao * w).toFloat(); val x1 = (f.fim / duracao * w).toFloat().coerceAtLeast(x0 + fh * 0.6f)
                val passado = f.fim <= pos
                drawRoundRect(cor.copy(alpha = if (passado || (p in ativos && pos >= f.ini && pos < f.fim)) 1f else 0.45f), Offset(x0, y), Size(x1 - x0, fh), raio)
            }
        }
        if (duracao > 0) {
            val x = (pos / duracao * w).toFloat().coerceIn(0f, w)
            drawRect(Cor.texto, Offset(x - 1.5f, 0f), Size(3f, size.height))
            drawCircle(Cor.texto, if (cabeca.arrastando) 9f else 6f, Offset(x, 2f))
        }
    }
}

/** Texto em que cada palavra é tocável e a palavra que está tocando fica marcada. */
@Composable
fun TextoPalavras(f: Fala, cor: Color, pos: Double, irPara: (Double) -> Unit, tamanho: Float = 15f) {
    val palavras = remember(f) { f.palavras }
    val tempos = remember(f) { f.tempos() }
    val ativa = if (pos >= f.ini && pos < f.fim) tempos.indexOfLast { it <= pos } else -1
    val inicios = remember(palavras) { var acc = 0; palavras.map { w -> val s = acc; acc += w.length + 1; s } }
    val texto = buildAnnotatedString {
        palavras.forEachIndexed { i, w ->
            if (i == ativa) withStyle(SpanStyle(background = cor.copy(alpha = 0.32f), color = Color.White)) { append(w) } else append(w)
            if (i < palavras.lastIndex) append(' ')
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        texto, color = Cor.texto2, fontSize = tamanho.sp, lineHeight = (tamanho * 1.5f).sp,
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

@Composable
fun LinhaFala(c: Conversa, f: Fala, pos: Double, irPara: (Double) -> Unit, onEditar: () -> Unit) {
    val cor = corDe(c, f.quem)
    val atual = pos >= f.ini && pos < f.fim
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(70.dp).clickable { onEditar() }.padding(top = 2.dp)) {
            Text(c.nome(f.quem), color = cor, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(hora(f.ini), color = Cor.sub, fontSize = 11.5.sp)
            if (f.manual || f.revisado) Text("✎ editado", color = Cor.sub, fontSize = 10.5.sp)
        }
        Box(Modifier.width(2.dp).fillMaxHeight().background(cor))
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                .background(if (atual) Cor.azul2.copy(alpha = 0.45f) else Color.Transparent)
                .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp)
        ) { TextoPalavras(f, cor, pos, irPara) }
    }
}

@Composable
fun BlocoJuntas(c: Conversa, b: Bloco.Juntas, pos: Double, irPara: (Double) -> Unit, onEditar: () -> Unit) {
    Cartao(Modifier.fillMaxWidth(), borda = Cor.laranjaBorda, fundo = Color(0x10FF9A3C)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚡ ao mesmo tempo · ${hora(b.ini)}–${hora(b.fim)}", color = Cor.laranja, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                if (b.revisado) Text("revisado", color = Cor.verde, fontSize = 11.5.sp)
            }
            b.falas.forEach { f ->
                val cor = corDe(c, f.quem)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(cor))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("${c.nome(f.quem)} · ${hora(f.ini)}", color = cor, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        TextoPalavras(f, cor, pos, irPara, 14.5f)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!b.revisado) Text("pode faltar uma das vozes", color = Cor.sub, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
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
