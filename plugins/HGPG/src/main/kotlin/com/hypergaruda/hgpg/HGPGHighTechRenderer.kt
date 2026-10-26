package com.hypergaruda.hgpg

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import com.aliucord.api.PatcherAPI
import com.aliucord.api.SettingsAPI
import com.aliucord.patcher.Hook
import com.aliucord.patcher.PreHook
import java.util.WeakHashMap

/** Native WebView transitions, short text reveals, and transient high-tech rendering. */
internal object HGPGHighTechRenderer {
    private const val TAG = "HGPG-HighTech"
    private val textGuards = WeakHashMap<TextView, Boolean>()
    private val lastText = WeakHashMap<TextView, String>()
    private val webLoads = WeakHashMap<WebView, Long>()
    @Volatile private var theme = HGPGThemes.get("garuda")
    @Volatile private var installed = false

    fun start(context: Context, settings: SettingsAPI, patcher: PatcherAPI) {
        theme = HGPGThemes.get(readString(settings.getString("theme_profile", "garuda")))
        if (installed) return
        installTextHooks(patcher)
        installWebHooks(patcher)
        installed = true
    }

    fun setTheme(value: HGPGTheme) { theme = value }

    fun attach(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        root.post { scanForWebViews(root) }
    }

    private fun scanForWebViews(view: View) {
        if (view is WebView && view.isAttachedToWindow) {
            prepareHardware(view)
            view.postDelayed({
                if (view.isAttachedToWindow && view.alpha < 0.99f) revealWebView(view)
            }, 1800L)
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) scanForWebViews(view.getChildAt(i))
    }

    fun stop() {
        synchronized(textGuards) { textGuards.clear() }
        synchronized(lastText) { lastText.clear() }
        synchronized(webLoads) { webLoads.clear() }
        installed = false
    }

    private fun installTextHooks(patcher: PatcherAPI) {
        attempt("TextView.setText") {
            patcher.patch(TextView::class.java.getDeclaredMethod(
                "setText", CharSequence::class.java, TextView.BufferType::class.java
            ), Hook { call ->
                val view = call.thisObject as? TextView ?: return@Hook
                if (view is EditText || view.javaClass.name.startsWith("com.hypergaruda.hgpg.")) return@Hook
                if (synchronized(textGuards) { textGuards[view] == true }) return@Hook
                if (!view.isAttachedToWindow || view.length() !in 1..72) return@Hook
                val current = view.text ?: return@Hook
                val value = current.toString()
                if (value.isBlank() || value.length > 72 ||
                    value.matches(Regex("[0-9:.,/%+\\- ]{1,24}"))) return@Hook
                val previous = synchronized(lastText) {
                    val old = lastText[view]
                    lastText[view] = value
                    old
                }
                if (previous == value) return@Hook
                view.post { revealText(view) }
            })
        }
    }

    private fun revealText(view: TextView) {
        if (!view.isAttachedToWindow || view is EditText || view.length() !in 1..72) return
        val source = view.text ?: return
        val text = source.toString()
        if (text.isBlank() || text.length > 72) return
        val spannable = SpannableString(source)
        val spans = ArrayList<AlphaCharacterSpan>(text.length)
        for (i in text.indices) {
            val span = AlphaCharacterSpan()
            spannable.setSpan(span, i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spans.add(span)
        }
        synchronized(textGuards) { textGuards[view] = true }
        try { view.setText(spannable, TextView.BufferType.SPANNABLE) }
        catch (_: Throwable) { return }
        finally { synchronized(textGuards) { textGuards.remove(view) } }

        val duration = when (theme.motion) {
            HGPGMotion.CRT_PS2 -> 170L
            HGPGMotion.LED_ORANGE -> 230L
            HGPGMotion.GT_SPORT -> 190L
            HGPGMotion.GT_OLD -> 320L
            else -> 260L
        }
        prepareHardware(view)
        ValueAnimator.ofInt(0, text.length).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val visible = animation.animatedValue as Int
                spans.forEachIndexed { index, span -> span.alpha = if (index < visible) 255 else 0 }
                view.invalidate()
            }
            start()
        }
        view.postDelayed({ releaseHardware(view) }, duration + 40L)
    }

    private fun installWebHooks(patcher: PatcherAPI) {
        attempt("WebView.loadUrl") {
            patcher.patch(WebView::class.java.getDeclaredMethod("loadUrl", String::class.java),
                PreHook { call -> (call.thisObject as? WebView)?.let { prepareWebView(it) } })
        }
        attempt("WebView.loadUrl headers") {
            patcher.patch(WebView::class.java.getDeclaredMethod("loadUrl", String::class.java, Map::class.java),
                PreHook { call -> (call.thisObject as? WebView)?.let { prepareWebView(it) } })
        }
        attempt("WebView.loadDataWithBaseURL") {
            patcher.patch(WebView::class.java.getDeclaredMethod(
                "loadDataWithBaseURL", String::class.java, String::class.java,
                String::class.java, String::class.java, String::class.java
            ), PreHook { call -> (call.thisObject as? WebView)?.let { prepareWebView(it) } })
        }
        attempt("WebViewClient.onPageFinished") {
            val client = Class.forName("android.webkit.WebViewClient")
            patcher.patch(client.getDeclaredMethod("onPageFinished", WebView::class.java, String::class.java),
                Hook { call -> (call.args.firstOrNull() as? WebView)?.let { revealWebView(it) } })
        }
    }

    private fun prepareWebView(web: WebView) {
        if (!web.isAttachedToWindow) return
        synchronized(webLoads) { webLoads[web] = SystemClock.uptimeMillis() }
        prepareHardware(web)
        web.animate().cancel()
        web.alpha = 0.72f
        web.scaleX = 0.99f
        web.scaleY = 0.99f
        setBlur(web, 5f)
        web.postDelayed({ if (web.isAttachedToWindow && web.alpha < 0.99f) revealWebView(web) }, 1800L)
    }

    private fun revealWebView(web: WebView) {
        if (!web.isAttachedToWindow) return
        web.post {
            if (!web.isAttachedToWindow) return@post
            val started = synchronized(webLoads) { webLoads[web] ?: 0L }
            val delay = (90L - (SystemClock.uptimeMillis() - started)).coerceAtLeast(0L)
            web.postDelayed({
                if (!web.isAttachedToWindow) return@postDelayed
                web.animate().cancel()
                animateBlur(web, 5f, 0f, 220L)
                web.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220L)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { setBlur(web, 0f); releaseHardware(web) }.start()
            }, delay)
        }
    }

    private fun prepareHardware(view: View) {
        try {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            if (view.cameraDistance <= 0f) view.cameraDistance = 8000f * view.resources.displayMetrics.density
        } catch (_: Throwable) {}
    }

    private fun releaseHardware(view: View) {
        try { if (view.isAttachedToWindow) view.setLayerType(View.LAYER_TYPE_NONE, null) } catch (_: Throwable) {}
    }

    private fun setBlur(view: View, radius: Float) {
        if (Build.VERSION.SDK_INT < 31) return
        try {
            val effectClass = Class.forName("android.graphics.RenderEffect")
            val effect = if (radius <= 0f) null else effectClass.getMethod(
                "createBlurEffect", java.lang.Float.TYPE, java.lang.Float.TYPE,
                Class.forName("android.graphics.Shader\$TileMode")
            ).invoke(null, radius, radius, android.graphics.Shader.TileMode.CLAMP)
            View::class.java.getMethod("setRenderEffect", effectClass).invoke(view, effect)
        } catch (_: Throwable) { /* Optional Android 12+ enhancement. */ }
    }

    private fun animateBlur(view: View, from: Float, to: Float, duration: Long) {
        if (Build.VERSION.SDK_INT < 31) return
        try {
            ValueAnimator.ofFloat(from, to).apply {
                this.duration = duration
                interpolator = DecelerateInterpolator()
                addUpdateListener { setBlur(view, it.animatedValue as Float) }
                start()
            }
        } catch (_: Throwable) {}
    }

    private class AlphaCharacterSpan : CharacterStyle() {
        var alpha: Int = 0
        override fun updateDrawState(tp: TextPaint) {
            tp.alpha = (tp.alpha * alpha / 255).coerceIn(0, 255)
        }
    }

    private fun readString(value: Any?): String {
        if (value == null) return "garuda"
        return try {
            val type = CharSequence::class.java
            val length = type.getMethod("length").invoke(value) as Int
            val charAt = type.getMethod("charAt", Integer.TYPE)
            val out = StringBuilder(length.coerceAtLeast(0))
            var i = 0
            while (i < length) { out.append(charAt.invoke(value, i) as Char); i++ }
            out.toString()
        } catch (_: Throwable) { try { value.toString() } catch (_: Throwable) { "garuda" } }
    }

    private fun attempt(label: String, action: () -> Unit) {
        try { action() } catch (error: Throwable) {
            android.util.Log.w(TAG, "High-tech render hook unavailable: $label", error)
        }
    }
}
