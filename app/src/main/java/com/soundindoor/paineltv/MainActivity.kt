package com.soundindoor.paineltv

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela única do app: uma WebView mostra o painel de verdade (o mesmo site que já
 * roda no navegador — clima, avisos, notícias, comerciais em imagem E vídeo).
 *
 * IMPORTANTE: o app NÃO tem mais nenhum código de TV fixo. Ele sempre aponta pra
 * página de pareamento (/tv/parear), e é essa própria página quem decide, na hora,
 * se já existe um pareamento salvo ou se precisa mostrar o QR Code/código.
 *
 * O pareamento é guardado usando SharedPreferences (armazenamento NATIVO do
 * Android) em vez do localStorage da WebView — isso porque, em testes reais, o
 * armazenamento da WebView se mostrou inconsistente em alguns aparelhos (nem
 * sempre é limpo direito ao desinstalar o app). SharedPreferences é sempre
 * limpo pelo próprio Android ao desinstalar, sem exceção, em qualquer aparelho.
 */
class MainActivity : AppCompatActivity() {

    private val urlBaseDoSistema = "https://soundindoor.duckdns.org"
    private val nomeArquivoPreferencias = "soundindoor_prefs"
    private val chaveCodigoTv = "codigo_tv_pareado"

    private lateinit var webView: WebView
    private var wakeLock: PowerManager.WakeLock? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // marca a janela como tela cheia ANTES de desenhar qualquer conteúdo — evita
        // que a WebView calcule o layout com um tamanho errado (conteúdo pequeno,
        // grudado no canto) em aparelhos de TV mais simples
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)
        ativarTelaCheiaImersiva()
        manterTelaLigada()

        webView = findViewById(R.id.webView)
        configurarWebView()
        carregarUrlCorreta()
    }

    // Decide sozinho qual URL abrir: se já tem um código de TV salvo de um
    // pareamento anterior, vai direto pra ele; senão, mostra a tela de pareamento.
    private fun carregarUrlCorreta() {
        val prefs = getSharedPreferences(nomeArquivoPreferencias, Context.MODE_PRIVATE)
        val codigoSalvo = prefs.getString(chaveCodigoTv, null)
        if (codigoSalvo != null) {
            webView.loadUrl("$urlBaseDoSistema/tv/$codigoSalvo")
        } else {
            webView.loadUrl("$urlBaseDoSistema/tv/parear")
        }
    }

    // Ponte chamada PELA PRÓPRIA PÁGINA WEB (parear.html) assim que o pareamento
    // é confirmado — window.AppSoundIndoor.salvarCodigoTv(codigo) no JavaScript.
    inner class PonteParaAndroid {
        @JavascriptInterface
        fun salvarCodigoTv(codigo: String) {
            val prefs = getSharedPreferences(nomeArquivoPreferencias, Context.MODE_PRIVATE)
            prefs.edit().putString(chaveCodigoTv, codigo).apply()
        }

        // Chamado quando o dono desconecta essa tela pelo painel (em tempo real,
        // via socket, ou pelo heartbeat) — sem isso, o app "lembrava" nativamente
        // do código antigo e pulava direto pro conteúdo de novo no próximo launch,
        // mesmo depois de já ter sido desconectado.
        @JavascriptInterface
        fun limparCodigoTv() {
            val prefs = getSharedPreferences(nomeArquivoPreferencias, Context.MODE_PRIVATE)
            prefs.edit().remove(chaveCodigoTv).apply()
        }
    }

    private fun configurarWebView() {
        val configuracoes: WebSettings = webView.settings
        configuracoes.javaScriptEnabled = true
        configuracoes.domStorageEnabled = true
        configuracoes.mediaPlaybackRequiresUserGesture = false
        configuracoes.cacheMode = WebSettings.LOAD_DEFAULT
        configuracoes.setSupportZoom(false)
        configuracoes.useWideViewPort = true
        configuracoes.loadWithOverviewMode = true
        webView.setInitialScale(0)
        // é isso que cria o "window.AppSoundIndoor" que o parear.html chama
        webView.addJavascriptInterface(PonteParaAndroid(), "AppSoundIndoor")

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                webView.postDelayed({ webView.reload() }, 5000)
            }
        }
        webView.webChromeClient = WebChromeClient()
    }

    private fun ativarTelaCheiaImersiva() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    private fun manterTelaLigada() {
        val gerenciadorDeEnergia = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = gerenciadorDeEnergia.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "PainelTV::TelaSempreLigada"
        )
        wakeLock?.acquire(12 * 60 * 60 * 1000L)
    }

    override fun onResume() {
        super.onResume()
        ativarTelaCheiaImersiva()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ativarTelaCheiaImersiva()
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        webView.destroy()
        super.onDestroy()
    }
}
