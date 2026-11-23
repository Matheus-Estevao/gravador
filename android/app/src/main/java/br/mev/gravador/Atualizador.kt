package br.mev.gravador

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Atualiza o app por dentro: baixa o APK assinado do GitHub e instala por cima. */
object Atualizador {
    private const val PADRAO = "https://raw.githubusercontent.com/Matheus-Estevao/gravador/atualizacao/"
    /** Só pra teste no emulador (extra url_atualizacao); no celular é sempre o GitHub. */
    var base = PADRAO
    private val BASE get() = base

    sealed class Est {
        data object Nada : Est()
        data object Procurando : Est()
        data object EmDia : Est()
        data class Disponivel(val build: Int) : Est()
        data class Baixando(val build: Int, val pct: Int) : Est()
        data object Instalando : Est()
        data object PrecisaPermitir : Est()
        data class Erro(val msg: String) : Est()
    }

    val estado = MutableStateFlow<Est>(Est.Nada)
    private var ultimo = 0
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    fun procurar() {
        if (Prefs.demo && base == PADRAO) return
        val e = estado.value
        if (e is Est.Baixando || e is Est.Instalando || e is Est.Procurando) return
        estado.value = Est.Procurando
        App.app.escopo.launch {
            estado.value = try {
                val txt = http.newCall(Request.Builder().url(BASE + "versao.txt?t=" + System.currentTimeMillis()).build()).execute().use { r ->
                    if (!r.isSuccessful) throw Exception("HTTP ${r.code}"); r.body!!.string()
                }
                val n = txt.trim().toInt(); ultimo = n
                if (n > BuildConfig.VERSION_CODE) Est.Disponivel(n) else Est.EmDia
            } catch (e: Exception) { Est.Erro("Sem conexão pra procurar atualização") }
        }
    }

    fun atualizar(ctx: Context) {
        val pm = ctx.packageManager
        if (!pm.canRequestPackageInstalls()) {
            estado.value = Est.PrecisaPermitir
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val n = ultimo
        estado.value = Est.Baixando(n, 0)
        App.app.escopo.launch {
            try {
                val f = File(ctx.cacheDir, "atualizacao.apk")
                http.newCall(Request.Builder().url(BASE + "Gravador.apk?t=" + System.currentTimeMillis()).build()).execute().use { r ->
                    if (!r.isSuccessful) throw Exception("HTTP ${r.code}")
                    val body = r.body!!; val total = body.contentLength()
                    body.byteStream().use { i -> f.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024); var lido = 0L; var k: Int
                        while (i.read(buf).also { k = it } >= 0) {
                            o.write(buf, 0, k); lido += k
                            if (total > 0) estado.value = Est.Baixando(n, (lido * 100 / total).toInt())
                        }
                    } }
                }
                estado.value = Est.Instalando
                withContext(Dispatchers.Main) { instalar(ctx, f) }
            } catch (e: Exception) { estado.value = Est.Erro("Não deu pra baixar: ${e.message ?: "erro"}") }
        }
    }

    /** Entrega o arquivo pro instalador padrão do Android (o mesmo de abrir o APK pelo gerenciador de arquivos). */
    private fun instalar(ctx: Context, f: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.arquivos", f)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** Voltou pro app sem ter atualizado (cancelou): mostra o botão de novo. */
    fun voltou() { if (estado.value == Est.Instalando) estado.value = Est.Disponivel(ultimo) }

    /** Volta pra "tem versão nova" quando a instalação é cancelada (o app só fecha quando dá certo). */
    fun falhou(msg: String?) { estado.value = if (msg == null) Est.Disponivel(ultimo) else Est.Erro(msg) }
}

