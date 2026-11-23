package br.mev.gravador

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Filtro da lista. Duração em segundos; null = sem limite. */
data class Filtro(
    val de: LocalDate? = null,
    val ate: LocalDate? = null,
    val durMin: Int? = null,
    val durMax: Int? = null,
    val pessoa: String? = null,
) {
    val ativo: Boolean get() = de != null || durMin != null || durMax != null || pessoa != null

    fun passa(quando: Long, dur: Double?, quem: String): Boolean {
        if (de != null) { val d = diaDe(quando); if (d < de || d > (ate ?: de)) return false }
        if (durMin != null || durMax != null) {
            if (dur == null || dur <= 0) return false
            if (durMin != null && dur < durMin) return false
            if (durMax != null && dur > durMax) return false
        }
        if (pessoa != null && quem != pessoa) return false
        return true
    }

    fun fichaData(): String? {
        val de = de ?: return null
        return if (ate == null || ate == de) diaFicha(de) else "${diaFicha(de, mes = de.month != ate.month)}–${diaFicha(ate)}"
    }

    /** Pedacinhos que aparecem escritos dentro da busca. */
    fun fichas(): List<String> = buildList {
        pessoa?.let { add(it) }
        fichaData()?.let { add(it) }
        if (durMin != null || durMax != null) add(when {
            durMin != null && durMax != null -> "${tempoFicha(durMin)}–${tempoFicha(durMax)}"
            durMin != null -> "mais de ${tempoFicha(durMin)}"
            else -> "até ${tempoFicha(durMax!!)}"
        })
    }

    companion object {
        /** Degraus da bolinha da duração: a linha abre espaço pros curtos e aperta os longos. */
        val DEGRAUS = listOf(0, 10, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200, 10800, 18000, 36000)
        val ROTULOS = listOf("0", "10 s", "30 s", "1 min", "2 min", "5 min", "10 min", "15 min", "30 min", "1 h", "2 h", "3 h", "5 h", "10 h")

        fun diaDe(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

        private val MESES = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
        fun diaFicha(d: LocalDate, mes: Boolean = true) = if (mes) "${d.dayOfMonth} ${MESES[d.monthValue - 1]}" else "${d.dayOfMonth}"

        fun tempoFicha(s: Int): String = when {
            s < 60 -> "$s s"
            s < 3600 -> if (s % 60 == 0) "${s / 60} min" else "${s / 60}min${s % 60}"
            else -> if (s % 3600 == 0) "${s / 3600} h" else "${s / 3600}h${"%02d".format((s % 3600) / 60)}"
        }

        /** Degrau mais perto de um valor (pra posicionar a bolinha quando o valor foi digitado). */
        fun degrauDe(s: Int?, fim: Boolean): Int {
            if (s == null) return if (fim) DEGRAUS.lastIndex else 0
            return DEGRAUS.indices.minBy { kotlin.math.abs(DEGRAUS[it] - s) }
        }

        /** Entende "6 h", "90 min", "1h30", "45 s", "2:30" (min:seg) e número puro (minutos). */
        fun lerTempo(t: String): Int? {
            val s = semAcento(t).replace(" ", "").replace(",", ".")
            if (s.isEmpty()) return null
            Regex("^(\\d+):(\\d{1,2})$").find(s)?.let { return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
            Regex("^(\\d+(?:\\.\\d+)?)h(?:oras?)?(\\d+)?(?:m(?:in)?)?$").find(s)?.let {
                return (it.groupValues[1].toDouble() * 3600).toInt() + (it.groupValues[2].toIntOrNull() ?: 0) * 60
            }
            Regex("^(\\d+(?:\\.\\d+)?)m(?:in(?:utos?)?)?$").find(s)?.let { return (it.groupValues[1].toDouble() * 60).toInt() }
            Regex("^(\\d+)s(?:eg(?:undos?)?)?$").find(s)?.let { return it.groupValues[1].toInt() }
            return s.toDoubleOrNull()?.let { (it * 60).toInt() }
        }
    }
}
