package com.ambiback.manager.ui.device

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.webkit.WebViewAssetLoader
import com.ambiback.manager.utils.ClaimKeys
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

private const val FULL_CONTROL_ENTRY_ASSET = "fullcontrol/index.html"
private const val FULL_CONTROL_ENTRY_PATH = "/assets/fullcontrol/index.html"
private const val FULL_CONTROL_BASE_URL = "https://appassets.androidplatform.net$FULL_CONTROL_ENTRY_PATH"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FullControlWebView(deviceIp: String, claimKey: String? = null) {
    var webView: WebView? by remember { mutableStateOf(null) }
    val currentClaimKey by rememberUpdatedState(claimKey)

    LaunchedEffect(webView, claimKey) {
        webView?.let { injectClaimKey(it, deviceIp, claimKey) }
    }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                loadUrl("about:blank")
                stopLoading()
                destroy()
            }
            webView = null
        }
    }

    AndroidView(
        factory = { context ->
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()

            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    allowContentAccess = true
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        val requestUrl = request.url
                        return if (requestUrl.path == FULL_CONTROL_ENTRY_PATH) {
                            try {
                                createBootstrappedIndexResponse(context, deviceIp)
                            } catch (_: IOException) {
                                assetLoader.shouldInterceptRequest(requestUrl)
                            }
                        } else {
                            assetLoader.shouldInterceptRequest(requestUrl)
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val original = request.url.toString()
                        val corrected = rewriteDeviceParam(original, deviceIp)
                        return if (corrected != original) {
                            view.loadUrl(corrected)
                            true
                        } else {
                            false
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        injectClaimKey(view, deviceIp, currentClaimKey)
                        canGoBack = view.canGoBack()
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                        canGoBack = view.canGoBack()
                    }
                }

                webChromeClient = WebChromeClient()
                loadUrl(fullControlUrl(deviceIp))
                webView = this
            }
        },
        update = { view ->
            val expectedUrl = fullControlUrl(deviceIp)
            if (!view.url.orEmpty().contains("device=$deviceIp")) {
                view.loadUrl(expectedUrl)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/** Hands the device key to the embedded UI. Only validated hex keys reach the script. */
private fun injectClaimKey(view: WebView, deviceIp: String, claimKey: String?) {
    if (!ClaimKeys.isValid(claimKey) || !view.url.orEmpty().startsWith(FULL_CONTROL_BASE_URL)) return
    view.evaluateJavascript(
        "try{var k=JSON.parse(localStorage.getItem('ambiback.claimKeys')||'{}');" +
            "k['http://$deviceIp']='$claimKey';" +
            "localStorage.setItem('ambiback.claimKeys',JSON.stringify(k));}catch(e){}",
        null
    )
}

private fun fullControlUrl(deviceIp: String): String =
    "$FULL_CONTROL_BASE_URL?device=$deviceIp"

@Throws(IOException::class)
private fun createBootstrappedIndexResponse(context: Context, deviceIp: String): WebResourceResponse {
    val html = context.assets
        .open(FULL_CONTROL_ENTRY_ASSET)
        .bufferedReader(StandardCharsets.UTF_8)
        .use { it.readText() }

    val injectedBootstrap = """
        <script>
        (function() {
          var baseUrl = 'http://$deviceIp';
          try {
            localStorage.setItem('ambiback.deviceBaseUrl', baseUrl);
          } catch (error) {
            console.warn('Failed to persist AmbiBack base URL', error);
          }
        })();
        </script>
    """.trimIndent()

    val bootstrappedHtml = if (html.contains("<script type=\"module\"")) {
        html.replace("<script type=\"module\"", "$injectedBootstrap\n<script type=\"module\"")
    } else {
        html.replace("</head>", "$injectedBootstrap\n</head>")
    }

    return WebResourceResponse(
        "text/html",
        StandardCharsets.UTF_8.name(),
        ByteArrayInputStream(bootstrappedHtml.toByteArray(StandardCharsets.UTF_8))
    )
}

internal fun rewriteDeviceParam(url: String, correctIp: String): String {
    val regex = Regex("([?&]device=)[^&#]*")
    return regex.replace(url) { match ->
        "${match.groupValues[1]}$correctIp"
    }
}
