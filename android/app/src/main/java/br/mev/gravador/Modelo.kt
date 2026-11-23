package br.mev.gravador

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Uma fala (trecho) de uma pessoa. Tempos em segundos. wt = início de cada palavra (da Mistral), pode vir vazio. */
data class Fala(
    val id: Int,
    val quem: String,
    val ini: Double,
    val fim: Double,
    val texto: String,
    val wt: List<Double> = emptyList(),
    val revisado: Boolean = false,
    val manual: Boolean = false,
) {
    val palavras: List<String> get() = texto.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** Tempo de início de cada palavra exibida. */
    fun tempos(): List<Double> {
        val p = palavras
        if (p.isEmpty()) return emptyList()
        if (wt.size >= 2 && !revisado) {
            return p.indices.map { i ->
                val j = if (p.size == 1) 0 else Math.round(i.toDouble() * (wt.size - 1) / (p.size - 1)).toInt()
                wt[j.coerceIn(0, wt.size - 1)].coerceIn(ini, fim)
            }
        }
        val total = p.sumOf { it.length + 1 }.toDouble()
        var acc = 0.0
        return p.map { w -> val t = ini + (fim - ini) * (acc / total); acc += w.length + 1; t }
    }

    fun json(): JSONObject = JSONObject().apply {
        put("id", id); put("quem", quem); put("ini", ini); put("fim", fim); put("texto", texto)
        put("wt", JSONArray(wt)); put("revisado", revisado); put("manual", manual)
    }

    companion object {
        fun de(o: JSONObject): Fala {
            val a = o.optJSONArray("wt") ?: JSONArray()
            return Fala(
                o.getInt("id"), o.getString("quem"), o.getDouble("ini"), o.getDouble("fim"),
                o.getString("texto"), (0 until a.length()).map { a.getDouble(it) },
                o.optBoolean("revisado"), o.optBoolean("manual"),
            )
        }
    }
}

data class Conversa(
    val chave: String,
    val arquivo: String,
    val uri: String,
    val titulo: String,
    val quando: Long,
    val duracao: Double,
    val nomes: Map<String, String>,
    val falas: List<Fala>,
) {
    fun json(): JSONObject = JSONObject().apply {
        put("chave", chave); put("arquivo", arquivo); put("uri", uri); put("titulo", titulo)
        put("quando", quando); put("duracao", duracao)
        put("nomes", JSONObject(nomes as Map<*, *>))
        put("falas", JSONArray().apply { falas.forEach { put(it.json()) } })
    }

    /** Ordem das pessoas: por primeira aparição. */
    val pessoas: List<String> get() = falas.sortedBy { it.ini }.map { it.quem }.distinct()

    fun nome(quem: String): String = nomes[quem]?.takeIf { it.isNotBlank() } ?: run {
        val n = Regex("\\d+").find(quem)?.value
        if (n != null) "Pessoa $n" else quem
    }

    val proximoId: Int get() = (falas.maxOfOrNull { it.id } ?: 0) + 1

    /** Pessoas com o mesmo nome viram uma só (a primeira que apareceu). */
    fun canonica(quem: String): String {
        val n = nomes[quem]?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return quem
        return falas.sortedBy { it.ini }.map { it.quem }.distinct().firstOrNull { nomes[it]?.trim()?.lowercase() == n } ?: quem
    }

    /** Cópia em que cada fala usa a pessoa canônica (pra exibir e copiar). */
    fun unificada(): Conversa {
        if (nomes.isEmpty()) return this
        val mapa = falas.map { it.quem }.distinct().associateWith { canonica(it) }
        if (mapa.all { (k, v) -> k == v }) return this
        return copy(falas = falas.map { it.copy(quem = mapa[it.quem] ?: it.quem) })
    }

    companion object {
        fun de(o: JSONObject): Conversa {
            val n = o.optJSONObject("nomes") ?: JSONObject()
            val f = o.getJSONArray("falas")
            return Conversa(
                o.getString("chave"), o.getString("arquivo"), o.getString("uri"), o.optString("titulo"),
                o.getLong("quando"), o.getDouble("duracao"),
                n.keys().asSequence().associateWith { n.getString(it) },
                (0 until f.length()).map { Fala.de(f.getJSONObject(it)) },
            )
        }
    }
}

/** Bloco exibido: uma fala sozinha ou várias que se sobrepõem no tempo. */
sealed class Bloco {
    abstract val ini: Double
    abstract val fim: Double
    abstract val falas: List<Fala>
    data class Uma(val fala: Fala) : Bloco() {
        override val ini get() = fala.ini; override val fim get() = fala.fim; override val falas get() = listOf(fala)
    }
    data class Juntas(override val falas: List<Fala>) : Bloco() {
        override val ini get() = falas.minOf { it.ini }; override val fim get() = falas.maxOf { it.fim }
        val revisado get() = falas.all { it.revisado }
    }
}

/** Falas seguidas da mesma pessoa, sem ninguém no meio. */
data class Turno(val quem: String, override val falas: List<Fala>) : Bloco() {
    override val ini get() = falas.first().ini
    override val fim get() = falas.maxOf { it.fim }
}

/** Junta blocos de uma fala só seguidos da mesma pessoa num turno. */
fun turnos(bs: List<Bloco>): List<Bloco> {
    val out = mutableListOf<Bloco>()
    for (b in bs) {
        val ult = out.lastOrNull()
        if (b is Bloco.Uma) {
            if (ult is Turno && ult.quem == b.fala.quem) out[out.lastIndex] = Turno(ult.quem, ult.falas + b.fala)
            else out += Turno(b.fala.quem, listOf(b.fala))
        } else out += b
    }
    return out
}

/** Coloca cada palavra (início, fim) na fala em que ela cai. */
fun alinharPalavras(falas: List<Fala>, palavras: List<Pair<Double, Double>>): List<Fala> {
    if (palavras.isEmpty()) return falas
    val por = HashMap<Int, MutableList<Double>>()
    for ((a, b) in palavras) {
        val m = (a + b) / 2
        val dentro = falas.filter { m >= it.ini - 0.05 && m <= it.fim + 0.05 }
        val f = dentro.minByOrNull { kotlin.math.abs((it.ini + it.fim) / 2 - m) } ?: continue
        por.getOrPut(f.id) { mutableListOf() } += a
    }
    return falas.map { f -> por[f.id]?.takeIf { it.size >= 2 }?.let { f.copy(wt = it.sorted()) } ?: f }
}

const val FOLGA = 0.15

fun blocos(falas: List<Fala>): List<Bloco> {
    val ord = falas.sortedWith(compareBy({ it.ini }, { it.id }))
    val out = mutableListOf<Bloco>()
    var grupo = mutableListOf<Fala>()
    var fimGrupo = -1.0
    fun fecha() {
        if (grupo.isEmpty()) return
        if (grupo.map { it.quem }.distinct().size > 1) out += Bloco.Juntas(grupo.toList())
        else grupo.forEach { out += Bloco.Uma(it) }
        grupo = mutableListOf()
    }
    for (f in ord) {
        if (grupo.isNotEmpty() && f.ini < fimGrupo - FOLGA) { grupo += f; fimGrupo = maxOf(fimGrupo, f.fim) }
        else { fecha(); grupo += f; fimGrupo = f.fim }
    }
    fecha()
    return out
}

fun hora(seg: Double): String {
    val s = seg.toInt().coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val ss = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%02d:%02d".format(m, ss)
}

fun duracaoCurta(seg: Double): String {
    val m = (seg / 60).toInt()
    return when { seg < 60 -> "${seg.toInt()} s"; m < 60 -> "$m min"; else -> "${m / 60} h ${m % 60} min" }
}

/** "Voz 261123_000657" -> data. */
fun dataDoNome(nome: String): Long? {
    val m = Regex("(\\d{6})_(\\d{6})").find(nome) ?: return null
    return try { SimpleDateFormat("yyMMddHHmmss", Locale.US).parse(m.groupValues[1] + m.groupValues[2])?.time } catch (e: Exception) { null }
}

private val meses = arrayOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

fun diaCurto(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val hoje = Calendar.getInstance()
    val ontem = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun mesmo(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    val hm = "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    val d = when {
        mesmo(c, hoje) -> "hoje"
        mesmo(c, ontem) -> "ontem"
        else -> "${c.get(Calendar.DAY_OF_MONTH)} ${meses[c.get(Calendar.MONTH)]}"
    }
    return "$d $hm"
}

fun dataLonga(ms: Long): String = SimpleDateFormat("dd/MM/yyyy, HH:mm", Locale("pt", "BR")).format(ms)

fun semAcento(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

/** Texto para copiar/compartilhar, no formato aprovado. */
fun textoParaCopiar(conv: Conversa): String {
    val c = conv.unificada()
    val sb = StringBuilder()
    sb.append("Conversa: ").append(c.titulo).append('\n')
    sb.append("Data: ").append(dataLonga(c.quando)).append(" · ").append(duracaoCurta(c.duracao)).append('\n')
    sb.append("Pessoas: ").append(c.pessoas.joinToString(", ") { c.nome(it) }).append("\n\n")
    var ultimo: String? = null
    for (b in blocos(c.falas)) {
        when (b) {
            is Bloco.Uma -> {
                val f = b.fala
                if (ultimo == f.quem && sb.isNotEmpty() && sb.last() == '\n') {
                    sb.setLength(sb.length - 1); sb.append(' ').append(f.texto.trim()).append('\n')
                } else {
                    sb.append('[').append(hora(f.ini)).append("] ").append(c.nome(f.quem)).append(": ").append(f.texto.trim()).append('\n')
                }
                ultimo = f.quem
            }
            is Turno -> {}
            is Bloco.Juntas -> {
                sb.append('[').append(hora(b.ini)).append('–').append(hora(b.fim)).append("] FALANDO AO MESMO TEMPO")
                if (!b.revisado) sb.append(" (não revisado)")
                sb.append('\n')
                for (f in b.falas) sb.append("  ").append(c.nome(f.quem)).append(": ").append(f.texto.trim()).append('\n')
                ultimo = null
            }
        }
    }
    return sb.toString().trimEnd()
}
