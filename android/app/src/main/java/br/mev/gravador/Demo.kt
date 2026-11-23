package br.mev.gravador

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/** Modo de demonstração (só pra testar telas sem a pasta do celular). Liga com: am start ... --ez demo true */
object Demo {
    private const val DUR = 350.0

    private fun audio(ctx: Context): File {
        val f = File(ctx.cacheDir, "demo.wav")
        if (f.exists()) return f
        val rate = 8000; val n = (DUR * rate).toInt()
        val buf = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + n * 2).put("WAVE".toByteArray()).put("fmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(n * 2)
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            val env = if ((t * 1.3).toInt() % 3 == 2) 0.05 else 0.35
            buf.putShort((sin(2 * PI * (180 + 40 * sin(t)) * t) * env * 32767 * 0.5).toInt().toShort())
        }
        FileOutputStream(f).use { it.write(buf.array()) }
        return f
    }

    private val nomes = listOf(
        "Voz 261123_003412.m4a" to 12 * 60.0, "Voz 261123_001204.m4a" to 38 * 60.0, "Voz 261122_190533.m4a" to 22 * 60.0,
        "Voz 261122_144010.m4a" to 9 * 60.0, "Voz 261120_231047.m4a" to 6 * 60.0, "Voz 261119_082215.m4a" to 41 * 60.0,
    )

    fun gravacoes(ctx: Context): List<Gravacao> {
        val f = audio(ctx)
        return nomes.mapIndexed { i, (n, _) ->
            Gravacao(Uri.fromFile(f), n, 1000L + i, dataDoNome(n) ?: 0L)
        }
    }

    fun semear(ctx: Context) {
        val g = gravacoes(ctx)[1]
        if (Banco.ler(g.chave) == null) Banco.salvar(transcrever(g).copy(titulo = "Consulta Dr. Paulo", nomes = mapOf("speaker_1" to "Dr. Paulo", "speaker_2" to "Matheus")))
        val g0 = gravacoes(ctx)[0]
        if (Banco.ler(g0.chave) == null) Banco.salvar(prova(g0))
        val g2 = gravacoes(ctx)[3]
        if (Banco.ler(g2.chave) == null) Banco.salvar(transcrever(g2).copy(titulo = "Discussão do carro"))
    }

    /** Conversa longa com 7 vozes (parecida com a gravação real que ele testou). */
    fun prova(g: Gravacao): Conversa {
        val frases = listOf(
            "É observação, né?", "É na extensão, tá lá, a cheirada tá lá.", "Eu vou soltar a palaclagem aqui, então vai dar uma saída.",
            "Aí bota aqui, ó.", "Até aqui, às seis horas.", "Isso, segura firme.", "Pode tirar agora.", "Tá bom assim?",
            "Calma, deixa eu ver.", "Não, mais pra cima.", "Agora sim.", "Vai precisar de outra?", "Acho que não.",
        )
        val quem = listOf(1, 1, 1, 2, 3, 3, 1, 4, 4, 1, 5, 6, 7, 6, 5, 7, 1, 3, 3, 2)
        val f = mutableListOf<Fala>()
        var t = 95.0
        quem.forEachIndexed { i, q ->
            val d = 3.0 + (i % 4) * 1.5
            val ini = if (i == 9 || i == 14) t - 2.0 else t
            f += Fala(i + 1, "speaker_$q", ini, ini + d, frases[i % frases.size])
            t = ini + d + 1.0 + (i % 3) * 3.0
        }
        return Conversa(g.chave, g.nome, g.uri.toString(), "Prova, ausentei do hospital", g.quando, DUR, emptyMap(), f)
    }

    fun transcrever(g: Gravacao): Conversa {
        val f = listOf(
            Fala(1, "speaker_1", 2.0, 8.5, "E desde quando o senhor sente essa dor no ombro? Foi depois de algum esforço?"),
            Fala(2, "speaker_2", 9.0, 17.0, "Faz umas duas semanas. Ele carregou uma caixa pesada e no dia seguinte já acordou reclamando."),
            Fala(3, "speaker_3", 18.0, 26.0, "Dói mais de noite, quando deito do lado direito. De dia dá pra levar."),
            Fala(4, "speaker_2", 21.5, 27.5, "e ele não tá dormindo direito por causa disso"),
            Fala(5, "speaker_1", 29.0, 37.0, "Certo. Vou pedir um ultrassom e a gente começa com fisioterapia enquanto isso."),
            Fala(6, "speaker_3", 38.0, 44.0, "Precisa de jejum pro exame ou posso fazer a qualquer hora?"),
            Fala(7, "speaker_1", 45.0, 55.0, "Não precisa de jejum. Leva os exames antigos se tiver, ajuda a comparar."),
            Fala(8, "speaker_2", 56.0, 64.0, "Beleza, eu marco amanhã cedo e trago tudo na próxima consulta."),
            Fala(9, "speaker_1", 63.0, 70.0, "Perfeito, e qualquer piora vocês me ligam"),
            Fala(10, "speaker_3", 64.0, 69.0, "a gente liga sim, obrigado doutor"),
            Fala(11, "speaker_1", 72.0, 86.0, "Até a próxima. Se a dor aumentar muito antes do resultado, pode tomar o analgésico que eu passei, de oito em oito horas."),
        )
        return Conversa(g.chave, g.nome, g.uri.toString(), g.titulo, g.quando, DUR, emptyMap(), f)
    }
}
