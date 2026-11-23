package br.mev.gravador

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

private val LETRAS = listOf("S", "T", "Q", "Q", "S", "S", "D") // segunda..domingo

/** Painel de meia tela com quando, duração e pessoa. Mostra a contagem ao vivo e só aplica no botão. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PainelFiltro(
    gravacoes: List<Gravacao>,
    inicial: Filtro,
    mostrarPessoa: Boolean,
    duracaoDe: (Gravacao) -> Double?,
    onAplicar: (Filtro) -> Unit,
    onFechar: () -> Unit,
) {
    var f by remember { mutableStateOf(inicial) }
    var calendario by remember { mutableStateOf(false) }
    var digitar by remember { mutableStateOf<Boolean?>(null) } // true = mínimo, false = máximo
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val vibra = LocalHapticFeedback.current

    val porDia = remember(gravacoes) { gravacoes.groupingBy { Filtro.diaDe(it.quando) }.eachCount() }
    val hoje = LocalDate.now()
    val dias = remember(porDia) {
        val primeiro = (porDia.keys.minOrNull() ?: hoje).coerceAtLeast(hoje.minusDays(120))
        generateSequence(primeiro) { it.plusDays(1) }.takeWhile { !it.isAfter(hoje) }.toList()
    }
    val maxDia = (porDia.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val pessoas = remember(gravacoes) { gravacoes.groupingBy { it.exibicao }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    var buscaPessoa by remember { mutableStateOf("") }
    var procurandoPessoa by remember { mutableStateOf(false) }
    val quantas = gravacoes.count { f.passa(it.quando, duracaoDe(it), it.exibicao) }
    val semDuracao = gravacoes.count { (duracaoDe(it) ?: 0.0) <= 0 }

    ModalBottomSheet(onDismissRequest = onFechar, sheetState = estado, containerColor = Cor.cartao2, dragHandle = {
        Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(38.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Cor.linha))
    }) {
        Column(Modifier.padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filtrar", color = Cor.texto, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (f.ativo) Text("Limpar", color = Cor.azul, fontSize = 14.sp, modifier = Modifier.clickable { f = Filtro() }.padding(6.dp))
            }
            Espaco(6)

            // QUANDO: faixa de dias com tracinho de quantidade
            Linha(Icones.calendario, "Quando", valor = f.fichaData())
            val lista = rememberLazyListState(initialFirstVisibleItemIndex = (dias.size - 7).coerceAtLeast(0))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(Modifier.weight(1f), state = lista, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(dias, key = { it.toEpochDay() }) { d ->
                        val n = porDia[d] ?: 0
                        val ponta = d == f.de || d == (f.ate ?: f.de)
                        val meio = f.de != null && f.ate != null && d > f.de!! && d < f.ate!!
                        val fundo = when { ponta -> Cor.azul; meio -> Color(0xFF17324A); else -> Color.Transparent }
                        val corTxt = when { ponta -> Cor.escuroBotao; n == 0 -> Color(0xFF3A4654); else -> Cor.texto2 }
                        Column(
                            Modifier.width(38.dp).clip(RoundedCornerShape(10.dp)).background(fundo)
                                .clickable(enabled = n > 0) {
                                    vibra.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val de = f.de; val ate = f.ate
                                    f = when {
                                        de == null || (ate != null && ate != de) -> f.copy(de = d, ate = null)
                                        d == de -> f.copy(de = null, ate = null)
                                        d < de -> f.copy(de = d, ate = de)
                                        else -> f.copy(ate = d)
                                    }
                                }.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(if (d == hoje) "hoje" else LETRAS[d.dayOfWeek.value - 1], color = if (ponta) corTxt else Cor.sub, fontSize = 10.5.sp)
                            Text("${d.dayOfMonth}", color = corTxt, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            val a = if (n == 0) 0f else 0.3f + 0.7f * n / maxDia
                            Box(Modifier.padding(top = 3.dp).size(14.dp, 3.dp).clip(RoundedCornerShape(2.dp))
                                .background(if (ponta) Cor.escuroBotao.copy(alpha = 0.5f) else Cor.azul.copy(alpha = a)))
                        }
                    }
                }
                Box(Modifier.padding(start = 6.dp).clip(RoundedCornerShape(10.dp)).clickable { calendario = true }.padding(8.dp)) { Ic(Icones.calendario, Cor.azul, 20.dp) }
            }
            Divisor()

            // DURAÇÃO: degraus + desenho das gravações por trás
            val iMin = Filtro.degrauDe(f.durMin, false).toFloat()
            val iMax = Filtro.degrauDe(f.durMax, true).toFloat()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic(Icones.relogio, Cor.sub, 16.dp); Spacer(Modifier.width(6.dp))
                Text("Duração", color = Cor.sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(f.durMin?.let { Filtro.tempoFicha(it) } ?: "0", color = Cor.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { digitar = true }.padding(horizontal = 6.dp, vertical = 4.dp))
                Text("–", color = Cor.sub, fontSize = 15.sp)
                Text(f.durMax?.let { Filtro.tempoFicha(it) } ?: "10 h+", color = Cor.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { digitar = false }.padding(horizontal = 6.dp, vertical = 4.dp))
            }
            val n = Filtro.DEGRAUS.size
            val hist = remember(gravacoes, semDuracao) {
                // quantas gravações caem entre cada par de degraus (3 barrinhas por degrau)
                val sub = 3; val barras = IntArray((n - 1) * sub)
                gravacoes.forEach { g ->
                    val d = duracaoDe(g) ?: return@forEach; if (d <= 0) return@forEach
                    var k = Filtro.DEGRAUS.indexOfLast { it <= d }.coerceIn(0, n - 2)
                    val a = Filtro.DEGRAUS[k]; val b = Filtro.DEGRAUS[k + 1]
                    val fr = if (d >= b) 0.999 else (d - a) / (b - a)
                    barras[(k * sub + (fr * sub).toInt()).coerceIn(0, barras.lastIndex)]++
                }
                barras
            }
            val mx = (hist.maxOrNull() ?: 1).coerceAtLeast(1)
            Canvas(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 10.dp)) {
                val w = size.width / hist.size; val gap = 2f
                hist.forEachIndexed { i, v ->
                    val pos = (i + 0.5f) / 3f // em degraus
                    val dentro = pos >= iMin && pos <= iMax
                    val h = if (v == 0) 2f else (size.height * (0.12f + 0.88f * v / mx))
                    drawRoundRect(if (dentro) Cor.azul else Color(0xFF2A3644), Offset(i * w + gap / 2, size.height - h), Size(w - gap, h), CornerRadius(2f, 2f))
                }
            }
            var ultimo by remember { mutableStateOf(iMin to iMax) }
            RangeSlider(
                value = iMin..iMax,
                onValueChange = { r ->
                    val a = r.start.roundToInt(); val b = r.endInclusive.roundToInt()
                    if (a.toFloat() to b.toFloat() != ultimo) { vibra.performHapticFeedback(HapticFeedbackType.TextHandleMove); ultimo = a.toFloat() to b.toFloat() }
                    f = f.copy(durMin = if (a == 0) null else Filtro.DEGRAUS[a], durMax = if (b == n - 1) null else Filtro.DEGRAUS[b])
                },
                valueRange = 0f..(n - 1).toFloat(),
                steps = n - 2,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Cor.azul, inactiveTrackColor = Cor.linha,
                    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                modifier = Modifier.height(30.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                listOf("0", "1 min", "15 min", "1 h", "10 h+").forEachIndexed { i, t ->
                    Text(t, color = Color(0xFF6E7B8C), fontSize = 11.sp, modifier = Modifier.weight(1f),
                        textAlign = when (i) { 0 -> androidx.compose.ui.text.style.TextAlign.Start; 4 -> androidx.compose.ui.text.style.TextAlign.End; else -> androidx.compose.ui.text.style.TextAlign.Center })
                }
            }
            if (semDuracao > 0 && !Prefs.demo) Text("Medindo a duração de $semDuracao gravações…", color = Cor.sub, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))

            // PESSOA (chamadas): quem mais aparece primeiro
            if (mostrarPessoa && pessoas.isNotEmpty()) {
                Divisor()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ic(Icones.pessoa, Cor.sub, 16.dp); Spacer(Modifier.width(6.dp))
                    if (procurandoPessoa) {
                        Box(Modifier.weight(1f)) {
                            if (buscaPessoa.isEmpty()) Text("Procurar contato", color = Cor.sub, fontSize = 14.sp)
                            BasicTextField(buscaPessoa, { buscaPessoa = it }, singleLine = true, textStyle = TextStyle(color = Cor.texto, fontSize = 14.sp),
                                cursorBrush = SolidColor(Cor.azul), modifier = Modifier.fillMaxWidth())
                        }
                    } else Text("Pessoa", color = Cor.sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable { procurandoPessoa = !procurandoPessoa; buscaPessoa = "" }.padding(6.dp)) {
                        Ic(if (procurandoPessoa) Icones.fechar else Icones.busca, Cor.sub, 18.dp)
                    }
                }
                Espaco(6)
                val q = semAcento(buscaPessoa.trim())
                val mostrar = (if (q.isEmpty()) pessoas else pessoas.filter { semAcento(it).contains(q) }).let { l ->
                    f.pessoa?.let { p -> listOf(p) + l.filter { it != p } } ?: l
                }.take(40)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(mostrar, key = { it }) { p ->
                        val on = p == f.pessoa
                        Text(p, color = if (on) Color(0xFFCFE6FF) else Cor.texto2, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (on) Color(0xFF13283C) else Color(0xFF1E2731))
                                .clickable { f = f.copy(pessoa = if (on) null else p) }.padding(horizontal = 11.dp, vertical = 7.dp))
                    }
                }
            }

            Espaco(16)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Cor.azul).clickable { onAplicar(f) }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Mostrar $quantas ${if (quantas == 1) "gravação" else "gravações"}", color = Cor.escuroBotao, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
    }

    if (calendario) {
        val st = rememberDateRangePickerState(
            initialSelectedStartDateMillis = f.de?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = (f.ate ?: f.de)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { calendario = false },
            confirmButton = {
                TextButton({
                    val a = st.selectedStartDateMillis?.let { LocalDate.ofEpochDay(it / 86_400_000) }
                    val b = st.selectedEndDateMillis?.let { LocalDate.ofEpochDay(it / 86_400_000) }
                    f = f.copy(de = a, ate = b)
                    calendario = false
                }) { Text("Ok", color = Cor.azul) }
            },
            dismissButton = { TextButton({ calendario = false }) { Text("Cancelar", color = Cor.sub) } },
            colors = DatePickerDefaults.colors(containerColor = Cor.cartao2),
        ) {
            DateRangePicker(st, modifier = Modifier.height(520.dp), title = null, showModeToggle = true,
                colors = DatePickerDefaults.colors(containerColor = Cor.cartao2, selectedDayContainerColor = Cor.azul, dayInSelectionRangeContainerColor = Color(0xFF17324A),
                    todayDateBorderColor = Cor.azul, todayContentColor = Cor.azul))
        }
    }

    digitar?.let { minimo ->
        DialogoTexto(if (minimo) "Duração mínima" else "Duração máxima", "", onOk = { t ->
            val s = Filtro.lerTempo(t)
            f = if (minimo) f.copy(durMin = s?.takeIf { it > 0 }) else f.copy(durMax = s?.takeIf { it > 0 })
        }, dica = "Exemplos: 30 s, 5 min, 1h30, 6 h") { digitar = null }
    }
}

@Composable
private fun Linha(icone: androidx.compose.ui.graphics.vector.ImageVector, nome: String, valor: String? = null) {
    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Ic(icone, Cor.sub, 16.dp); Spacer(Modifier.width(6.dp))
        Text(nome, color = Cor.sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (valor != null) Text(valor, color = Cor.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Divisor() = HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color(0xFF222D39))
