package com.luzzymeow.luzzyrp

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.luzzymeow.luzzyrp.util.AssetExtractor
import com.luzzymeow.luzzyrp.web.AnysearchBridge
import com.luzzymeow.luzzyrp.web.DownloadHandler
import com.luzzymeow.luzzyrp.web.FileChooserHandler
import com.luzzymeow.luzzyrp.web.WebViewSetup
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private val fileChooserHandler = FileChooserHandler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false)
        }

        val entryFile = File(AssetExtractor.ensureExtracted(this), "index.html")

        webView = WebView(this)
        WebViewSetup.configure(webView)
        webView.setBackgroundColor(Color.WHITE)
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.addJavascriptInterface(AnysearchBridge(webView), "LuzzyAnysearchNative")
        webView.webViewClient = WebViewClient()
        DownloadHandler.attach(webView)
        webView.webChromeClient = fileChooserHandler.webChromeClient()
        webView.loadUrl("file://" + entryFile.absolutePath)

        root = FrameLayout(this).apply {
            addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        fileChooserHandler.handleActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        root.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}
