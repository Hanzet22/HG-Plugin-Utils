package com.hypergaruda.hgpg

import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.TextView

/** Small entrance helpers for HG PG-owned UI; never rewrites Discord text or input fields. */
internal object HGPGUIAnimations {
    fun fadeInTextTree(root: View, staggerMs: Long = 16L) {
        val targets = ArrayList<TextView>()

        fun collect(view: View) {
            if (view is TextView &&
                view !is EditText &&
                !view.isClickable &&
                !view.isFocusable &&
                !view.text.isNullOrBlank()
            ) {
                targets.add(view)
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) collect(view.getChildAt(index))
            }
        }

        collect(root)
        targets.forEachIndexed { index, textView ->
            textView.animate().cancel()
            textView.alpha = 0f
            textView.translationY = dp(textView, 3).toFloat()
            textView.postDelayed({
                if (!textView.isAttachedToWindow) return@postDelayed
                textView.animate().alpha(1f).translationY(0f)
                    .setDuration(210L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }, index * staggerMs.coerceAtLeast(0L))
        }
    }

    private fun dp(view: View, value: Int): Int =
        (value * view.resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
