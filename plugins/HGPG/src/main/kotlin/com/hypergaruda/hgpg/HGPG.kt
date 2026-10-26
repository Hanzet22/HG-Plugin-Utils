package com.hypergaruda.hgpg

import android.app.Activity
import android.app.Application
import android.content.Context
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin

@AliucordPlugin(requiresRestart = true)
@Suppress("unused")
class HGPG : Plugin() {
    private var activityCallbacks: Application.ActivityLifecycleCallbacks? = null

    override fun start(context: Context) {
        settingsTab = com.aliucord.entities.Plugin.SettingsTab(HGPGSettingsPage::class.java)
            .withArgs(settings)

        try {
            HGPGGlobalThemeEngine.start(context, settings, patcher)
        } catch (error: Throwable) {
            android.util.Log.e("HGPG", "Built-in global theme engine could not start", error)
        }

        // Reattach whenever an Aliucord activity becomes active. Tab navigation can
        // recreate or switch activities after the original onCreate hook has fired.
        val app = context.applicationContext as? Application ?: return
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                activity.window.decorView.post {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        HGPGOverlay.attach(activity, settings)
                    }
                }
            }
        }
        activityCallbacks = callbacks
        app.registerActivityLifecycleCallbacks(callbacks)
    }

    override fun stop(context: Context) {
        val app = context.applicationContext as? Application
        activityCallbacks?.let { app?.unregisterActivityLifecycleCallbacks(it) }
        activityCallbacks = null
        HGPGOverlay.dismiss()
        patcher.unpatchAll()
        HGPGGlobalThemeEngine.stop()
    }
}
