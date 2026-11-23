package br.mev.gravador

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ErroApi(val codigo: Int, msg: String) : Exception(msg)

object Mistral {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.MINUTES).readTimeout(15, TimeUnit.MINUTES).build()

    private fun corpoArquivo(ctx: Context, uri: Uri, nome: String, tamanho: Long): RequestBody {
        val tipo = when (nome.substringAfterLast('.').lowercase()) {
            "m4a", "aac" -> "audio/mp4"; "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "ogg", "opus" -> "audio/ogg"
            "flac" -> "audio/flac"; "webm" -> "audio/webm"; else -> "application/octet-stream"
        }.toMediaType()
        return object : RequestBody() {
            override fun contentType(): MediaType = tipo
            override fun contentLength(): Long = if (tamanho > 0) tamanho else -1
            override fun writeTo(sink: BufferedSink) {
                ctx.contentResolver.openInputStream(uri)!!.use { sink.writeAll(it.source()) }
            }
        }
    }

    private fun postar(req: Request): JSONObject {
        http.newCall(req).execute().use { r ->
            val txt = r.body?.string() ?: ""
            if (!r.isSuccessful) {
                val msg = try { JSONObject(txt).let { it.optString("message").ifEmpty { it.opt("detail")?.toString() ?: "" } } } catch (e: Exception) { txt.take(200) }
                throw ErroApi(r.code, "${r.code} $msg")
            }
            return JSONObject(txt)
        }
    }

    private fun transcricao(ctx: Context, g: Gravacao, extras: List<Pair<String, String>>): JSONObject {
        val b = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("model", Prefs.modelo)
        extras.forEach { (k, v) -> b.addFormDataPart(k, v) }
        b.addFormDataPart("file", g.nome, corpoArquivo(ctx, g.uri, g.nome, g.tamanho))
        val req = Request.Builder().url("${Prefs.base}/v1/audio/transcriptions")
            .header("Authorization", "Bearer ${Prefs.chave}").post(b.build()).build()
        return postar(req)
    }

    /** Transcreve com separação de vozes e, se der, com o tempo de cada palavra. */
    fun transcrever(ctx: Context, g: Gravacao, duracao: Double): Conversa {
        val j = transcricao(ctx, g, listOf("diarize" to "true", "timestamp_granularities" to "segment"))
        val segs = j.optJSONArray("segments") ?: JSONArray()
        var falas = (0 until segs.length()).mapNotNull { i ->
            val s = segs.getJSONObject(i)
            val t = s.optString("text").trim()
            if (t.isEmpty()) null else {
                val ini = s.optDouble("start", 0.0)
                Fala(i + 1, s.optString("speaker_id").ifEmpty { "speaker_1" }, ini, s.optDouble("end", ini).coerceAtLeast(ini), t)
            }
        }
        if (falas.isEmpty() && j.optString("text").isNotBlank()) falas = listOf(Fala(1, "speaker_1", 0.0, duracao, j.optString("text").trim()))

        // 2ª chamada: tempo de cada palavra (a separação de vozes só dá o tempo da frase)
        try {
            val w = transcricao(ctx, g, listOf("timestamp_granularities" to "word"))
            val ws = w.optJSONArray("segments") ?: JSONArray()
            val palavras = (0 until ws.length()).map { ws.getJSONObject(it) }
                .filter { it.optString("text").isNotBlank() }
                .map { it.optDouble("start", 0.0) to it.optDouble("end", it.optDouble("start", 0.0)) }
            falas = alinharPalavras(falas, palavras)
        } catch (e: Exception) { /* fica com o tempo estimado */ }

        val d = if (duracao > 0) duracao else (falas.maxOfOrNull { it.fim } ?: 0.0)
        return Conversa(g.chave, g.nome, g.uri.toString(), g.titulo, g.quando, d, emptyMap(), falas)
    }

    fun resumir(c: Conversa): String {
        val corpo = JSONObject().apply {
            put("model", "mistral-small-latest")
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content",
                    "Você resume conversas transcritas, em português do Brasil. Escreva um resumo curto em tópicos: assunto, pontos principais, decisões e próximos passos. Use os nomes das pessoas. Não invente nada que não esteja no texto."))
                put(JSONObject().put("role", "user").put("content", textoParaCopiar(c)))
            })
        }
        val req = Request.Builder().url("${Prefs.base}/v1/chat/completions")
            .header("Authorization", "Bearer ${Prefs.chave}")
            .post(corpo.toString().toRequestBody("application/json".toMediaType())).build()
        val j = postar(req)
        return j.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
    }
}

sealed class Estado {
    data object Nada : Estado()
    data object Transcrevendo : Estado()
    data class Erro(val msg: String) : Estado()
}

object Tarefas {
    val estados = MutableStateFlow<Map<String, Estado>>(emptyMap())

    fun transcrever(ctx: Context, g: Gravacao) {
        if (estados.value[g.chave] == Estado.Transcrevendo) return
        if (Prefs.chave.isBlank() && !Prefs.demo) { estados.update { it + (g.chave to Estado.Erro("Falta a chave da Mistral nos ajustes")) }; return }
        estados.update { it + (g.chave to Estado.Transcrevendo) }
        App.app.escopo.launch {
            var tentativa = 1
            while (true) {
                try {
                    val dur = Pasta.duracao(ctx, g)
                    val c = if (Prefs.demo) Demo.transcrever(g) else Mistral.transcrever(ctx, g, dur)
                    val velha = Banco.ler(g.chave)
                    Banco.salvar(if (velha != null) c.copy(titulo = velha.titulo, nomes = velha.nomes) else c)
                    estados.update { it - g.chave }
                    break
                } catch (e: Exception) {
                    val cod = (e as? ErroApi)?.codigo ?: 0
                    if (tentativa < 3 && cod !in setOf(400, 401, 403, 404, 413, 422)) { tentativa++; delay(4000L * tentativa); continue }
                    val msg = when (cod) {
                        401 -> "Chave da Mistral recusada"
                        413 -> "Arquivo grande demais pra Mistral"
                        0 -> "Sem conexão: ${e.message ?: ""}".trim()
                        else -> e.message ?: "Erro"
                    }
                    estados.update { it + (g.chave to Estado.Erro(msg)) }
                    break
                }
            }
        }
    }
}
