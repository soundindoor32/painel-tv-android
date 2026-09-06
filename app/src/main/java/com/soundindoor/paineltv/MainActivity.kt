package com.soundindoor.paineltv

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela única do app: uma WebView mostra o painel de verdade (o mesmo site que já
 * roda no navegador — clima, avisos, notícias, comerciais em imagem E vídeo).
 *
 * Essa é a versão mais simples possível: sem nenhum player nativo separado (nem
 * ExoPlayer, nem MediaPlayer/VideoView) — o vídeo toca direto pela tag <video> do
 * HTML, exatamente como já funciona perfeito no Chrome do computador. A WebView do
 * Android usa o mesmo motor (Chromium) que o Chrome.
 */
class MainActivity : AppCompatActivity() {

    // TROQUE AQUI pela URL real da sua TV, se quiser fixar direto no app.
    private val urlPadraoDaTv = "https://soundindoor.duckdns.org/tv/parear"

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
        webView.loadUrl(urlPadraoDaTv)
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
