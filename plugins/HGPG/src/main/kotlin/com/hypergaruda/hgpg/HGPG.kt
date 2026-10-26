package com.hypergaruda.hgpg

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.discord.app.AppActivity
import de.robv.android.xposed.XC_MethodHook

@AliucordPlugin(requiresRestart = true)
@Suppress("unused")
class HGPG : Plugin() {
    override fun start(context: Context) {
        settingsTab = com.aliucord.entities.Plugin.SettingsTab(HGPGSettingsPage::class.java)
            .withArgs(settings)

        // Add the floating HG PG launcher when the main Aliucord view is created.
        patcher.patch(
            AppActivity::class.java.getDeclaredMethod("onCreate", Bundle::class.java),
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val hostActivity = param.thisObject as? Activity ?: return
                    hostActivity.window.decorView.post {
                        if (!hostActivity.isFinishing && !hostActivity.isDestroyed) {
                            HGPGOverlay.attach(hostActivity, settings)
                        }
                    }
                }
            },
        )
    }

    override fun stop(context: Context) {
        HGPGOverlay.dismiss()
        patcher.unpatchAll()
    }
}
