package com.hyacinthe.fontkeyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

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
    private var mathCat = 0
    private var mode = 0            // 0 = lettres, 1 = chiffres/accents, 2 = maths
    private var shiftState = 0      // 0 = off, 1 = une lettre, 2 = verrouillé
    private var lastShift = 0L

    private lateinit var root: LinearLayout
    private val chips = ArrayList<TextView>()
    private val letterKeys = ArrayList<Pair<TextView, Char>>()
    private val sugViews = ArrayList<TextView>()
    private var shiftKey: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    // ----- Suggestions de mots -----
    private val wordPlain = StringBuilder()     // lettres tapées (sans police)
    private val mapped = StringBuilder()        // texte réellement écrit (avec police)
    private val lens = ArrayList<Int>()         // longueur écrite pour chaque lettre
    private var upperFirst = false
    private var allUpper = true
    private val learned = HashMap<String, Int>()
    private var learnCount = 0
    private val dictNorm = DICT_WORDS.map { norm(it) }

    override fun onCreate() {
        super.onCreate()
        val p = getSharedPreferences("fk", MODE_PRIVATE)
        fontIdx = p.getInt("font", 0).coerceIn(0, FONTS.size - 1)
        themeIdx = p.getInt("theme", 0).coerceIn(0, themes.size - 1)
        val saved = p.getString("learned", "") ?: ""
        for (entry in saved.split(",")) {
            val parts = entry.split(":")
            if (parts.size == 2) {
                val n = parts[1].toIntOrNull()
                if (n != null) learned[parts[0]] = n
            }
        }
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

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetWord()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        saveLearned()
        resetWord()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (wordPlain.isNotEmpty() && (newSelStart != newSelEnd || newSelStart < mapped.length)) resetWord()
    }

    private fun save() {
        getSharedPreferences("fk", MODE_PRIVATE).edit()
            .putInt("font", fontIdx).putInt("theme", themeIdx).apply()
    }

    private fun saveLearned() {
        val sb = StringBuilder()
        for ((w, c) in learned) {
            if (sb.isNotEmpty()) sb.append(",")
            sb.append(w).append(":").append(c)
        }
        getSharedPreferences("fk", MODE_PRIVATE).edit().putString("learned", sb.toString()).apply()
    }

    // ---------- Affichage ----------

    private fun render() {
        cur = themes[themeIdx]
        root.removeAllViews()
        sugViews.clear()
        root.setBackgroundColor(cur.bg)
        root.addView(fontBar())
        when (mode) {
            0 -> { root.addView(suggestionBar()); letters() }
            1 -> symbols()
            else -> maths()
        }
        if (mode != 0) bottomAlt()
    }

    private fun suggestionBar(): View {
        val bar = hrow()
        for (i in 0 until 3) {
            val tv = TextView(this)
            tv.textSize = 16f
            tv.gravity = Gravity.CENTER
            tv.maxLines = 1
            tv.setTextColor(cur.text)
            tv.layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f)
            tv.setOnClickListener {
                val s = tv.tag as? String
                if (s != null) applySuggestion(s)
            }
            sugViews.add(tv)
            bar.addView(tv)
        }
        return bar
    }

    private fun fontBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(small("🎨") { themeIdx = (themeIdx + 1) % themes.size; save(); render() })
        bar.addView(small("📋") { pastePlain() })
        bar.addView(small("🔠") { pasteOrConvert() })
        bar.addView(small("📄") { copyText() })

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
                setOnClickListener { fontIdx = i; refreshChips(); save() }
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
    }

    private fun letters() {
        letterKeys.clear()
        shiftKey = null
        val rows = listOf("azertyuiop", "qsdfghjklm", "wxcvbn")

        rows.forEachIndexed { i, r ->
            val row = hrow()
            if (i == 2) {
                val s = key("⇧", 1.5f, cur.special, fast = true) { toggleShift() }
                shiftKey = s
                row.addView(s)
            }
            r.forEach { ch ->
                val k = key(ch.toString(), fast = true) { typeLetter(ch) }
                letterKeys.add(Pair(k, ch))
                row.addView(k)
            }
            if (i == 2) {
                val d = key("⌫", 1.5f, cur.special) {}
                repeatable(d) { doDelete() }
                row.addView(d)
            }
            root.addView(row)
        }

        val b = hrow()
        b.addView(key("123", 1.4f, cur.special, 16f, fast = true) { mode = 1; render() })
        b.addView(key("√π", 1.2f, cur.special, 16f, fast = true) { mode = 2; render() })
        b.addView(key("espace", 4f, size = 14f, fast = true) { type(" ") })
        b.addView(key(".", 1f, fast = true) { type(".") })
        b.addView(key("⏎", 1.4f, cur.special, fast = true) { enter() })
        root.addView(b)
        updateShift()
        updateSuggestions()
    }

    private fun symbols() {
        listOf("1234567890", "@#\$_&-+()/", "*\"':;!?%=<", "éèêàùçôîñë").forEach { r ->
            val row = hrow()
            r.forEach { ch ->
                row.addView(key(ch.toString(), fast = true) {
                    if (ch.isLetter()) typeAccent(ch) else type(FONTS[fontIdx].map(ch))
                })
            }
            root.addView(row)
        }
    }

    private fun maths() {
        val cats = ArrayList<TextView>()
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sv = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(170))
            isVerticalScrollBarEnabled = false
            addView(grid)
        }

        fun fill() {
            cats.forEachIndexed { i, c ->
                val sel = i == mathCat
                c.background = round(if (sel) cur.accent else cur.key)
                c.setTextColor(if (sel) Color.WHITE else cur.text)
            }
            grid.removeAllViews()
            sv.scrollTo(0, 0)
            MATH_CATEGORIES[mathCat].second.split(" ").filter { it.isNotEmpty() }.chunked(8).forEach { chunk ->
                val row = hrow()
                chunk.forEach { s -> row.addView(key(s, size = 18f) { type(s) }) }
                repeat(8 - chunk.size) {
                    val spacer = View(this)
                    spacer.layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
                    row.addView(spacer)
                }
                grid.addView(row)
            }
        }

        val catBar = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val catRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        MATH_CATEGORIES.forEachIndexed { i, cat ->
            val c = TextView(this).apply {
                text = cat.first
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(7), dp(12), dp(7))
                layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) }
                setOnClickListener { mathCat = i; fill() }
            }
            cats.add(c)
            catRow.addView(c)
        }
        catBar.addView(catRow)
        root.addView(catBar)
        root.addView(sv)
        fill()
    }

    private fun bottomAlt() {
        val b = hrow()
        b.addView(key("ABC", 1.4f, cur.special, 16f, fast = true) { mode = 0; render() })
        b.addView(key(if (mode == 1) "√π" else "123", 1.2f, cur.special, 16f, fast = true) { mode = if (mode == 1) 2 else 1; render() })
        b.addView(key("espace", 3f, size = 14f, fast = true) { type(" ") })
        val d = key("⌫", 1.2f, cur.special) {}
        repeatable(d) { doDelete() }
        b.addView(d)
        b.addView(key("⏎", 1.4f, cur.special, fast = true) { enter() })
        root.addView(b)
    }

    // ---------- Copier / coller ----------

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun clipText(): String? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val t: CharSequence? = clip.getItemAt(0).coerceToText(this)
        return t?.toString()
    }

    /** Transforme un texte normal dans la police sélectionnée. */
    private fun convert(s: String): String {
        val sb = StringBuilder()
        for (c in s) sb.append(FONTS[fontIdx].map(c))
        return sb.toString()
    }

    private fun pastePlain() {
        val t = clipText()
        if (t == null || t.isEmpty()) { toast("Presse-papiers vide"); return }
        endWord()
        commit(t)
    }

    /** Texte sélectionné : le convertit. Sinon : colle le presse-papiers converti. */
    private fun pasteOrConvert() {
        val ic = currentInputConnection ?: return
        endWord()
        val sel: CharSequence? = ic.getSelectedText(0)
        if (sel != null && sel.isNotEmpty()) {
            val out = convert(sel.toString())
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            commit(out)
            return
        }
        val t = clipText()
        if (t == null || t.isEmpty()) { toast("Presse-papiers vide"); return }
        commit(convert(t))
    }

    /** Copie la sélection, ou tout le texte du champ s'il n'y a pas de sélection. */
    private fun copyText() {
        val ic = currentInputConnection ?: return
        val sel: CharSequence? = ic.getSelectedText(0)
        var txt = ""
        if (sel != null && sel.isNotEmpty()) {
            txt = sel.toString()
        } else {
            val before: CharSequence? = ic.getTextBeforeCursor(10000, 0)
            val after: CharSequence? = ic.getTextAfterCursor(10000, 0)
            txt = (before?.toString() ?: "") + (after?.toString() ?: "")
        }
        if (txt.isEmpty()) { toast("Rien à copier"); return }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Font Keyboard", txt))
        toast("Copié")
    }

    // ---------- Saisie ----------

    /** Écrit du texte brut, sans toucher au mot en cours
