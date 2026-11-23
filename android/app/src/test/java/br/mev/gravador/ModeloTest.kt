package br.mev.gravador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeloTest {
    private fun conv(falas: List<Fala>, nomes: Map<String, String> = emptyMap()) =
        Conversa("k", "Voz 261123_001204.m4a", "u", "Consulta", 0L, 120.0, nomes, falas)

    private val falas = listOf(
        Fala(1, "speaker_1", 2.0, 8.0, "E desde quando sente essa dor?"),
        Fala(2, "speaker_2", 9.0, 17.0, "Faz duas semanas."),
        Fala(3, "speaker_3", 18.0, 26.0, "Dói mais de noite."),
        Fala(4, "speaker_2", 21.5, 27.5, "e ele não dorme direito"),
        Fala(5, "speaker_1", 29.0, 37.0, "Vou pedir um ultrassom."),
        Fala(6, "speaker_1", 38.0, 40.0, "E fisioterapia."),
    )

    @Test fun sobreposicaoViraBlocoJuntas() {
        val b = blocos(falas)
        assertEquals(5, b.size)
        assertTrue(b[2] is Bloco.Juntas)
        assertEquals(listOf(3, 4), (b[2] as Bloco.Juntas).falas.map { it.id })
    }

    @Test fun mesmaPessoaSobrepostaNaoEJuntas() {
        val b = blocos(listOf(Fala(1, "a", 0.0, 5.0, "x"), Fala(2, "a", 4.0, 6.0, "y")))
        assertTrue(b.all { it is Bloco.Uma })
    }

    @Test fun falasSeguidasDaMesmaPessoaViramUmTurno() {
        val t = turnos(blocos(falas))
        assertEquals(4, t.size)
        val ultimo = t.last() as Turno
        assertEquals(listOf(5, 6), ultimo.falas.map { it.id })
    }

    @Test fun mesmoNomeJuntaPessoas() {
        val c = conv(falas, mapOf("speaker_1" to "Dr. Paulo", "speaker_3" to " dr. paulo "))
        val u = c.unificada()
        assertEquals(listOf("speaker_1", "speaker_2"), u.pessoas)
        assertEquals("speaker_1", u.falas.first { it.id == 3 }.quem)
    }

    @Test fun textoParaCopiarNoFormatoAprovado() {
        val t = textoParaCopiar(conv(falas, mapOf("speaker_1" to "Dr. Paulo", "speaker_2" to "Matheus")))
        assertTrue(t, t.startsWith("Conversa: Consulta\nData: "))
        assertTrue(t, t.contains("Pessoas: Dr. Paulo, Matheus, Pessoa 3"))
        assertTrue(t, t.contains("[00:02] Dr. Paulo: E desde quando sente essa dor?"))
        assertTrue(t, t.contains("[00:18–00:27] FALANDO AO MESMO TEMPO (não revisado)\n  Pessoa 3: Dói mais de noite.\n  Matheus: e ele não dorme direito"))
        assertTrue(t, t.contains("[00:29] Dr. Paulo: Vou pedir um ultrassom. E fisioterapia."))
    }

    @Test fun revisadoSaiSemAviso() {
        val r = falas.map { if (it.id in listOf(3, 4)) it.copy(revisado = true) else it }
        assertTrue(!textoParaCopiar(conv(r)).contains("não revisado"))
    }

    @Test fun palavrasCaemNaFalaCerta() {
        val p = listOf(2.0 to 2.4, 2.5 to 3.0, 3.1 to 3.5, 9.1 to 9.6, 9.7 to 10.2, 22.0 to 22.4, 22.5 to 23.0)
        val a = alinharPalavras(falas, p)
        assertEquals(listOf(2.0, 2.5, 3.1), a[0].wt)
        assertEquals(listOf(9.1, 9.7), a[1].wt)
        assertEquals(2, a.first { it.id == 4 }.wt.size + a.first { it.id == 3 }.wt.size)
    }

    @Test fun temposDasPalavrasCrescentesEDentroDaFala() {
        for (f in falas + alinharPalavras(falas, listOf(2.0 to 2.4, 5.0 to 5.5))) {
            val t = f.tempos()
            assertEquals(f.palavras.size, t.size)
            assertTrue(t.zipWithNext().all { (a, b) -> a <= b })
            assertTrue(t.all { it >= f.ini && it <= f.fim })
        }
    }

    @Test fun dataDoNomeDoArquivo() {
        val ms = dataDoNome("Voz 261123_000657.m4a")!!
        val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        assertEquals(2026, c.get(java.util.Calendar.YEAR)); assertEquals(10, c.get(java.util.Calendar.MONTH))
        assertEquals(23, c.get(java.util.Calendar.DAY_OF_MONTH)); assertEquals(6, c.get(java.util.Calendar.MINUTE))
    }

    @Test fun horaFormatada() {
        assertEquals("00:05", hora(5.2)); assertEquals("08:21", hora(501.0)); assertEquals("1:02:03", hora(3723.0))
    }
}
