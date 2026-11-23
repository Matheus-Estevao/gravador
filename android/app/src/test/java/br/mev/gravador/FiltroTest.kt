package br.mev.gravador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FiltroTest {
    private fun ms(d: LocalDate, h: Int = 12) = d.atTime(h, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun lerTempo() {
        assertEquals(6 * 3600, Filtro.lerTempo("6 h"))
        assertEquals(5400, Filtro.lerTempo("1h30"))
        assertEquals(90 * 60, Filtro.lerTempo("90 min"))
        assertEquals(45, Filtro.lerTempo("45 s"))
        assertEquals(150, Filtro.lerTempo("2:30"))
        assertEquals(600, Filtro.lerTempo("10"))
        assertEquals(null, Filtro.lerTempo("abc"))
    }

    @Test fun passaPorDataDuracaoPessoa() {
        val d = LocalDate.of(2026, 11, 21)
        val f = Filtro(de = d.minusDays(2), ate = d, durMin = 300, durMax = 1200, pessoa = "Pai")
        assertTrue(f.passa(ms(d), 600.0, "Pai"))
        assertFalse(f.passa(ms(d.plusDays(1)), 600.0, "Pai"))
        assertFalse(f.passa(ms(d), 100.0, "Pai"))
        assertFalse(f.passa(ms(d), 600.0, "Mãe"))
        assertFalse(f.passa(ms(d), null, "Pai"))
        assertTrue(Filtro(de = d).passa(ms(d, 23), null, "x"))
        assertTrue(Filtro().passa(0, null, "x"))
    }

    @Test fun fichas() {
        val f = Filtro(de = LocalDate.of(2026, 11, 15), ate = LocalDate.of(2026, 11, 21), durMin = 300, durMax = 1200, pessoa = "Pai")
        assertEquals(listOf("Pai", "15–21 nov", "5 min–20 min"), f.fichas())
        assertEquals(listOf("mais de 2 h"), Filtro(durMin = 7200).fichas())
        assertEquals(listOf("até 30 s"), Filtro(durMax = 30).fichas())
    }

    @Test fun degraus() {
        assertEquals(0, Filtro.degrauDe(null, false))
        assertEquals(Filtro.DEGRAUS.lastIndex, Filtro.degrauDe(null, true))
        assertEquals(Filtro.DEGRAUS.indexOf(300), Filtro.degrauDe(320, false))
    }
}
