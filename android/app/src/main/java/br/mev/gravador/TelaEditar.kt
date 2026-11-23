package br.mev.gravador

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class Item(val id: Int?, quem: String, texto: String, var ini: Double, var fim: Double, val manual: Boolean) {
    var quem by mutableStateOf(quem)
    var campo by mutableStateOf(TextFieldValue(texto))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaEditar(chave: String, ids: List<Int>, tocador: Tocador, onFechar: () -> Unit) {
    val c = remember(chave) { Banco.ler(chave) }
    if (c == null) { LaunchedEffect(Unit) { onFechar() }; return }
    val orig = remember { c.falas.filter { it.id in ids }.sortedBy { it.ini } }
    if (orig.isEmpty()) { LaunchedEffect(Unit) { onFechar() }; return }
    val ini = orig.minOf { it.ini }; val fim = orig.maxOf { it.fim }
    val itens = remember { mutableStateListOf<Item>().apply { orig.forEach { add(Item(it.id, it.quem, it.texto, it.ini, it.fim, it.manual)) } } }
    var foco by remember { mutableIntStateOf(0) }
    var repetir by remember { mutableStateOf(true) }
    val velAntes = remember { tocador.velocidade }
    var devagar by remember { mutableStateOf(false) }
    val pessoasExtras = remember { mutableStateListOf<String>() }
    val pessoas = (c.pessoas + pessoasExtras).distinct()
    val nomeDe = { p: String -> c.nome(p) }
    val cu = remember { c.unificada() }
    val corPessoa = { p: String -> if (c.canonica(p) in cu.pessoas) corDe(cu, c.canonica(p)) else Cor.vozes[(c.pessoas.size + pessoasExtras.indexOf(p).coerceAtLeast(0)) % Cor.vozes.size] }

    LaunchedEffect(Unit) { tocador.carregar(c.uri); tocador.irPara(ini) }
    LaunchedEffect(repetir) { tocador.loop = if (repetir) ini to fim else null }
    LaunchedEffect(tocador.tocando) { while (tocador.tocando) { withFrameNanos { }; tocador.atualizar() } }
    DisposableEffect(Unit) { onDispose { tocador.loop = null; tocador.mudarVelocidade(velAntes) } }

    fun novaPessoa(): String {
        val n = (pessoas.mapNotNull { Regex("\\d+").find(it)?.value?.toIntOrNull() }.maxOrNull() ?: 0) + 1
        return "speaker_$n".also { pessoasExtras += it }
    }

    fun salvar() {
        var prox = c.proximoId
        val novas = itens.filter { it.campo.text.isNotBlank() }.map { it ->
            val id = it.id ?: prox++
            val velha = orig.firstOrNull { o -> o.id == id }
            val mudou = velha == null || velha.texto != it.campo.text.trim() || velha.quem != it.quem || velha.ini != it.ini || velha.fim != it.fim
            Fala(id, it.quem, it.ini, it.fim, it.campo.text.trim(), if (mudou) emptyList() else velha!!.wt, revisado = true, manual = it.manual || velha == null)
        }
        Banco.salvar(c.copy(falas = c.falas.filter { it.id !in ids } + novas))
        onFechar()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Redondo("✕", onClick = onFechar)
            Spacer(Modifier.weight(1f))
            BotaoAzul("Salvar") { salvar() }
        }
        Espaco(10)
        val varias = orig.map { it.quem }.distinct().size > 1
        Titulo("Editar trecho", "${hora(ini)} – ${hora(fim)}" + if (varias) " · falando ao mesmo tempo" else " · ${nomeDe(orig.first().quem)}")
        Espaco(10)
        val sub = remember(itens.size, itens.map { it.quem }) {
            c.copy(falas = itens.mapIndexed { i, it -> Fala(it.id ?: (10000 + i), it.quem, it.ini, it.fim, "") }, duracao = fim - ini)
        }
        Cartao(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Redondo(if (tocador.tocando) "❚❚" else "▶", forte = true) {
                    if (!tocador.tocando && (tocador.posicao < ini || tocador.posicao >= fim)) tocador.irPara(ini)
                    tocador.alternar()
                }
                Spacer(Modifier.width(10.dp))
                // faixas só desse trecho (tempo relativo)
                val cab = remember { Cabeca() }
                val rel = ((if (cab.arrastando) cab.posArraste + ini else tocador.posicao) - ini).coerceIn(0.0, fim - ini)
                Faixas(sub.copy(falas = sub.falas.map { it.copy(ini = it.ini - ini, fim = it.fim - ini) }), cab, fim - ini, rel, Modifier.weight(1f), corPessoa) { tocador.irPara(ini + it) }
            }
        }
        Espaco(8)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pilula("⟲ repetir trecho", cor = if (repetir) Cor.azul else Cor.texto, borda = if (repetir) Cor.azul else Cor.linha) { repetir = !repetir }
            Pilula("0,75x", cor = if (devagar) Cor.azul else Cor.texto, borda = if (devagar) Cor.azul else Cor.linha) {
                devagar = !devagar; tocador.mudarVelocidade(if (devagar) 0.75f else 1f)
            }
            Pilula("✂ dividir aqui") {
                val it = itens.getOrNull(foco) ?: return@Pilula
                val t = tocador.posicao.coerceIn(it.ini + 0.1, it.fim - 0.1)
                val cur = it.campo.selection.start.coerceIn(0, it.campo.text.length)
                val antes = it.campo.text.substring(0, cur).trim(); val depois = it.campo.text.substring(cur).trim()
                val novo = Item(null, it.quem, depois, t, it.fim, manual = true)
                it.campo = TextFieldValue(antes, TextRange(antes.length)); it.fim = t
                itens.add(foco + 1, novo)
            }
        }
        Espaco(12)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itens.forEachIndexed { i, it ->
                val cor = corPessoa(it.quem)
                var menu by remember(it) { mutableStateOf(false) }
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Cor.cartao)
                        .border(1.dp, if (foco == i) cor else Cor.linha, RoundedCornerShape(12.dp))
                ) {
                    Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 9.dp, bottom = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                Text("● ${nomeDe(it.quem)} ▾", color = cor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { menu = true })
                                DropdownMenu(menu, { menu = false }, containerColor = Cor.cartao2) {
                                    pessoas.forEach { p -> DropdownMenuItem({ Text(nomeDe(p), color = Cor.texto) }, { it.quem = p; menu = false }) }
                                    DropdownMenuItem({ Text("+ outra pessoa", color = Cor.azul) }, { it.quem = novaPessoa(); menu = false })
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(hora(it.ini), color = Cor.sub, fontSize = 11.5.sp, modifier = Modifier.clickable { tocador.irPara(it.ini) })
                            if (itens.size > 1) Text("  ✕", color = Cor.sub, fontSize = 13.sp, modifier = Modifier.clickable { itens.removeAt(i); foco = 0 })
                        }
                        BasicTextField(
                            it.campo, { v -> it.campo = v },
                            textStyle = TextStyle(color = Cor.texto, fontSize = 15.sp, lineHeight = 22.sp),
                            cursorBrush = SolidColor(Cor.azul),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).onFocusChanged { f -> if (f.isFocused) foco = i },
                            decorationBox = { inner ->
                                if (it.campo.text.isEmpty()) Text("digite o que essa pessoa falou", color = Cor.sub, fontSize = 15.sp)
                                inner()
                            },
                        )
                    }
                }
            }
            Text(
                "+ adicionar fala de outra pessoa", color = Cor.sub, fontSize = 13.5.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Cor.linha, RoundedCornerShape(12.dp))
                    .clickable {
                        val presentes = itens.map { x -> x.quem }.toSet()
                        val quem = c.pessoas.firstOrNull { p -> p !in presentes } ?: novaPessoa()
                        itens.add(Item(null, quem, "", ini, fim, manual = true)); foco = itens.lastIndex
                    }.padding(12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text("O que você digitar substitui o texto da Mistral. O áudio não muda.", color = Cor.sub, fontSize = 11.5.sp)
            Espaco(30)
        }
    }
}
