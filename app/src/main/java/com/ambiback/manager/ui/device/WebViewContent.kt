package com.ambiback.manager.ui.device

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.ambiback.manager.R

private const val TAG = "WebViewContent"

/**
 * Embeds a WebView that opens the device's built-in React UI.
 *
 * Intercepts every navigation and rewrites the `?device=` query parameter to
 * the correct IP the Android app used to reach the device.
 *
 * @param active whether this tab is on screen; only then does Back navigate inside the page.
 * @param reloadSignal reloads the page each time the value increases.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContent(deviceIp: String, active: Boolean = true, reloadSignal: Int = 0) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }
    val homeUrl = "http://$deviceIp/"

    BackHandler(enabled = active && canGoBack) {
        webView?.goBack()
    }

    LaunchedEffect(reloadSignal) {
        if (reloadSignal > 0) {
            loadFailed = false
            webView?.reload()
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                            canGoBack = view.canGoBack()
                            if (!configured && !loadFailed) {
                                configured = true
                                view.evaluateJavascript(
                                    "localStorage.setItem('ambiback.deviceBaseUrl','http://$deviceIp');",
                                    null
                                )
                                view.reload()
                            }
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) loadFailed = true
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            canGoBack = view.canGoBack()
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress
                        }
                    }
                    loadUrl(homeUrl)
                    webView = this
                }
            },
            update = { view ->
                if (view.url != null && !view.url!!.startsWith("http://$deviceIp")) {
                    view.loadUrl(homeUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        WebLoadOverlay(
            progress = progress,
            failed = loadFailed,
            address = deviceIp,
            onRetry = {
                loadFailed = false
                webView?.loadUrl(homeUrl)
            }
        )
    }
}

/** Progress bar while a page loads, and a retry screen when the controller cannot be reached. */
@Composable
internal fun WebLoadOverlay(progress: Int, failed: Boolean, address: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (failed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                LoadErrorState(
                    message = stringResource(R.string.web_load_failed, address),
                    detail = null,
                    onRetry = onRetry
                )
            }
        } else if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }
    }
}
