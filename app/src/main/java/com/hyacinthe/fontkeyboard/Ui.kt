package com.hyacinthe.fontkeyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

class KbTheme(val bg: Int, val key: Int, val special: Int, val text: Int, val accent: Int)

val THEMES = listOf(
    KbTheme(Color.parseColor("#D3D6DB"), Color.WHITE, Color.parseColor("#ADB2BB"), Color.parseColor("#111111"), Color.parseColor("#1E88C7")),
    KbTheme(Color.parseColor("#121212"), Color.parseColor("#2B2B2B"), Color.parseColor("#1C1C1C"), Color.parseColor("#F2F2F2"), Color.parseColor("#4FC3F7")),
    KbTheme(Color.parseColor("#FCE4EC"), Color.parseColor("#F8BBD0"), Color.parseColor("#F48FB1"), Color.parseColor("#4A1A2C"), Color.parseColor("#D81B60"))
)

class Ui(private val ctx: Context) {
    var cur: KbTheme = THEMES[0]
    val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val handler = Handler(Looper.getMainLooper())

    fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun round(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(7).toFloat()
        return g
    }

    fun hrow(): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.HORIZONTAL
        l.gravity = Gravity.CENTER_HORIZONTAL
        l.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        return l
    }

    /** fast = true : la touche tape dès que le doigt touche l'écran. */
    fun key(
        label: String,
        weight: Float = 1f,
        bg: Int = cur.key,
        size: Float = 20f,
        fast: Boolean = false,
        onClick: () -> Unit
    ): TextView {
        val tv = TextView(ctx)
        tv.text = label
        tv.textSize = size
        tv.gravity = Gravity.CENTER
        tv.setTextColor(cur.text)
        tv.background = round(bg)
        val lp = LinearLayout.LayoutParams(0, dp(46), weight)
        lp.setMargins(dp(2), dp(3), dp(2), dp(3))
        tv.layoutParams = lp
        if (fast) {
            tv.setOnTouchListener { v, e ->
                if (e.action == MotionEvent.ACTION_DOWN) {
                    v.alpha = 0.6f
                    onClick()
                } else if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
                    v.alpha = 1f
                }
                true
            }
        } else {
            tv.setOnClickListener { onClick() }
        }
        return tv
    }

    fun small(label: String, onClick: () -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = label
        tv.textSize = 15f
        tv.gravity = Gravity.CENTER
        tv.setTextColor(cur.text)
        tv.background = round(cur.special)
        tv.setPadding(dp(9), dp(8), dp(9), dp(8))
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        tv.layoutParams = lp
        tv.setOnClickListener { onClick() }
        return tv
    }

    fun chip(label: String, size: Float, padH: Int, padV: Int): TextView {
        val tv = TextView(ctx)
        tv.text = label
        tv.textSize = size
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(padH), dp(padV), dp(padH), dp(padV))
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.setMargins(dp(3), dp(2), dp(3), dp(2))
        tv.layoutParams = lp
        return tv
    }

    fun select(tv: TextView, sel: Boolean) {
        tv.background = round(if (sel) cur.accent else cur.key)
        tv.setTextColor(if (sel) Color.WHITE else cur.text)
    }

    /** Touche qui se répète quand on la maintient (effacer). */
    fun repeatable(v: View, action: () -> Unit) {
        val r = object : Runnable {
            override fun run() {
                action()
                handler.postDelayed(this, 60)
            }
        }
        v.setOnTouchListener { view, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                view.alpha = 0.6f
                action()
                handler.postDelayed(r, 400)
            } else if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
                view.alpha = 1f
                handler.removeCallbacks(r)
            }
            true
        }
    }
}
