package com.soundindoor.paineltv

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
        // habilita inspeção remota via Chrome DevTools (chrome://inspect, com o
        // aparelho ligado por USB/ADB) — sem isso, um problema como a "tela
        // branca" fica impossível de diagnosticar de verdade, porque não tinha
        // como ver o que estava acontecendo dentro da WebView
        WebView.setWebContentsDebuggingEnabled(true)

        val configuracoes: WebSettings = webView.settings
        configuracoes.javaScriptEnabled = true
        configuracoes.domStorageEnabled = true
        configuracoes.mediaPlaybackRequiresUserGesture = false
        configuracoes.cacheMode = WebSettings.LOAD_DEFAULT
        configuracoes.setSupportZoom(false)
        // acrescenta um identificador ao User-Agent — o próprio site (tv-boot.js)
        // usa isso pra saber se está rodando dentro do app (em vez de um navegador
        // comum) e aplicar ajustes específicos. Sem isso, o app nunca era
        // reconhecido como "app de verdade" pelo lado do site.
        configuracoes.userAgentString = configuracoes.userAgentString + " PainelTVAndroidApp"
        // useWideViewPort/loadWithOverviewMode servem pra "encolher" sites feitos
        // pra computador (largura fixa) até caberem numa tela pequena — mas a
        // página do painel já é responsiva de verdade (usa vh/vw e já declara
        // <meta name="viewport" content="width=device-width, initial-scale=1.0">
        // no próprio HTML). Em alguns aparelhos com densidade de tela incomum,
        // essa combinação fazia o conteúdo renderizar pequeno, grudado num canto —
        // removendo essas duas opções, a WebView respeita só a meta tag da própria
        // página, que já está correta.
        // é isso que cria o "window.AppSoundIndoor" que o parear.html chama
        webView.addJavascriptInterface(PonteParaAndroid(), "AppSoundIndoor")

        webView.webViewClient = object : WebViewClient() {
            // Usa a versão do onReceivedError que recebe a REQUISIÇÃO (não só a
            // URL) — isso permite saber se o erro foi no DOCUMENTO PRINCIPAL ou
            // num recurso secundário qualquer (uma fonte do Google Fonts, um
            // ícone, etc). Antes, QUALQUER erro (até de recurso secundário)
            // disparava um recarregamento da página inteira — o que podia virar
            // um ciclo de recarregar sem parar se, por exemplo, só a fonte
            // estivesse indisponível, sem nada de errado com o painel em si.
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                val eDocumentoPrincipal = request?.isForMainFrame ?: true
                Log.e("SoundIndoorWebView", "onReceivedError: ${error?.description} (código ${error?.errorCode}, url: ${request?.url}, documento principal: $eDocumentoPrincipal)")
                if(eDocumentoPrincipal){
                    webView.postDelayed({ webView.reload() }, 5000)
                }
            }

            // Em aparelhos mais fracos, o processo de renderização da WebView
            // (que roda separado do app) pode ser encerrado pelo sistema por
            // falta de memória/GPU — sem tratar isso, a tela simplesmente fica
            // congelada/branca pra sempre, porque o app nem percebe que o
            // renderizador morreu. Só dar reload() na MESMA instância de WebView
            // não é seguro depois desse evento (o processo dela já morreu) — o
            // certo é descartar essa WebView e criar uma nova do zero.
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                Log.e("SoundIndoorWebView", "Renderizador da WebView morreu (crash: ${detail?.didCrash()}, prioridade: ${detail?.rendererPriorityAtExit()}) — recriando a WebView do zero.")
                recriarWebViewAposCrash()
                return true // avisa o Android que já tratamos o problema, não deixa ele derrubar o app inteiro
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // manda todo log do console JavaScript (inclusive erros) pro logcat
            // do Android — antes disso, um erro tipo "Cannot read properties of
            // null" na rotação dos painéis simplesmente desaparecia sem deixar
            // rastro nenhum, e a tela ficava branca sem explicação visível.
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.e(
                    "SoundIndoorWebView",
                    "${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})"
                )
                return true
            }
        }
    }

    // Depois que o processo de renderização morre, a WebView antiga não serve
    // mais — precisa sair da tela, ser destruída, e uma NOVA WebView entrar no
    // lugar dela (mesma posição na hierarquia de telas, mesmo tamanho). Não
    // precisamos saber os detalhes do layout XML: pegamos essas informações da
    // própria WebView antiga, ANTES de destruí-la.
    private fun recriarWebViewAposCrash() {
        val parent = webView.parent as? ViewGroup
        val layoutParams = webView.layoutParams
        val posicaoNaTela = parent?.indexOfChild(webView) ?: -1

        parent?.removeView(webView)
        webView.destroy()

        webView = WebView(this)
        webView.layoutParams = layoutParams
        if (parent != null) {
            if (posicaoNaTela >= 0) parent.addView(webView, posicaoNaTela) else parent.addView(webView)
        }

        configurarWebView()
        carregarUrlCorreta()
    }

    private fun ativarTelaCheiaImersiva() {
        // A partir do Android 11 (API 30), o jeito CORRETO e mais confiável de
        // esconder as barras de sistema é o WindowInsetsController — as flags
        // antigas (SYSTEM_UI_FLAG_*) são descontinuadas, e em alguns firmwares
        // genéricos de TV Box mais simples elas podem não esconder a barra de
        // verdade, fazendo a WebView calcular um espaço disponível MENOR que a
        // tela real (efeito de "conteúdo encolhido"). Mantém as flags antigas
        // só como reserva pra Android mais velho, que não tem a API nova.
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controlador ->
                controlador.hide(WindowInsets.Type.systemBars())
                controlador.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
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
