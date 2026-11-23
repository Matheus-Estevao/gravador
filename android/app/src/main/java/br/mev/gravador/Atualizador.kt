package br.mev.gravador

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
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
    private const val BASE = "https://raw.githubusercontent.com/Matheus-Estevao/gravador/atualizacao/"

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
        if (Prefs.demo) return
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
                withContext(Dispatchers.IO) { instalar(ctx, f) }
            } catch (e: Exception) { estado.value = Est.Erro("Não deu pra baixar: ${e.message ?: "erro"}") }
        }
    }

    private fun instalar(ctx: Context, f: File) {
        val inst = ctx.packageManager.packageInstaller
        val p = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = inst.createSession(p)
        inst.openSession(id).use { s ->
            s.openWrite("app.apk", 0, f.length()).use { o -> f.inputStream().use { it.copyTo(o) }; s.fsync(o) }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, Instalou::class.java), flags)
            s.commit(pi.intentSender)
        }
    }

    /** Volta pra "tem versão nova" quando a instalação é cancelada (o app só fecha quando dá certo). */
    fun falhou(msg: String?) { estado.value = if (msg == null) Est.Disponivel(ultimo) else Est.Erro(msg) }
}

class Instalou : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirma = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirma?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { ctx.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Atualizador.falhou(null)
            else -> Atualizador.falhou("Não instalou: " + (i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "erro"))
        }
    }
}
