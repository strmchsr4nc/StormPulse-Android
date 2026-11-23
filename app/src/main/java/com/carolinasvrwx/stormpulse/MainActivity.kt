package com.carolinasvrwx.stormpulse

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.SafeBrowsingResponse
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.webkit.WebSettings
import android.webkit.SslErrorHandler
import android.webkit.RenderProcessGoneDetail
import android.net.http.SslError

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var errorView: TextView
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    companion object {
        private const val HOME_URL = "https://carolinasevereweatherresearch.base44.app/"
        private const val HOME_HOST = "carolinasevereweatherresearch.base44.app"
        private const val LOCATION_REQUEST = 4102
        private val APP_BACKGROUND = Color.rgb(7, 17, 31)
        private val ACCENT = Color.rgb(38, 183, 232)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = APP_BACKGROUND
        window.navigationBarColor = APP_BACKGROUND

        val root = FrameLayout(this).apply { setBackgroundColor(APP_BACKGROUND) }
        webView = WebView(this)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(24, 43, 62))
        }
        errorView = TextView(this).apply {
            text = "STORMPULSE\n\nUnable to connect. Check your internet connection, then tap here to try again."
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
            visibility = View.GONE
            setOnClickListener {
                visibility = View.GONE
                webView.reload()
            }
        }
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))
        root.addView(progress, FrameLayout.LayoutParams(-1, 5, Gravity.TOP))
        root.addView(errorView, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        configureWebView()
        publishNavigationShortcut()

        if (savedInstanceState == null) webView.loadUrl(HOME_URL) else webView.restoreState(savedInstanceState)
    }

    /** Adds navigation to the launcher icon's long-press shortcuts without changing the home screen UI. */
    private fun publishNavigationShortcut() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N_MR1) return
        val manager = getSystemService(ShortcutManager::class.java) ?: return
        val shortcut = ShortcutInfo.Builder(this, "stormpulse_navigation")
            .setShortLabel("Navigation")
            .setLongLabel("Open StormPulse Navigation")
            .setIcon(Icon.createWithResource(this, R.drawable.ic_stormpulse))
            .setIntent(Intent(this, NavigationActivity::class.java).apply {
                action = Intent.ACTION_VIEW
            })
            .build()
        manager.dynamicShortcuts = listOf(shortcut)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = false
            setGeolocationEnabled(true)
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "$userAgentString StormPulseAndroid/1.0.1"
        }
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: return true
                if (scheme == "https" && uri.host?.lowercase() == HOME_HOST) return false
                return openExternal(uri)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                errorView.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    progress.visibility = View.GONE
                    errorView.visibility = View.VISIBLE
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                progress.visibility = View.GONE
                errorView.visibility = View.VISIBLE
            }

            override fun onSafeBrowsingHit(
                view: WebView?, request: WebResourceRequest?, threatType: Int,
                callback: SafeBrowsingResponse?
            ) {
                callback?.backToSafety(true)
                progress.visibility = View.GONE
                errorView.visibility = View.VISIBLE
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                val parent = webView.parent as? FrameLayout
                parent?.removeView(webView)
                webView.destroy()
                webView = WebView(this@MainActivity)
                configureWebView()
                parent?.addView(webView, 0, FrameLayout.LayoutParams(-1, -1))
                webView.loadUrl(HOME_URL)
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                if (origin == null || callback == null) return
                val host = Uri.parse(origin).host?.lowercase()
                if (host != HOME_HOST) {
                    callback.invoke(origin, false, false)
                    return
                }
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback
                    requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        LOCATION_REQUEST
                    )
                }
            }
        }
    }

    private fun openExternal(uri: Uri): Boolean {
        return try {
            when (uri.scheme?.lowercase()) {
                "https", "http", "mailto", "tel" -> startActivity(Intent(Intent.ACTION_VIEW, uri))
                else -> return true
            }
            true
        } catch (_: Exception) {
            Toast.makeText(this, "No app is available to open this link.", Toast.LENGTH_SHORT).show()
            true
        }
    }

    @Deprecated("Retained for compatibility with the current Android permission callback")
    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST) {
            val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
            pendingGeoCallback?.invoke(pendingGeoOrigin, granted, false)
            pendingGeoCallback = null
            pendingGeoOrigin = null
            if (!granted) Toast.makeText(this, "Location access wasn't granted. StormPulse location features may be limited.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (this::webView.isInitialized) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (this::webView.isInitialized) webView.onResume()
    }

    override fun onPause() {
        if (this::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (this::webView.isInitialized) {
            pendingGeoCallback?.invoke(pendingGeoOrigin, false, false)
            pendingGeoCallback = null
            webView.stopLoading()
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }
}
