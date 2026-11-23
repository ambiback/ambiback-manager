package com.ambiback.manager.ui.device

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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

private const val TAG = "WebViewContent"

/**
 * Embeds a WebView that opens the device's built-in React UI.
 *
 * Intercepts every navigation and rewrites the `?device=` query parameter to
 * the correct IP the Android app used to reach the device.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContent(deviceIp: String) {
    var webView: WebView? by remember { mutableStateOf(null) }
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
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                }

                var configured = false
                webViewClient = object : WebViewClient() {

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val original = request.url.toString()
                        Log.d(TAG, "shouldOverrideUrlLoading: $original")

                        val corrected = rewriteDeviceParam(original, deviceIp)
                        return if (corrected != original) {
                            Log.d(TAG, "Rewrote device param → $corrected")
                            view.loadUrl(corrected)
                            true
                        } else {
                            false
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        Log.d(TAG, "onPageFinished: $url (configured=$configured)")
                        canGoBack = view.canGoBack()
                        if (!configured) {
                            configured = true
                            view.evaluateJavascript(
                                "localStorage.setItem('ambiback.deviceBaseUrl','http://$deviceIp');",
                                null
                            )
                            view.reload()
                        }
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                        canGoBack = view.canGoBack()
                    }
                }
                webChromeClient = WebChromeClient()
                loadUrl("http://$deviceIp/")
                webView = this
            }
        },
        update = { view ->
            if (view.url != null && !view.url!!.startsWith("http://$deviceIp")) {
                view.loadUrl("http://$deviceIp/")
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
