# Painel TV Indoor — App Android TV

App nativo que mostra o painel (o mesmo site já em produção) numa WebView, mas usa o
player de vídeo nativo do Android (Media3/ExoPlayer) pra tocar os comerciais em vídeo —
resolve o travamento/tela preta que acontecia em navegadores mais fracos (Open Browser
e similares) em TVs, TV boxes e sticks mais simples.

## Antes de compilar: configure a URL da sua TV

Abra `app/src/main/java/com/soundindoor/paineltv/MainActivity.kt` e troque a linha:

```kotlin
private val urlPadraoDaTv = "https://soundindoor.duckdns.org/tv/SEU_CODIGO_AQUI"
```

Coloque o link de exibição real do cliente (o mesmo que você usa hoje no navegador,
`https://soundindoor.duckdns.org/tv/{codigo}`).

> Se você tem clientes diferentes em aparelhos diferentes, precisa gerar um APK
> separado pra cada um (troque a URL, suba de novo, baixe o APK novo). Se isso virar
> um incômodo com muitos clientes, me avise — dá pra evoluir isso depois pra pedir a
> URL na primeira vez que o app abre, salva, e não precisa mais mexer no código.

## Como compilar (sem instalar nada no computador)

1. Crie um repositório novo no GitHub (pode ser privado)
2. Suba todos os arquivos desta pasta pra esse repositório
3. Vá na aba **Actions** do repositório — o GitHub já começa a compilar sozinho
   (leva uns 3-5 minutos)
4. Quando terminar (ícone verde ✓), clique na execução → role até "Artifacts" →
   baixe **painel-tv-apk** (vem como um `.zip` contendo o `app-debug.apk` dentro)

## Como instalar num aparelho Android TV / TV box / stick

1. Copie o `.apk` pra um pendrive, ou use um app tipo "Send Files to TV" / "Downloader"
   pra baixar direto no aparelho
2. No aparelho, pode ser necessário ativar "Fontes desconhecidas" (Configurações →
   Segurança) antes de instalar
3. Abre o arquivo `.apk` no próprio aparelho e instala
4. Abre o app "Painel TV Indoor" — ele já entra direto no seu painel, em tela cheia
5. Ele liga sozinho quando o aparelho reiniciar/ligar de novo

## O que esse app resolve especificamente
- Vídeo tocado pelo **Media3/ExoPlayer** nativo do Android (muito mais compatível
  com hardware fraco do que qualquer navegador embutido)
- Tela sempre ligada (sem economia de energia apagando)
- Tela cheia de verdade, sem barra de navegação/status por cima
- Liga sozinho no boot do aparelho
- Recarrega sozinho se a página cair por qualquer motivo

## O que esse app NÃO muda
Todo o resto continua exatamente igual — clima, cotações, avisos, notícias, comerciais
em imagem, sincronização com spot da rádio — tudo isso continua vindo do mesmo site,
só que mostrado dentro de uma WebView em vez de um navegador solto. Qualquer alteração
que você fizer no admin (`/admin.html`) aparece automaticamente no app também, sem
precisar recompilar nada.
