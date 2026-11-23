package br.mev.gravador

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class App : Application() {
    val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() { super.onCreate(); app = this }
    companion object { lateinit var app: App }
}

object Prefs {
    private val p get() = App.app.getSharedPreferences("gravador", Context.MODE_PRIVATE)
    var chave: String get() = p.getString("chave", "") ?: ""; set(v) = p.edit().putString("chave", v.trim()).apply()
    var modelo: String get() = p.getString("modelo", "voxtral-mini-latest") ?: "voxtral-mini-latest"; set(v) = p.edit().putString("modelo", v.trim()).apply()
    var pasta: String get() = p.getString("pasta", "") ?: ""; set(v) = p.edit().putString("pasta", v).apply()
    var demo: Boolean get() = p.getBoolean("demo", false); set(v) = p.edit().putBoolean("demo", v).apply()
    var base: String get() = p.getString("base", "https://api.mistral.ai") ?: "https://api.mistral.ai"; set(v) = p.edit().putString("base", v.trim().trimEnd('/')).apply()
}

/** Uma gravação achada na pasta. */
data class Gravacao(
    val uri: Uri,
    val nome: String,
    val tamanho: Long,
    val modificado: Long,
) {
    val chave: String get() = sha("$nome|$tamanho")
    val titulo: String get() = nome.substringBeforeLast('.')
    val quando: Long get() = dataDoNome(nome) ?: modificado
}

fun sha(s: String): String = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

private val EXT_AUDIO = setOf("m4a", "mp3", "wav", "ogg", "opus", "aac", "amr", "3gp", "flac", "webm")

object Pasta {
    /** Abre o seletor já na pasta do gravador da Samsung. */
    fun intentEscolher(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        val ini = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Recordings/Voice Recorder")
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, ini)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

    fun guardar(ctx: Context, arvore: Uri) {
        ctx.contentResolver.takePersistableUriPermission(arvore, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        Prefs.pasta = arvore.toString()
    }

    fun temAcesso(ctx: Context): Boolean {
        if (Prefs.demo) return true
        val p = Prefs.pasta.ifEmpty { return false }
        return ctx.contentResolver.persistedUriPermissions.any { it.uri.toString() == p && it.isReadPermission }
    }

    fun nomePasta(): String {
        val p = Prefs.pasta.ifEmpty { return "" }
        return Uri.decode(p).substringAfterLast(':').ifEmpty { "pasta" }
    }

    fun listar(ctx: Context): List<Gravacao> {
        if (Prefs.demo) return Demo.gravacoes(ctx)
        val arvore = Uri.parse(Prefs.pasta.ifEmpty { return emptyList() })
        val docId = DocumentsContract.getTreeDocumentId(arvore)
        val filhos = DocumentsContract.buildChildDocumentsUriUsingTree(arvore, docId)
        val out = mutableListOf<Gravacao>()
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        try {
            ctx.contentResolver.query(filhos, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val nome = c.getString(1) ?: continue
                    val mime = c.getString(4) ?: ""
                    val ext = nome.substringAfterLast('.', "").lowercase()
                    if (!mime.startsWith("audio") && ext !in EXT_AUDIO) continue
                    out += Gravacao(DocumentsContract.buildDocumentUriUsingTree(arvore, c.getString(0)), nome, c.getLong(2), c.getLong(3))
                }
            }
        } catch (e: Exception) { return emptyList() }
        return out.sortedByDescending { it.quando }
    }

    private val duracoes = HashMap<String, Double>()

    fun duracao(ctx: Context, g: Gravacao): Double {
        duracoes[g.chave]?.let { return it }
        val d = try {
            MediaMetadataRetriever().run {
                setDataSource(ctx, g.uri)
                val ms = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                release(); ms / 1000.0
            }
        } catch (e: Exception) { 0.0 }
        duracoes[g.chave] = d
        return d
    }
}

object Banco {
    private val dir get() = File(App.app.filesDir, "conversas").apply { mkdirs() }
    private val cache = HashMap<String, Conversa?>()
    /** Muda quando alguma conversa é salva (pra telas recarregarem). */
    val versao = MutableStateFlow(0)

    @Synchronized fun ler(chave: String): Conversa? = cache.getOrPut(chave) {
        val f = File(dir, "$chave.json")
        if (!f.exists()) null else try { Conversa.de(JSONObject(f.readText())) } catch (e: Exception) { null }
    }

    @Synchronized fun salvar(c: Conversa) {
        val tmp = File(dir, "${c.chave}.tmp"); tmp.writeText(c.json().toString())
        tmp.renameTo(File(dir, "${c.chave}.json"))
        cache[c.chave] = c
        versao.update { it + 1 }
    }

    @Synchronized fun apagar(chave: String) {
        File(dir, "$chave.json").delete(); cache[chave] = null; versao.update { it + 1 }
    }
}
