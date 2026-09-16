package com.kostas.weathertv

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        WebView.setWebContentsDebuggingEnabled(false)
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.host == "appassets.androidplatform.net") return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        }
        webView.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && ::webView.isInitialized) {
            webView.evaluateJavascript(
                "(function(){return (window.tvBack && window.tvBack()) ? true : false;})()"
            ) { result ->
                if (result != "true") {
                    if (webView.canGoBack()) webView.goBack() else finish()
                }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
