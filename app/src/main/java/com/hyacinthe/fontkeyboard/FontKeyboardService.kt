package com.hyacinthe.fontkeyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class FontKeyboardService : InputMethodService() {

    private class Theme(val bg: Int, val key: Int, val special: Int, val text: Int, val accent: Int)

    private val themes = listOf(
        Theme(Color.parseColor("#D3D6DB"), Color.WHITE, Color.parseColor("#ADB2BB"), Color.parseColor("#111111"), Color.parseColor("#1E88C7")),
        Theme(Color.parseColor("#121212"), Color.parseColor("#2B2B2B"), Color.parseColor("#1C1C1C"), Color.parseColor("#F2F2F2"), Color.parseColor("#4FC3F7")),
        Theme(Color.parseColor("#FCE4EC"), Color.parseColor("#F8BBD0"), Color.parseColor("#F48FB1"), Color.parseColor("#4A1A2C"), Color.parseColor("#D81B60"))
    )

    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    private var cur: Theme = themes[0]
    private var themeIdx = 0
    private var fontIdx = 0
    private var azerty = true
    private var mode = 0            // 0 = lettres, 1 = chiffres/accents, 2 = maths
    private var shiftState = 0      // 0 = off, 1 = une lettre, 2 = verrouillé
    private var lastShift = 0L

    private lateinit var root: LinearLayout
    private val chips = ArrayList<TextView>()
    private val letterKeys = ArrayList<Pair<TextView, Char>>()
    private var shiftKey: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        val p = getSharedPreferences("fk", MODE_PRIVATE)
        fontIdx = p.getInt("font", 0).coerceIn(0, FONTS.size - 1)
        themeIdx = p.getInt("theme", 0).coerceIn(0, themes.size - 1)
        azerty = p.getBoolean("azerty", true)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(4), dp(3), dp(6))
        }
        render()
        return root
    }

    // ---------- Affichage ----------

    private fun render() {
        cur = themes[themeIdx]
        root.removeAllViews()
        root.setBackgroundColor(cur.bg)
        root.addView(fontBar())
        when (mode) {
            0 -> letters()
            1 -> symbols()
            else -> maths()
        }
        if (mode != 0) bottomAlt()
        getSharedPreferences("fk", MODE_PRIVATE).edit()
            .putInt("font", fontIdx).putInt("theme", themeIdx).putBoolean("azerty", azerty).apply()
    }

    private fun fontBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(small("🎨") { themeIdx = (themeIdx + 1) % themes.size; render() })
        bar.addView(small(if (azerty) "AZ" else "QW") { azerty = !azerty; render() })

        val sv = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chips.clear()
        FONTS.forEachIndexed { i, f ->
            val c = TextView(this).apply {
                text = f.map('A') + f.map('b')
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(8), dp(14), dp(8))
                layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) }
                setOnClickListener { fontIdx = i; refreshChips() }
            }
            chips.add(c)
            chipRow.addView(c)
        }
        refreshChips()
        sv.addView(chipRow)
        bar.addView(sv)
        return bar
    }

    private fun refreshChips() {
        chips.forEachIndexed { i, c ->
            val sel = i == fontIdx
            c.background = round(if (sel) cur.accent else cur.key)
            c.setTextColor(if (sel) Color.WHITE else cur.text)
        }
        getSharedPreferences("fk", MODE_PRIVATE).edit().putInt("font", fontIdx).apply()
    }

    private fun letters() {
        letterKeys.clear()
        shiftKey = null
        val rows = if (azerty) listOf("azertyuiop", "qsdfghjklm", "wxcvbn")
        else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

        rows.forEachIndexed { i, r ->
            val row = hrow()
            if (i == 2) {
                val s = key("⇧", 1.5f, cur.special) { toggleShift() }
                shiftKey = s
                row.addView(s)
            }
            r.forEach { ch ->
                val k = key(ch.toString()) { typeLetter(ch) }
                letterKeys.add(k to ch)
                row.addView(k)
            }
            if (i == 2) {
                val d = key("⌫", 1.5f, cur.special) {}
                repeatable(d) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
                row.addView(d)
            }
            root.addView(row)
        }

        val b = hrow()
        b.addView(key("123", 1.4f, cur.special, 16f) { mode = 1; render() })
        b.addView(key("√π", 1.2f, cur.special, 16f) { mode = 2; render() })
        b.addView(key("espace", 4f, size = 14f) { type(" ") })
        b.addView(key(".", 1f) { type(".") })
        b.addView(key("⏎", 1.4f, cur.special) { enter() })
        root.addView(b)
        updateShift()
    }

    private fun symbols() {
        listOf("1234567890", "@#\$_&-+()/", "*\"':;!?%=<", "éèêàùçôîñë").forEach { r ->
            val row = hrow()
            r.forEach { ch -> row.addView(key(ch.toString()) { type(FONTS[fontIdx].map(ch)) }) }
            root.addView(row)
        }
    }

    private fun maths() {
        val sv = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(208))
            isVerticalScrollBarEnabled = false
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        MATH_SYMBOLS.split(" ").filter { it.isNotEmpty() }.chunked(8).forEach { chunk ->
            val row = hrow()
            chunk.forEach { s -> row.addView(key(s, size = 18f) { type(s) }) }
            repeat(8 - chunk.size) {
                row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
                })
            }
            col.addView(row)
        }
        sv.addView(col)
        root.addView(sv)
    }

    private fun bottomAlt() {
        val b = hrow()
        b.addView(key("ABC", 1.4f, cur.special, 16f) { mode = 0; render() })
        b.addView(key(if (mode == 1) "√π" else "123", 1.2f, cur.special, 16f) { mode = if (mode == 1) 2 else 1; render() })
        b.addView(key("espace", 3f, size = 14f) { type(" ") })
        val d = key("⌫", 1.2f, cur.special) {}
        repeatable(d) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
        b.addView(d)
        b.addView(key("⏎", 1.4f, cur.special) { enter() })
        root.addView(b)
    }

    // ---------- Saisie ----------

    private fun type(s: String) {
        currentInputConnection?.commitText(s, 1)
    }

    private fun typeLetter(ch: Char) {
        val c = if (shiftState > 0) ch.uppercaseChar() else ch
        type(FONTS[fontIdx].map(c))
        if (shiftState == 1) { shiftState = 0; updateShift() }
    }

    private fun toggleShift() {
        val now = System.currentTimeMillis()
        shiftState = when {
            shiftState == 2 -> 0
            shiftState == 1 && now - lastShift < 350 -> 2
            shiftState == 1 -> 0
            else -> 1
        }
        lastShift = now
        updateShift()
    }

    private fun updateShift() {
        letterKeys.forEach { (tv, c) ->
            tv.text = (if (shiftState > 0) c.uppercaseChar() else c).toString()
        }
        shiftKey?.apply {
            text = if (shiftState == 2) "AA" else "⇧"
            background = round(if (shiftState > 0) cur.accent else cur.special)
        }
    }

    private fun enter() {
        val opts = currentInputEditorInfo?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    // ---------- Outils de vue ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun round(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(7).toFloat()
    }

    private fun hrow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }

    private fun key(
        label: String,
        weight: Float = 1f,
        bg: Int = cur.key,
        size: Float = 20f,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        text = label
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(cur.text)
        background = round(bg)
        layoutParams = LinearLayout.LayoutParams(0, dp(46), weight).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
    }

    private fun small(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(cur.text)
        background = round(cur.special)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        setOnClickListener { onClick() }
    }

    /** Touche qui se répète quand on la maintient (effacer). */
    private fun repeatable(v: View, action: () -> Unit) {
        val r = object : Runnable {
            override fun run() {
                action()
                handler.postDelayed(this, 60)
            }
        }
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    action()
                    handler.postDelayed(r, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(r)
            }
            true
        }
    }
}
