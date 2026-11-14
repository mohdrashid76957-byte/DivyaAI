package com.divya.ai

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

/** v3.8: Divya-design toy avatars (assets/divya-toys). Gallery se chuno, floating window mein dikhe. */
object ToyPrefs {
    const val DEFAULT = "div3d"
    fun name(c: Context) = c.getSharedPreferences("divya", 0).getString("toyAvatar", DEFAULT) ?: DEFAULT
    fun setName(c: Context, n: String) { c.getSharedPreferences("divya", 0).edit().putString("toyAvatar", n).apply() }
    fun enabled(c: Context) = c.getSharedPreferences("divya", 0).getBoolean("toyOverlay", false)
    fun setEnabled(c: Context, on: Boolean) { c.getSharedPreferences("divya", 0).edit().putBoolean("toyOverlay", on).apply() }
    fun url(n: String) = "https://appassets.androidplatform.net/assets/divya-toys/$n.html"
}

/** Gallery screen: toy chuno (bridge se pref save). */
class OverlayToysActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var loader: WebViewAssetLoader

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            addJavascriptInterface(PickerBridge(), "DivyaToyPicker")   // gallery.html isi naam se call karta hai
            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    select(ToyPrefs.name(this@OverlayToysActivity))
                }
            }
        }
        setContentView(web)
        web.loadUrl("https://appassets.androidplatform.net/assets/divya-toys/gallery.html")
    }

    private fun select(n: String) { web.evaluateJavascript("window.setSelectedAvatar && window.setSelectedAvatar('$n')", null) }

    private inner class PickerBridge {
        @JavascriptInterface fun pageReady() { runOnUiThread { select(ToyPrefs.name(this@OverlayToysActivity)) } }
        @JavascriptInterface fun selectAvatar(name: String) {
            runOnUiThread {
                ToyPrefs.setName(this@OverlayToysActivity, name)
                select(name)
                FloatingToy.refresh(this@OverlayToysActivity)   // floating toy ho to naya toy dikhe
            }
        }
    }

    override fun onDestroy() {
        if (::web.isInitialized) { web.removeJavascriptInterface("DivyaToyPicker"); web.stopLoading(); web.destroy() }
        super.onDestroy()
    }
}

/** Floating toy: TYPE_APPLICATION_OVERLAY window, touch pass-through (screen ko block nahi karta). */
object FloatingToy {
    private var view: WebView? = null
    private var shownName: String? = null

    fun canDraw(c: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(c)

    /** Setting ke hisaab se dikhao / chhupao. */
    fun sync(c: Context) { if (ToyPrefs.enabled(c) && canDraw(c)) show(c) else hide(c) }

    fun refresh(c: Context) { if (view != null) { hide(c); sync(c) } }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun show(c: Context) {
        if (view != null) return
        val app = c.applicationContext
        val name = ToyPrefs.name(app)
        val wm = app.getSystemService(WindowManager::class.java)
        val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(app)).build()
        val wv = WebView(app)
        wv.setBackgroundColor(Color.TRANSPARENT)
        wv.settings.javaScriptEnabled = true
        wv.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)
        }
        wv.loadUrl(ToyPrefs.url(name))
        val size = (160 * app.resources.displayMetrics.density).toInt()
        val lp = WindowManager.LayoutParams(
            size, size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.END
        lp.x = 16
        lp.y = (120 * app.resources.displayMetrics.density).toInt()
        try { wm.addView(wv, lp); view = wv; shownName = name } catch (_: Exception) { wv.destroy() }
    }

    fun hide(c: Context) {
        val wv = view ?: return
        try { c.applicationContext.getSystemService(WindowManager::class.java).removeView(wv) } catch (_: Exception) {}
        try { wv.destroy() } catch (_: Exception) {}
        view = null; shownName = null
    }
}
