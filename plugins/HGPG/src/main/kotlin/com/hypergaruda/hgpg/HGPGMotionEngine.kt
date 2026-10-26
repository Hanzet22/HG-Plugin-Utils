package com.hypergaruda.hgpg

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.TextView
import android.webkit.WebView
import android.view.SurfaceView
import android.view.TextureView
import com.aliucord.api.PatcherAPI
import com.aliucord.api.SettingsAPI
import com.aliucord.patcher.Hook
import com.aliucord.patcher.PreHook
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Global native UI motion system for HG PG.
 *
 * It animates app screen roots, meaningful views as they attach, interactive
 * touch/click feedback, and dialog surfaces. It never replaces a view's
 * OnClickListener or OnTouchListener, preserving the host UI's own behavior.
 */
internal object HGPGMotionEngine {
    private const val TAG = "HGPG-Motion"
    private val enteredViews = WeakHashMap<View, Boolean>()
    private val enteredRoots = WeakHashMap<View, Boolean>()
    private val rootBackgrounds = WeakHashMap<View, Drawable?>()
    private val lastTouchRelease = WeakHashMap<View, Long>()
    private val sequence = AtomicInteger(0)
    @Volatile private var currentTheme = HGPGThemes.get("garuda")
    @Volatile private var installed = false

    fun start(context: Context, settings: SettingsAPI, patcher: PatcherAPI) {
        currentTheme = HGPGThemes.get(readString(settings.getString("theme_profile", "garuda")))
        if (!installed) {
            installHooks(patcher)
            installed = true
        }
    }

    fun setTheme(theme: HGPGTheme) {
        currentTheme = theme
        synchronized(rootBackgrounds) {
            rootBackgrounds.keys.toList().forEach { root ->
                if (root.isAttachedToWindow) {
                    root.background = HGPGAnimatedDrawable(theme, cornerScale = 0f, intensity = 0.82f)
                    root.invalidate()
                }
            }
        }
    }

    /** Called on activity resume so existing, already-inflated screens are covered. */
    fun attach(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        content.post {
            if (!activity.isFinishing && !activity.isDestroyed) {
                applyAnimatedBackdrop(content)
                animateScreen(content, force = true)
                visitExisting(content)
            }
        }
    }

    fun stop() {
        synchronized(rootBackgrounds) {
            rootBackgrounds.forEach { (view, original) ->
                if (view.background is HGPGAnimatedDrawable) view.background = original
            }
            rootBackgrounds.clear()
        }
        synchronized(enteredViews) { enteredViews.clear() }
        synchronized(enteredRoots) { enteredRoots.clear() }
        synchronized(lastTouchRelease) { lastTouchRelease.clear() }
        installed = false
    }

    /** Applies a moving, theme-specific Drawable beneath the actual Aliucord screen. */
    private fun applyAnimatedBackdrop(content: ViewGroup) {
        val target = if (content.childCount > 0) content.getChildAt(0) else content
        synchronized(rootBackgrounds) {
            if (!rootBackgrounds.containsKey(target)) rootBackgrounds[target] = target.background
            target.background = HGPGAnimatedDrawable(currentTheme, cornerScale = 0f, intensity = 0.82f)
        }
        target.invalidate()
    }

    private fun installHooks(patcher: PatcherAPI) {
        attempt("View.onAttachedToWindow") {
            patcher.patch(View::class.java.getDeclaredMethod("onAttachedToWindow"), Hook { call ->
                val view = call.thisObject as? View ?: return@Hook
                view.post { animateEntranceIfNeeded(view) }
            })
        }

        // Let views animate again after fragment/tab navigation detaches and reattaches them.
        attempt("View.onDetachedFromWindow") {
            patcher.patch(View::class.java.getDeclaredMethod("onDetachedFromWindow"), Hook { call ->
                val view = call.thisObject as? View ?: return@Hook
                synchronized(enteredViews) { enteredViews.remove(view) }
                synchronized(enteredRoots) { enteredRoots.remove(view) }
                synchronized(lastTouchRelease) { lastTouchRelease.remove(view) }
            })
        }

        attempt("View.onTouchEvent") {
            patcher.patch(View::class.java.getDeclaredMethod("onTouchEvent", MotionEvent::class.java), PreHook { call ->
                val view = call.thisObject as? View ?: return@PreHook
                val event = call.args.firstOrNull() as? MotionEvent ?: return@PreHook
                if (!isInteractive(view) || skipMotion(view)) return@PreHook
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> press(view)
                    MotionEvent.ACTION_UP -> {
                        synchronized(lastTouchRelease) { lastTouchRelease[view] = SystemClock.uptimeMillis() }
                        release(view, cancelled = false)
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        synchronized(lastTouchRelease) { lastTouchRelease[view] = SystemClock.uptimeMillis() }
                        release(view, cancelled = true)
                    }
                }
            })
        }

        attempt("View.performClick") {
            patcher.patch(View::class.java.getDeclaredMethod("performClick"), Hook { call ->
                val view = call.thisObject as? View ?: return@Hook
                if (!isInteractive(view) || skipMotion(view)) return@Hook
                val touchedRecently = synchronized(lastTouchRelease) {
                    val last = lastTouchRelease[view] ?: 0L
                    SystemClock.uptimeMillis() - last < 360L
                }
                if (!touchedRecently) clickPulse(view)
            })
        }

        // Dialogs have their own windows, not children of an Activity's content root.
        attempt("Dialog.show") {
            patcher.patch(Dialog::class.java.getDeclaredMethod("show"), Hook { call ->
                val dialog = call.thisObject as? Dialog ?: return@Hook
                val decor = dialog.window?.decorView ?: return@Hook
                val root = decor.findViewById<View>(android.R.id.content) ?: decor
                root.post { animateScreen(root, force = true); visitExisting(root) }
            })
        }
    }

    private fun visitExisting(view: View) {
        animateEntranceIfNeeded(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                visitExisting(view.getChildAt(i))
            }
        }
    }

    private fun animateScreen(root: View, force: Boolean = false) {
        synchronized(enteredRoots) {
            if (enteredRoots.containsKey(root) && !force) return
            enteredRoots[root] = true
        }
        val theme = currentTheme
        val d = root.resources.displayMetrics.density
        root.animate().cancel()
        when (theme.motion) {
            HGPGMotion.DEEP_SEA -> {
                root.alpha = 0f; root.translationY = 30f * d; root.scaleX = 0.965f; root.scaleY = 0.965f
                root.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(480)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
            HGPGMotion.GARUDA -> {
                root.alpha = 0f; root.translationX = 38f * d; root.scaleX = 0.975f; root.scaleY = 0.975f
                root.animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f).setDuration(430)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
            HGPGMotion.LED_ORANGE -> {
                root.alpha = 0f; root.scaleX = 0.92f; root.scaleY = 0.92f
                root.animate().alpha(1f).scaleX(1.035f).scaleY(1.035f).setDuration(280)
                    .setInterpolator(OvershootInterpolator(0.8f)).withEndAction {
                        root.animate().scaleX(1f).scaleY(1f).setDuration(150)
                            .setInterpolator(DecelerateInterpolator()).start()
                    }.start()
            }
            HGPGMotion.GT_SPORT -> {
                root.alpha = 0f; root.translationX = 42f * d; root.scaleX = 0.975f; root.scaleY = 0.975f
                root.animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f).setDuration(350)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
            HGPGMotion.GT_OLD -> {
                root.alpha = 0f; root.translationY = 20f * d; root.scaleX = 0.98f; root.scaleY = 0.98f
                root.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(520)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
            HGPGMotion.CRT_PS2 -> {
                root.alpha = 0f; root.scaleX = 0.955f; root.scaleY = 0.955f
                root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(190)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        }
    }

    private fun animateEntranceIfNeeded(view: View) {
        if (!view.isAttachedToWindow || view.visibility != View.VISIBLE || skipMotion(view)) return
        if (!shouldAnimateEntrance(view)) return
        synchronized(enteredViews) {
            if (enteredViews.containsKey(view)) return
            enteredViews[view] = true
        }
        val delay = (sequence.incrementAndGet() % 6) * 16L
        view.postDelayed({
            if (!view.isAttachedToWindow || view.visibility != View.VISIBLE) return@postDelayed
            val theme = currentTheme
            val d = view.resources.displayMetrics.density
            view.animate().cancel()
            when (theme.motion) {
                HGPGMotion.DEEP_SEA -> {
                    view.alpha = 0f; view.translationY = 7f * d; view.scaleX = 0.99f; view.scaleY = 0.99f
                    view.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay(0L)
                        .setDuration(210).setInterpolator(DecelerateInterpolator()).start()
                }
                HGPGMotion.GARUDA -> {
                    view.alpha = 0f; view.translationX = 9f * d
                    view.animate().alpha(1f).translationX(0f).setDuration(205)
                        .setInterpolator(DecelerateInterpolator()).start()
                }
                HGPGMotion.LED_ORANGE -> {
                    view.alpha = 0f; view.scaleX = 0.94f; view.scaleY = 0.94f
                    view.animate().alpha(1f).scaleX(1.025f).scaleY(1.025f).setDuration(150)
                        .withEndAction {
                            view.animate().scaleX(1f).scaleY(1f).setDuration(135)
                                .setInterpolator(DecelerateInterpolator()).start()
                        }.start()
                }
                HGPGMotion.GT_SPORT -> {
                    view.alpha = 0f; view.translationX = -12f * d
                    view.animate().alpha(1f).translationX(0f).setDuration(165)
                        .setInterpolator(DecelerateInterpolator()).start()
                }
                HGPGMotion.GT_OLD -> {
                    view.alpha = 0f; view.translationY = 10f * d
                    view.animate().alpha(1f).translationY(0f).setDuration(275)
                        .setInterpolator(DecelerateInterpolator()).start()
                }
                HGPGMotion.CRT_PS2 -> {
                    view.alpha = 0f; view.scaleX = 0.975f; view.scaleY = 0.975f
                    view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(100)
                        .setInterpolator(DecelerateInterpolator()).start()
                }
            }
        }, delay)
    }

    private fun shouldAnimateEntrance(view: View): Boolean {
        if (view.id == android.R.id.content) return false
        if (isInteractive(view) || view.isFocusable) return true
        if (view is TextView || view is EditText) return false
        if (view is ViewGroup && view.childCount in 1..18) {
            val bg = view.background
            return bg != null && bg !is ColorDrawable
        }
        return false
    }

    private fun isInteractive(view: View): Boolean =
        view.isEnabled && (view.isClickable || view.isLongClickable || view.hasOnClickListeners())

    private fun skipMotion(view: View): Boolean {
        if (view is EditText || view is WebView || view is SurfaceView || view is TextureView) return true
        val name = view.javaClass.name
        if (name.contains("SurfaceView", true) || name.contains("TextureView", true) ||
            name.contains("VideoView", true) || name.contains("WebView", true)) return true
        return false
    }

    private fun press(view: View) {
        view.animate().cancel()
        when (currentTheme.motion) {
            HGPGMotion.DEEP_SEA -> view.animate().scaleX(0.93f).scaleY(0.93f).alpha(0.82f)
                .setDuration(95).setInterpolator(DecelerateInterpolator()).start()
            HGPGMotion.GARUDA -> view.animate().scaleX(0.92f).scaleY(0.92f).alpha(0.88f)
                .setDuration(80).setInterpolator(DecelerateInterpolator()).start()
            HGPGMotion.LED_ORANGE -> view.animate().scaleX(0.93f).scaleY(0.93f).alpha(0.78f)
                .setDuration(75).setInterpolator(DecelerateInterpolator()).start()
            HGPGMotion.GT_SPORT -> view.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.84f)
                .setDuration(70).setInterpolator(DecelerateInterpolator()).start()
            HGPGMotion.GT_OLD -> view.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.82f)
                .setDuration(90).setInterpolator(DecelerateInterpolator()).start()
            HGPGMotion.CRT_PS2 -> view.animate().scaleX(0.93f).scaleY(0.93f).alpha(0.70f)
                .setDuration(45).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun release(view: View, cancelled: Boolean) {
        view.animate().cancel()
        val duration = if (cancelled) 100L else 175L
        when (currentTheme.motion) {
            HGPGMotion.DEEP_SEA, HGPGMotion.GARUDA -> {
                view.animate().alpha(1f).scaleX(1.045f).scaleY(1.045f).setDuration(duration)
                    .setInterpolator(OvershootInterpolator(1.25f)).withEndAction {
                        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(115)
                            .setInterpolator(DecelerateInterpolator()).start()
                    }.start()
            }
            HGPGMotion.LED_ORANGE -> {
                view.animate().alpha(1f).scaleX(1.055f).scaleY(1.055f).setDuration(105)
                    .setInterpolator(OvershootInterpolator(1.15f)).withEndAction {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(160)
                            .setInterpolator(DecelerateInterpolator()).start()
                    }.start()
            }
            HGPGMotion.GT_SPORT -> view.animate().alpha(1f).scaleX(1.035f).scaleY(1.035f)
                .setDuration(125).setInterpolator(OvershootInterpolator(0.9f)).withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }.start()
            HGPGMotion.GT_OLD -> view.animate().alpha(1f).scaleX(1.03f).scaleY(1.03f)
                .setDuration(180).setInterpolator(DecelerateInterpolator()).withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }.start()
            HGPGMotion.CRT_PS2 -> view.animate().alpha(1f).scaleX(1.025f).scaleY(1.025f)
                .setDuration(85).withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(70).start()
                }.start()
        }
    }

    /** Clearly visible pulse for programmatic/accessibility clicks without a touch sequence. */
    private fun clickPulse(view: View) {
        view.animate().cancel()
        when (currentTheme.motion) {
            HGPGMotion.DEEP_SEA -> view.animate().scaleX(1.05f).scaleY(1.05f).alpha(0.88f).setDuration(90)
                .setInterpolator(OvershootInterpolator(1.1f)).withEndAction {
                    view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
                }.start()
            HGPGMotion.GARUDA -> view.animate().scaleX(1.06f).scaleY(1.06f).setDuration(90)
                .setInterpolator(OvershootInterpolator(1.25f)).withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(155).start()
                }.start()
            HGPGMotion.LED_ORANGE -> view.animate().alpha(0.68f).scaleX(0.95f).scaleY(0.95f).setDuration(55)
                .withEndAction {
                    view.animate().alpha(1f).scaleX(1.05f).scaleY(1.05f).setDuration(110)
                        .setInterpolator(OvershootInterpolator(1.1f)).withEndAction {
                            view.animate().scaleX(1f).scaleY(1f).setDuration(115).start()
                        }.start()
                }.start()
            HGPGMotion.GT_SPORT -> view.animate().translationX(5f * view.resources.displayMetrics.density)
                .scaleX(1.04f).scaleY(1.04f).setDuration(65).withEndAction {
                    view.animate().translationX(0f).scaleX(1f).scaleY(1f).setDuration(135).start()
                }.start()
            HGPGMotion.GT_OLD -> view.animate().alpha(0.78f).scaleX(0.96f).scaleY(0.96f).setDuration(70)
                .withEndAction {
                    view.animate().alpha(1f).scaleX(1.035f).scaleY(1.035f).setDuration(130)
                        .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(130).start() }.start()
                }.start()
            HGPGMotion.CRT_PS2 -> view.animate().alpha(0.60f).scaleX(0.94f).scaleY(0.94f).setDuration(35)
                .withEndAction {
                    view.animate().alpha(1f).scaleX(1.025f).scaleY(1.025f).setDuration(85)
                        .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(75).start() }.start()
                }.start()
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
            while (i < length) {
                out.append(charAt.invoke(value, i) as Char)
                i++
            }
            out.toString()
        } catch (_: Throwable) {
            try {
                Any::class.java.getMethod("toString").invoke(value)?.toString() ?: "garuda"
            } catch (_: Throwable) {
                "garuda"
            }
        }
    }

    private fun attempt(label: String, action: () -> Unit) {
        try {
            action()
        } catch (error: Throwable) {
            android.util.Log.w(TAG, "Animation hook unavailable: $label", error)
        }
    }
}
