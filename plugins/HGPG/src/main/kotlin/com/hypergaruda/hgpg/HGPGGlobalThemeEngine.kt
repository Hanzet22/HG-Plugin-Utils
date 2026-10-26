package com.hypergaruda.hgpg

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Window
import android.view.View
import android.widget.TextView
import com.aliucord.Utils
import com.aliucord.api.PatcherAPI
import com.aliucord.api.SettingsAPI
import com.aliucord.patcher.Hook
import com.aliucord.patcher.PreHook
import com.discord.utilities.color.ColorCompat
import com.google.android.material.textfield.TextInputLayout
import com.lytefast.flexinput.R
import de.robv.android.xposed.XposedBridge

/**
 * HG PG's built-in app-wide resource color engine.
 *
 * This implementation is maintained inside HG PG; users do not need a separate
 * theming plugin or an external theme JSON. It follows the public Aliucord
 * plugin patching model and intentionally limits itself to color resources,
 * selected drawable tints, and theme attributes.
 */
internal object HGPGGlobalThemeEngine {
    private const val TAG = "HGPG-ThemeEngine"

    private val originalColorNames = HashMap<Int, String>()
    private val colorsByName = HashMap<String, Int>()
    private val colorsById = HashMap<Int, Int>()
    private val drawableTints = HashMap<Int, Int>()
    private val attrs = HashMap<Int, Int>()
    private var hooksInstalled = false

    private val accentNames = arrayOf(
        "link", "link_light", "brand_new", "brand_new_230", "brand_new_360",
        "brand_new_500", "brand_new_530", "brand_new_560", "brand_new_600",
        "uikit_btn_bg_color_selector_brand", "uikit_btn_bg_color_selector_secondary_dark",
        "uikit_btn_compound_color_selector_dark", "uikit_btn_compound_color_selector_light"
    )
    private val backgroundNames = arrayOf(
        "dark_grey_2", "primary_600", "primary_660", "primary_800",
        "primary_dark_600", "primary_dark_630", "primary_dark_800"
    )
    private val secondaryNames = arrayOf(
        "primary_500", "primary_630", "primary_700", "primary_dark_660",
        "primary_dark_700", "input_background", "statusbar", "active_channel"
    )
    private val accentAttrs = arrayOf(
        "color_brand", "brand_new_500", "colorControlBrandForeground", "colorControlActivated",
        "colorTextLink", "__alpha_10_theme_chat_mention_background", "theme_chat_mention_foreground"
    )
    private val backgroundAttrs = arrayOf(
        "colorSurface", "colorBackgroundFloating", "colorTabsBackground",
        "theme_chat_spoiler_inapp_bg", "primary_600", "primary_660", "primary_800"
    )
    private val secondaryAttrs = arrayOf(
        "colorBackgroundTertiary", "colorBackgroundSecondary", "primary_700", "theme_chat_spoiler_bg"
    )

    private val attributeTargets = mapOf(
        "brand_new" to arrayOf("color_brand"),
        "brand_new_360" to arrayOf("colorControlBrandForeground"),
        "brand_new_500" to arrayOf("colorControlBrandForeground", "color_brand_500"),
        "brand_new_260" to arrayOf("theme_chat_mention_foreground"),
        "brand_new_530" to arrayOf("theme_chat_mention_foreground"),
        "brand_360" to arrayOf("colorControlActivated"),
        "brand_500" to arrayOf("colorControlActivated"),
        "brand_500_alpha_20" to arrayOf("theme_chat_mention_background"),
        "brand_new_160" to arrayOf("theme_chat_mention_background"),
        "link" to arrayOf("colorTextLink"),
        "link_light" to arrayOf("colorTextLink"),
        "mention_highlight" to arrayOf("theme_chat_mentioned_me"),
        "primary_dark_600" to arrayOf("colorBackgroundPrimary"),
        "primary_dark_800" to arrayOf("colorSurface", "colorBackgroundFloating", "colorTabsBackground"),
        "primary_600" to arrayOf("primary_600", "theme_chat_spoiler_inapp_bg"),
        "primary_630" to arrayOf("theme_chat_code"),
        "primary_660" to arrayOf("primary_660", "theme_chat_codeblock_border"),
        "primary_700" to arrayOf("primary_700", "colorBackgroundTertiary", "colorBackgroundSecondary"),
        "primary_800" to arrayOf("primary_800"),
        "primary_300" to arrayOf("theme_chat_spoiler_bg"),
        "black_alpha_10" to arrayOf("theme_chat_spoiler_bg_visible")
    )

    fun start(context: Context, settings: SettingsAPI, patcher: PatcherAPI) {
        if (!hooksInstalled) {
            mapOriginalColors(context)
            installHooks(patcher)
            hooksInstalled = true
        }
        apply(HGPGThemes.get(readSetting(settings.getString("theme_profile", "garuda"))))
    }

    fun apply(theme: HGPGTheme) {
        colorsByName.clear()
        colorsById.clear()
        drawableTints.clear()
        attrs.clear()

        // Palette mappings for the common Discord/Aliucord resource names.
        putColors(accentNames, theme.accent)
        putAttrs(accentAttrs, theme.accent)
        putColors(backgroundNames, theme.background)
        putAttrs(backgroundAttrs, theme.background)
        putColors(secondaryNames, theme.surface)
        putAttrs(secondaryAttrs, theme.surface)

        putColor("statusbar", theme.background)
        putColor("input_background", theme.surfaceRaised)
        putColor("active_channel", theme.surfaceRaised)
        putColor("blocked_bg", theme.surfaceRaised)

        // Keep links, selected-channel icons, voice activity, and Nitro accent
        // drawables in the same visual system as the selected palette.
        putDrawableTint("ic_nitro_rep", theme.accent)
        putDrawableTint("drawable_voice_indicator_speaking", theme.accent)
        putDrawableTint("drawable_overlay_channels_selected_dark", theme.surfaceRaised)
        putDrawableTint("drawable_overlay_channels_selected_light", theme.surfaceRaised)
        putDrawableTint("drawable_overlay_channels_active_dark", theme.surfaceRaised)
        putDrawableTint("drawable_overlay_channels_active_light", theme.surfaceRaised)
    }

    fun stop() {
        colorsByName.clear()
        colorsById.clear()
        drawableTints.clear()
        attrs.clear()
        originalColorNames.clear()
        hooksInstalled = false
    }

    /** Recreate the host activity so already-inflated screens can reread colors. */
    fun refreshActivity(context: Context) {
        var current: Context? = context
        while (current is android.content.ContextWrapper) {
            if (current is android.app.Activity) {
                val activity = current
                activity.window?.decorView?.post {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        try {
                            activity.recreate()
                        } catch (error: Throwable) {
                            Log.w(TAG, "Could not refresh the current Aliucord screen", error)
                        }
                    }
                }
                return
            }
            current = current.baseContext
        }
        if (current is android.app.Activity) refreshActivity(current)
    }

    private fun readSetting(value: Any?): String {
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

    private fun mapOriginalColors(context: Context) {
        try {
            R.c::class.java.declaredFields.forEach { field ->
                try {
                    val id = field.getInt(null)
                    val color = context.getColor(id)
                    if (color != 0) originalColorNames[color] = field.name
                } catch (_: Throwable) {
                    // A color removed by a future Discord build should not stop the plugin.
                }
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Could not index all Discord color resources", error)
        }
    }

    private fun putColor(name: String, color: Int) {
        val id = try { Utils.getResId(name, "color") } catch (_: Throwable) { 0 }
        if (id != 0) {
            colorsById[id] = color
            colorsByName[name] = color
        } else if (name in arrayOf("statusbar", "input_background", "active_channel", "blocked_bg")) {
            colorsByName[name] = color
        }
    }

    private fun putColors(names: Array<String>, color: Int) {
        names.forEach { putColor(it, color) }
    }

    private fun putAttrs(names: Array<String>, color: Int) {
        names.forEach { name ->
            if (name.startsWith("__alpha_10_")) {
                setAttr(name.removePrefix("__alpha_10_"), (color and 0x00FFFFFF) or 0x1A000000)
            } else {
                setAttr(name, color)
            }
        }
    }

    private fun putAttr(name: String, color: Int) {
        attributeTargets[name]?.forEach { setAttr(it, color) }
    }

    private fun setAttr(name: String, color: Int) {
        val id = try { Utils.getResId(name, "attr") } catch (_: Throwable) { 0 }
        if (id != 0) attrs[id] = color
    }

    private fun putDrawableTint(name: String, color: Int) {
        val id = try { Utils.getResId(name, "drawable") } catch (_: Throwable) { 0 }
        if (id != 0) drawableTints[id] = color
    }

    private fun replacementForColor(color: Int): Int? =
        originalColorNames[color]?.let { colorsByName[it] }

    private fun installHooks(patcher: PatcherAPI) {
        deoptimizeColorMethods()

        with(patcher) {
            attempt("Resources.getColor") {
                patch(
                    Resources::class.java.getDeclaredMethod("getColor", Integer.TYPE, Resources.Theme::class.java),
                    PreHook { call ->
                        colorsById[call.args[0] as Int]?.let { call.result = it }
                    }
                )
            }

            attempt("Resources.loadDrawable") {
                val method = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Resources::class.java.getDeclaredMethod(
                        "loadDrawable", TypedValue::class.java, Integer.TYPE, Integer.TYPE, Resources.Theme::class.java
                    )
                } else {
                    Resources::class.java.getDeclaredMethod(
                        "loadDrawable", TypedValue::class.java, Integer.TYPE, Resources.Theme::class.java
                    )
                }
                patch(method, PreHook { call ->
                    val value = call.args[0] as TypedValue
                    if (value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
                        replacementForColor(value.data)?.let { value.data = it }
                    }
                })
            }

            attempt("ColorDrawable.setColor") {
                patch(
                    ColorDrawable::class.java.getDeclaredMethod("setColor", Integer.TYPE),
                    PreHook { call ->
                        val old = call.args[0] as Int
                        replacementForColor(old)?.let { call.args[0] = it }
                        if (old == 0xFF34373C.toInt() || old == 0xFFFCFCFC.toInt()) {
                            colorsByName["blocked_bg"]?.let { call.args[0] = it }
                        }
                    }
                )
            }

            attempt("ColorStateList.getColorForState") {
                patch(
                    ColorStateList::class.java.getDeclaredMethod("getColorForState", IntArray::class.java, Integer.TYPE),
                    Hook { call ->
                        replacementForColor(call.result as Int)?.let { call.result = it }
                    }
                )
            }

            attempt("Resources.getDrawableForDensity") {
                patch(
                    Resources::class.java.getDeclaredMethod(
                        "getDrawableForDensity", Integer.TYPE, Integer.TYPE, Resources.Theme::class.java
                    ),
                    Hook { call ->
                        drawableTints[call.args[0] as Int]?.let { color ->
                            (call.result as? Drawable)?.setTint(color)
                        }
                    }
                )
            }

            attempt("ColorCompat.getThemedColor") {
                patch(
                    ColorCompat::class.java.getDeclaredMethod("getThemedColor", Context::class.java, Integer.TYPE),
                    PreHook { call -> attrs[call.args[1] as Int]?.let { call.result = it } }
                )
            }

            attempt("ColorCompat.setStatusBarColor") {
                patch(
                    ColorCompat::class.java.getDeclaredMethod(
                        "setStatusBarColor", Window::class.java, Integer.TYPE, java.lang.Boolean.TYPE
                    ),
                    PreHook { call -> colorsByName["statusbar"]?.let { call.args[1] = it } }
                )
            }

            attempt("TextInputLayout.calculateBoxBackgroundColor") {
                patch(
                    TextInputLayout::class.java.getDeclaredMethod("calculateBoxBackgroundColor"),
                    PreHook { call -> colorsByName["input_background"]?.let { call.result = it } }
                )
            }
        }
    }

    private fun deoptimizeColorMethods() {
        val targets = listOf(
            RippleDrawable::class.java to "updateRipplePaint",
            TextView::class.java to "updateTextColors",
            Drawable::class.java to "updateBlendModeFilter",
            GradientDrawable::class.java to "updateLocalState",
            View::class.java to "setBackgroundColor"
        )
        targets.forEach { (clazz, name) ->
            clazz.declaredMethods.filter { it.name == name }.forEach { method ->
                try { XposedBridge.deoptimizeMethod(method) } catch (_: Throwable) {}
            }
        }
    }

    private fun attempt(label: String, action: () -> Unit) {
        try {
            action()
        } catch (error: Throwable) {
            Log.w(TAG, "Color hook unavailable: $label", error)
        }
    }
}
