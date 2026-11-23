package br.mev.gravador

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** Envolve o ExoPlayer e expõe estado pro Compose. */
class Tocador(ctx: Context) {
    val p: ExoPlayer = ExoPlayer.Builder(ctx).build()
    var tocando by mutableStateOf(false); private set
    var posicao by mutableDoubleStateOf(0.0); private set
    var duracao by mutableDoubleStateOf(0.0); private set
    var velocidade by mutableFloatStateOf(1f); private set
    /** Trecho em loop (ini, fim) ou null. */
    var loop: Pair<Double, Double>? = null
    private var uriAtual: String? = null

    init {
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { tocando = isPlaying; atualizar() }
            override fun onPlaybackStateChanged(state: Int) {
                if (p.duration > 0) duracao = p.duration / 1000.0
                if (state == Player.STATE_ENDED) { p.pause(); p.seekTo(0) }
                atualizar()
            }
        })
    }

    fun carregar(uri: String) {
        if (uri == uriAtual) return
        uriAtual = uri
        p.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
        p.prepare()
    }

    /** Chamado a cada quadro pela tela, pra a posição andar lisa. */
    fun atualizar() {
        posicao = p.currentPosition / 1000.0
        val l = loop
        if (l != null && p.isPlaying && posicao >= l.second) { p.seekTo((l.first * 1000).toLong()); posicao = l.first }
    }

    fun alternar() { if (p.isPlaying) p.pause() else p.play() }
    fun tocar() = p.play()
    fun pausar() = p.pause()

    /** Vai pro tempo sem mudar se está tocando ou pausado. */
    fun irPara(seg: Double) {
        val s = seg.coerceAtLeast(0.0)
        p.seekTo((s * 1000).toLong()); posicao = s
    }

    fun mudarVelocidade(v: Float) { velocidade = v; p.playbackParameters = PlaybackParameters(v) }

    fun soltar() = p.release()
}
