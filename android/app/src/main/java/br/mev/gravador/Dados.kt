package br.mev.gravador

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
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
    val pasta: String = "Voice Recorder",
    val pastaUri: Uri? = null,
) {
    val chave: String get() = chaveDe(nome, tamanho)
    val titulo: String get() = nome.substringBeforeLast('.')
    val quando: Long get() = dataDoNome(nome) ?: Datas.de(chave) ?: modificado
    val ehChamada: Boolean get() = pasta.equals("Call", true) || nome.startsWith("Call recording", true)
    /** Nome bonito: em chamada, tira "Call recording" e a data do fim. */
    val exibicao: String get() = if (!ehChamada) titulo else contatoDaChamada(titulo)
}

fun chaveDe(nome: String, tamanho: Long) = sha("$nome|$tamanho")

fun contatoDaChamada(t: String): String {
    var s = t.replace(Regex("^(Call recording|Gravação de chamada|Chamada)\\s*", RegexOption.IGNORE_CASE), "")
    s = s.replace(Regex("[ _-]*\\d{6}[_ -]?\\d{4,6}$"), "").trim(' ', '_', '-')
    return s.ifEmpty { t }
}

fun sha(s: String): String = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

private val EXT_AUDIO = setOf("m4a", "mp3", "wav", "ogg", "opus", "aac", "amr", "3gp", "flac", "webm")

/** Uma subpasta de Recordings (Voice Recorder, Call, ou criada por você). */
data class Pastinha(val nome: String, val uri: Uri) {
    val rotulo: String get() = when {
        nome.equals("Voice Recorder", true) -> "🎙 Gravador"
        nome.equals("Call", true) -> "📞 Chamadas"
        else -> "📁 $nome"
    }
    val ordem: Int get() = when { nome.equals("Voice Recorder", true) -> 0; nome.equals("Call", true) -> 1; else -> 2 }
}

object Pasta {
    /** Abre o seletor já em Recordings (pra pegar as duas pastas de uma vez). */
    fun intentEscolher(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        val ini = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Recordings")
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, ini)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

    fun guardar(ctx: Context, arvore: Uri) {
        val cr = ctx.contentResolver
        try { cr.takePersistableUriPermission(arvore, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (e: SecurityException) { cr.takePersistableUriPermission(arvore, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        // solta a permissão antiga (ex.: só Voice Recorder)
        val velha = Prefs.pasta
        if (velha.isNotEmpty() && velha != arvore.toString()) try {
            cr.releasePersistableUriPermission(Uri.parse(velha), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) { }
        Prefs.pasta = arvore.toString()
    }

    fun temAcesso(ctx: Context): Boolean {
        if (Prefs.demo) return true
        val p = Prefs.pasta.ifEmpty { return false }
        return ctx.contentResolver.persistedUriPermissions.any { it.uri.toString() == p && it.isReadPermission }
    }

    fun podeMexer(ctx: Context): Boolean {
        if (Prefs.demo) return true
        val p = Prefs.pasta.ifEmpty { return false }
        return ctx.contentResolver.persistedUriPermissions.any { it.uri.toString() == p && it.isWritePermission }
    }

    private fun arvore(): Uri? = Prefs.pasta.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }

    fun nomePasta(): String {
        val p = Prefs.pasta.ifEmpty { return "" }
        return Uri.decode(p).substringAfterLast(':').ifEmpty { "pasta" }
    }

    /** Liberou só Voice Recorder (versão antiga do app): precisa liberar Recordings pra ver as chamadas. */
    fun soUmaPasta(): Boolean = !Prefs.demo && Prefs.pasta.isNotEmpty() && !nomePasta().equals("Recordings", true)

    private val COLS = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
    )

    private fun filhos(ctx: Context, arv: Uri, docId: String, f: (id: String, nome: String, tam: Long, mod: Long, mime: String) -> Unit) {
        val u = DocumentsContract.buildChildDocumentsUriUsingTree(arv, docId)
        ctx.contentResolver.query(u, COLS, null, null, null)?.use { c ->
            while (c.moveToNext()) f(c.getString(0), c.getString(1) ?: "", c.getLong(2), c.getLong(3), c.getString(4) ?: "")
        }
    }

    // ".trashed-..." e ".pending-..." são arquivos escondidos do Android (lixeira): não aparecem
    private fun ehAudio(nome: String, mime: String) = !nome.startsWith(".") && mime.startsWith("audio") || nome.substringAfterLast('.', "").lowercase() in EXT_AUDIO

    /** Todas as subpastas com as gravações de cada uma, mais recentes primeiro. */
    fun listarTudo(ctx: Context): List<Pair<Pastinha, List<Gravacao>>> {
        if (Prefs.demo) return Demo.pastas(ctx)
        val arv = arvore() ?: return emptyList()
        val raizId = DocumentsContract.getTreeDocumentId(arv)
        val raizUri = DocumentsContract.buildDocumentUriUsingTree(arv, raizId)
        val out = mutableListOf<Pair<Pastinha, List<Gravacao>>>()
        val soltos = mutableListOf<Gravacao>()
        val nomeRaiz = nomePasta().substringAfterLast('/')
        try {
            filhos(ctx, arv, raizId) { id, nome, tam, mod, mime ->
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val pu = DocumentsContract.buildDocumentUriUsingTree(arv, id)
                    val gs = mutableListOf<Gravacao>()
                    filhos(ctx, arv, id) { fid, fn, ft, fm, fmime ->
                        if (fmime != DocumentsContract.Document.MIME_TYPE_DIR && ehAudio(fn, fmime))
                            gs += Gravacao(DocumentsContract.buildDocumentUriUsingTree(arv, fid), fn, ft, fm, nome, pu)
                    }
                    out += Pastinha(nome, pu) to gs.sortedByDescending { it.quando }
                } else if (ehAudio(nome, mime)) {
                    soltos += Gravacao(DocumentsContract.buildDocumentUriUsingTree(arv, id), nome, tam, mod, nomeRaiz, raizUri)
                }
            }
        } catch (e: Exception) { return emptyList() }
        if (soltos.isNotEmpty()) out += Pastinha(nomeRaiz, raizUri) to soltos.sortedByDescending { it.quando }
        val ord = out.sortedWith(compareBy({ it.first.ordem }, { it.first.nome.lowercase() }))
        // conversa guarda o endereço do áudio: atualiza se o arquivo mudou de lugar
        ord.forEach { (_, gs) -> gs.forEach { g -> Banco.ler(g.chave)?.let { c -> if (c.uri != g.uri.toString()) Banco.salvar(c.copy(uri = g.uri.toString())) } } }
        return ord
    }

    private val duracoes = HashMap<String, Double>()

    fun duracaoGuardada(g: Gravacao): Double? = synchronized(duracoes) { duracoes[g.chave] }

    fun duracao(ctx: Context, g: Gravacao): Double {
        synchronized(duracoes) { duracoes[g.chave] }?.let { return it }
        val d = try {
            MediaMetadataRetriever().run {
                setDataSource(ctx, g.uri)
                val ms = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                release(); ms / 1000.0
            }
        } catch (e: Exception) { 0.0 }
        synchronized(duracoes) { duracoes[g.chave] = d }
        return d
    }
}

/** Mexe nos arquivos de verdade, na pasta do celular. */
object Arquivos {
    fun renomear(ctx: Context, g: Gravacao, novo: String): Gravacao? {
        val ext = g.nome.substringAfterLast('.', "")
        val nome = novo.trim().replace(Regex("[\\\\/:*?\"<>|]"), "-").let { if (ext.isNotEmpty() && !it.endsWith(".$ext", true)) "$it.$ext" else it }
        if (nome.isBlank() || nome == g.nome) return null
        val u = DocumentsContract.renameDocument(ctx.contentResolver, g.uri, nome) ?: return null
        val nova = g.copy(uri = u, nome = nome)
        Datas.guardar(nova.chave, g.quando) // nome novo sem data: guarda a data da gravação
        Banco.migrar(g, nova)
        return nova
    }

    fun mover(ctx: Context, gs: List<Gravacao>, destino: Pastinha): Int {
        var n = 0
        for (g in gs) {
            val origem = g.pastaUri ?: continue
            if (origem == destino.uri) continue
            val u = try { DocumentsContract.moveDocument(ctx.contentResolver, g.uri, origem, destino.uri) } catch (e: Exception) { null }
                ?: copiarEApagar(ctx, g, destino)
            if (u != null) { Banco.migrar(g, g.copy(uri = u, pasta = destino.nome, pastaUri = destino.uri)); n++ }
        }
        return n
    }

    private fun copiarEApagar(ctx: Context, g: Gravacao, destino: Pastinha): Uri? = try {
        val cr = ctx.contentResolver
        val mime = cr.getType(g.uri) ?: "audio/mp4"
        val novo = DocumentsContract.createDocument(cr, destino.uri, mime, g.nome) ?: throw Exception("sem criar")
        cr.openInputStream(g.uri)!!.use { i -> cr.openOutputStream(novo)!!.use { o -> i.copyTo(o) } }
        DocumentsContract.deleteDocument(cr, g.uri)
        novo
    } catch (e: Exception) { null }

    fun novaPasta(ctx: Context, nome: String): Pastinha? = try {
        val arv = Uri.parse(Prefs.pasta)
        val raizDoc = DocumentsContract.buildDocumentUriUsingTree(arv, DocumentsContract.getTreeDocumentId(arv))
        DocumentsContract.createDocument(ctx.contentResolver, raizDoc, DocumentsContract.Document.MIME_TYPE_DIR, nome.trim())?.let { Pastinha(nome.trim(), it) }
    } catch (e: Exception) { null }

    /** Endereços da lixeira do Android (MediaStore). Null se algum arquivo não estiver no MediaStore. */
    fun paraLixeira(ctx: Context, gs: List<Gravacao>): List<Uri>? = try {
        gs.map { g ->
            // getMediaUri devolve o endereço genérico (.../file/ID); a lixeira só aceita o de áudio (.../audio/media/ID)
            val m = MediaStore.getMediaUri(ctx, g.uri) ?: return null
            val id = m.lastPathSegment?.toLongOrNull() ?: return null
            val vol = m.pathSegments.firstOrNull() ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
            android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.getContentUri(vol), id)
        }
    } catch (e: Exception) { null }

    fun apagarDeVez(ctx: Context, gs: List<Gravacao>): Int = gs.count { g ->
        try { DocumentsContract.deleteDocument(ctx.contentResolver, g.uri) } catch (e: Exception) { false }
    }
}

/** Data original de gravações renomeadas pra um nome sem data. */
object Datas {
    private val f get() = File(App.app.filesDir, "datas.json")
    private val mapa: JSONObject by lazy { try { JSONObject(f.readText()) } catch (e: Exception) { JSONObject() } }
    @Synchronized fun de(chave: String): Long? = if (mapa.has(chave)) mapa.getLong(chave) else null
    @Synchronized fun guardar(chave: String, quando: Long) { mapa.put(chave, quando); f.writeText(mapa.toString()) }
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

    /** Arquivo mudou de nome ou de lugar: a transcrição vai junto. */
    @Synchronized fun migrar(velha: Gravacao, nova: Gravacao) {
        val c = ler(velha.chave) ?: return
        val titulo = if (c.titulo == velha.titulo) nova.titulo else c.titulo
        val n = c.copy(chave = nova.chave, arquivo = nova.nome, uri = nova.uri.toString(), titulo = titulo)
        if (velha.chave != nova.chave) { File(dir, "${velha.chave}.json").delete(); cache[velha.chave] = null }
        salvar(n)
    }

    @Synchronized fun apagar(chave: String) {
        File(dir, "$chave.json").delete(); cache[chave] = null; versao.update { it + 1 }
    }
}
