package com.example.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GeForceNowWebView(
    url: String,
    gamepadBridge: GamepadJsBridge,
    modifier: Modifier = Modifier,
    onTitleChanged: (String) -> Unit = {},
    customUserAgent: String? = null
) {
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }

    val defaultUserAgent = customUserAgent ?: "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                GamepadWebView(context, gamepadBridge).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        userAgentString = defaultUserAgent
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = false
                        displayZoomControls = false
                        allowFileAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()

                    addJavascriptInterface(gamepadBridge, "__androidGamepadBridge")

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadingProgress = newProgress / 100f
                            isLoading = newProgress < 100
                        }

                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.grant(request.resources)
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            if (!title.isNullOrBlank()) {
                                onTitleChanged(title)
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            // Inject HTML5 Gamepad Polyfill & Bridge
                            view?.evaluateJavascript(GamepadJsBridge.GAMEPAD_POLYFILL_JS, null)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return false
                        }
                    }

                    loadUrl(url)
                }
            },
            update = { webView ->
                // Keep focus on WebView for controller inputs
                webView.requestFocus()
            }
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { loadingProgress },
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Custom WebView intercepting KeyEvents and MotionEvents from controllers
 */
@SuppressLint("ViewConstructor")
private class GamepadWebView(
    context: Context,
    private val gamepadBridge: GamepadJsBridge
) : WebView(context) {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Forward gamepad keys to both the JS Bridge and the WebView
        gamepadBridge.onNativeKeyEvent(event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        gamepadBridge.onNativeMotionEvent(event)
        return super.dispatchGenericMotionEvent(event)
    }
}
