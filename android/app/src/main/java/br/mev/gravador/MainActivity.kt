package br.mev.gravador

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Modo demonstração: abre a lista já com o painel de filtro (pra tirar print). */
var filtroDemo = false

sealed class Tela {
    data object Lista : Tela()
    data object Ajustes : Tela()
    data class Conversa(val chave: String) : Tela()
    data class Editar(val chave: String, val ids: List<Int>) : Tela()
}

class MainActivity : ComponentActivity() {
    private var telaInicial: Tela = Tela.Lista

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.hasExtra("demo") == true) {
            Prefs.demo = intent.getBooleanExtra("demo", false)
            if (Prefs.demo) Demo.semear(this)
        }
        if (Prefs.demo) intent?.getStringExtra("tela")?.let { t ->
            val gs = Demo.gravacoes(this)
            telaInicial = when (t) {
                "conversa", "compartilhar" -> Tela.Conversa(gs[1].chave)
                "prova" -> Tela.Conversa(gs[0].chave)
                "editar" -> Tela.Editar(gs[1].chave, listOf(3, 4))
                "ajustes" -> Tela.Ajustes
                else -> Tela.Lista
            }
        }
        val abrirCompartilhar = Prefs.demo && intent?.getStringExtra("tela") == "compartilhar"
        filtroDemo = Prefs.demo && intent?.getStringExtra("tela") == "filtro"
        val demoPos = if (Prefs.demo) intent?.getIntExtra("pos", -1) ?: -1 else -1
        setContent { Tema { App(telaInicial, abrirCompartilhar, demoPos) } }
    }
}

@Composable
fun App(inicial: Tela, abrirCompartilhar: Boolean, demoPos: Int = -1) {
    val ctx = LocalContext.current
    val tocador = remember { Tocador(ctx) }
    DisposableEffect(Unit) { onDispose { tocador.soltar() } }
    LaunchedEffect(Unit) { if (demoPos >= 0) { kotlinx.coroutines.delay(1200); tocador.irPara(demoPos.toDouble()) } }
    var pilha by remember { mutableStateOf(if (inicial == Tela.Lista) listOf<Tela>(Tela.Lista) else listOf(Tela.Lista, inicial)) }
    val tela = pilha.last()
    fun ir(t: Tela) { pilha = pilha + t }
    fun voltar() { if (pilha.size > 1) pilha = pilha.dropLast(1) }

    var temPasta by remember { mutableStateOf(Pasta.temAcesso(ctx)) }
    var pastas by remember { mutableStateOf(emptyList<Pair<Pastinha, List<Gravacao>>>()) }
    var precisaRecordings by remember { mutableStateOf(false) }
    var recarregar by remember { mutableIntStateOf(0) }
    LaunchedEffect(temPasta, recarregar) {
        if (temPasta) pastas = withContext(Dispatchers.IO) { Pasta.listarTudo(ctx) }
        precisaRecordings = temPasta && (Pasta.soUmaPasta() || !Pasta.podeMexer(ctx))
    }
    // recarrega a lista quando o app volta pra frente (gravação nova)
    val dono = LocalLifecycleOwner.current
    DisposableEffect(dono) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) {
            temPasta = Pasta.temAcesso(ctx); recarregar++
            // voltou da tela de permissão de instalar: continua a atualização
            if (Atualizador.estado.value == Atualizador.Est.PrecisaPermitir && ctx.packageManager.canRequestPackageInstalls()) Atualizador.atualizar(ctx)
        } else if (e == Lifecycle.Event.ON_STOP) tocador.pausar() }
        dono.lifecycle.addObserver(o); onDispose { dono.lifecycle.removeObserver(o) }
    }
    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.let { Pasta.guardar(ctx, it); temPasta = true; recarregar++ }
    }

    LaunchedEffect(Unit) { Atualizador.procurar() }
    BackHandler(enabled = pilha.size > 1) { voltar() }

    Box(Modifier.fillMaxSize().background(Cor.fundo).safeDrawingPadding()) {
        when (val t = tela) {
            Tela.Lista -> TelaLista(pastas, temPasta, precisaRecordings, { escolher.launch(Pasta.intentEscolher()) }, { g -> ir(Tela.Conversa(g.chave)) }, { ir(Tela.Ajustes) }, { recarregar++ })
            Tela.Ajustes -> TelaAjustes(onVoltar = { voltar() }, onTrocarPasta = { escolher.launch(Pasta.intentEscolher()) })
            is Tela.Conversa -> TelaConversa(t.chave, tocador, abrirCompartilhar, onVoltar = { tocador.pausar(); voltar() }, onEditar = { ids -> ir(Tela.Editar(t.chave, ids)) })
            is Tela.Editar -> TelaEditar(t.chave, t.ids, tocador) { voltar() }
        }
    }
}

@Composable
fun TelaAjustes(onVoltar: () -> Unit, onTrocarPasta: () -> Unit) {
    var chave by remember { mutableStateOf(Prefs.chave) }
    var ver by remember { mutableStateOf(false) }
    var modelo by remember { mutableStateOf(Prefs.modelo) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Redondo("‹") { Prefs.chave = chave; Prefs.modelo = modelo.ifBlank { "voxtral-mini-latest" }; onVoltar() }
            Spacer(Modifier.weight(1f))
            BotaoAzul("Salvar") { Prefs.chave = chave; Prefs.modelo = modelo.ifBlank { "voxtral-mini-latest" }; onVoltar() }
        }
        Espaco(14)
        Titulo("Ajustes")
        Espaco(18)
        Text("Chave da Mistral", color = Cor.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Espaco(6)
        OutlinedTextField(
            chave, { chave = it }, singleLine = true, colors = campoCores(), modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("cole aqui", color = Cor.sub) },
            visualTransformation = if (ver) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { Text(if (ver) "ocultar" else "ver", color = Cor.azul, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp).clickable { ver = !ver }) },
        )
        Text("Fica guardada só neste celular, dentro do app.", color = Cor.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Espaco(18)
        Text("Modelo", color = Cor.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Espaco(6)
        OutlinedTextField(modelo, { modelo = it }, singleLine = true, colors = campoCores(), modifier = Modifier.fillMaxWidth())
        Espaco(18)
        Text("Pasta das gravações", color = Cor.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Espaco(6)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Pasta.nomePasta().ifEmpty { "nenhuma" }, color = Cor.sub, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            BotaoAzul("Trocar", forte = false, onClick = onTrocarPasta)
        }
        Espaco(18)
        Text("Atualização", color = Cor.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Espaco(6)
        PainelAtualizacao(sempre = true)
        Espaco(30)
    }
}

/** Cartão de atualização. Na lista só aparece quando tem versão nova; nos ajustes, sempre. */
@Composable
fun PainelAtualizacao(sempre: Boolean) {
    val ctx = LocalContext.current
    val e by Atualizador.estado.collectAsState()
    val aparece = sempre || e is Atualizador.Est.Disponivel || e is Atualizador.Est.Baixando || e is Atualizador.Est.Instalando || e is Atualizador.Est.PrecisaPermitir
    if (!aparece) return
    val versao = "Você está no build ${BuildConfig.VERSION_CODE}"
    val (texto, botao) = when (val x = e) {
        is Atualizador.Est.Disponivel -> "Versão nova: build ${x.build}" to "Atualizar"
        is Atualizador.Est.Baixando -> "Baixando build ${x.build}… ${x.pct}%" to null
        Atualizador.Est.Instalando -> "Instalando… confirme se o Android pedir" to null
        Atualizador.Est.PrecisaPermitir -> "Ligue \"Permitir desta fonte\" e volte aqui" to "Tentar de novo"
        Atualizador.Est.Procurando -> "$versao · procurando…" to null
        Atualizador.Est.EmDia -> "$versao · está em dia" to "Procurar"
        is Atualizador.Est.Erro -> x.msg to "Procurar de novo"
        Atualizador.Est.Nada -> versao to "Procurar"
    }
    val destaque = e is Atualizador.Est.Disponivel || e is Atualizador.Est.Baixando || e is Atualizador.Est.Instalando
    Cartao(Modifier.fillMaxWidth(), borda = if (destaque) Cor.azul else Cor.linha) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(texto, color = if (destaque) Cor.texto else Cor.sub, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
            if (botao != null) {
                Spacer(Modifier.width(10.dp))
                BotaoAzul(botao, forte = destaque) {
                    when (e) {
                        is Atualizador.Est.Disponivel, Atualizador.Est.PrecisaPermitir -> Atualizador.atualizar(ctx)
                        else -> Atualizador.procurar()
                    }
                }
            }
        }
    }
}
