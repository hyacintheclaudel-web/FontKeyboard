package com.hyacinthe.fontkeyboard

import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class FontKeyboardService : InputMethodService() {

    private lateinit var ui: Ui
    private lateinit var sug: Suggester
    private lateinit var math: MathPanel
    private lateinit var root: LinearLayout

    private var themeIdx = 0
    private var fontIdx = 0
    private var mode = 0            // 0 = lettres, 1 = chiffres/accents, 2 = maths
    private var shiftState = 0      // 0 = off, 1 = une lettre, 2 = verrouillé
    private var lastShift = 0L

    private val chips = ArrayList<TextView>()
    private val letterKeys = ArrayList<Pair<TextView, Char>>()
    private val sugViews = ArrayList<TextView>()
    private var shiftKey: TextView? = null

    override fun onCreate() {
        super.onCreate()
        val p = getSharedPreferences("fk", MODE_PRIVATE)
        ui = Ui(this)
        sug = Suggester(p)
        math = MathPanel(this, ui) { s -> type(s) }
        fontIdx = p.getInt("font", 0).coerceIn(0, FONTS.size - 1)
        themeIdx = p.getInt("theme", 0).coerceIn(0, THEMES.size - 1)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(ui.dp(3), ui.dp(4), ui.dp(3), ui.dp(6))
        render()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetWord()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        sug.save()
        resetWord()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (!sug.isEmpty() && (newSelStart != newSelEnd || newSelStart < sug.mapped.length)) resetWord()
    }

    private fun savePrefs() {
        getSharedPreferences("fk", MODE_PRIVATE).edit()
            .putInt("font", fontIdx).putInt("theme", themeIdx).apply()
    }

    // ---------- Affichage ----------

    private fun render() {
        ui.cur = THEMES[themeIdx]
        root.removeAllViews()
        sugViews.clear()
        root.setBackgroundColor(ui.cur.bg)
        root.addView(fontBar())
        if (mode == 0) {
            root.addView(suggestionBar())
            letters()
        } else {
            if (mode == 1) symbols() else math.addTo(root)
            bottomAlt()
        }
    }

    private fun fontBar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.addView(ui.small("🎨") {
            themeIdx = (themeIdx + 1) % THEMES.size
            savePrefs()
            render()
        })
        bar.addView(ui.small("📋") { pastePlain() })
        bar.addView(ui.small("🔠") { pasteOrConvert() })
        bar.addView(ui.small("📄") { copyText() })

        val sv = HorizontalScrollView(this)
        sv.isHorizontalScrollBarEnabled = false
        sv.layoutParams = LinearLayout.LayoutParams(0, ui.WRAP, 1f)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        chips.clear()
        for (i in FONTS.indices) {
            val f = FONTS[i]
            val c = ui.chip(f.map('A') + f.map('b'), 18f, 14, 8)
            c.setOnClickListener {
                fontIdx = i
                refreshChips()
                savePrefs()
            }
            chips.add(c)
            row.addView(c)
        }
        refreshChips()
        sv.addView(row)
        bar.addView(sv)
        return bar
    }

    private fun refreshChips() {
        for (i in chips.indices) ui.select(chips[i], i == fontIdx)
    }

    private fun suggestionBar(): View {
        val bar = ui.hrow()
        for (i in 0 until 3) {
            val tv = TextView(this)
            tv.textSize = 16f
            tv.gravity = Gravity.CENTER
            tv.maxLines = 1
            tv.setTextColor(ui.cur.text)
            tv.layoutParams = LinearLayout.LayoutParams(0, ui.dp(38), 1f)
            tv.setOnClickListener {
                val s = tv.tag as? String
                if (s != null) applySuggestion(s)
            }
            sugViews.add(tv)
            bar.addView(tv)
        }
        return bar
    }

    private fun letters() {
        letterKeys.clear()
        shiftKey = null
        val rows = listOf("azertyuiop", "qsdfghjklm", "wxcvbn")
        for (r in 0 until rows.size) {
            val row = ui.hrow()
            if (r == 2) {
                val s = ui.key("⇧", 1.5f, ui.cur.special, fast = true) { toggleShift() }
                shiftKey = s
                row.addView(s)
            }
            for (ch in rows[r]) {
                val k = ui.key(ch.toString(), fast = true) { typeLetter(ch) }
                letterKeys.add(Pair(k, ch))
                row.addView(k)
            }
            if (r == 2) {
                val d = ui.key("⌫", 1.5f, ui.cur.special) { }
                ui.repeatable(d) { doDelete() }
                row.addView(d)
            }
            root.addView(row)
        }

        val b = ui.hrow()
        b.addView(ui.key("123", 1.4f, ui.cur.special, 16f, fast = true) { mode = 1; render() })
        b.addView(ui.key("√π", 1.2f, ui.cur.special, 16f, fast = true) { mode = 2; render() })
        b.addView(ui.key("espace", 4f, size = 14f, fast = true) { type(" ") })
        b.addView(ui.key(".", 1f, fast = true) { type(".") })
        b.addView(ui.key("⏎", 1.4f, ui.cur.special, fast = true) { enter() })
        root.addView(b)
        updateShift()
        updateSuggestions()
    }

    private fun symbols() {
        val rows = listOf("1234567890", "@#\$_&-+()/", "*\"':;!?%=<", "éèêàùçôîñë")
        for (r in rows) {
            val row = ui.hro
