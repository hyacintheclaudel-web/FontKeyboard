package com.hyacinthe.fontkeyboard

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
        p.getString("learned", "")?.split(",")?.forEach { e ->
            val parts = e.split(":")
            if (parts.size == 2) parts[1].toIntOrNull()?.let { learned[parts[0]] = it }
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
        val s = learned.entries.joinToString(",") { it.key + ":" + it.value }
        getSharedPreferences("fk", MODE_PRIVATE).edit().putString("learned", s).apply()
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
            val tv = TextView(this).apply {
                textSize = 16f
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(cur.text)
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f)
                setOnClickListener { (tag as? String)?.let { applySuggestion(it) } }
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
                letterKeys.add(k to ch)
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
                    row.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
                    })
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

    // ---------- Saisie ----------

    /** Écrit du texte brut, sans toucher au mot en cours. */
    private fun commit(s: String) {
        currentInputConnection?.commitText(s, 1)
    }

    /** Écrit un caractère qui termine le mot (espace, ponctuation, symbole). */
    private fun type(s: String) {
        endWord()
        commit(s)
    }

    private fun typeLetter(ch: Char) {
        val up = shiftState > 0
        val c = if (up) ch.uppercaseChar() else ch
        val m = FONTS[fontIdx].map(c)
        commit(m)
        addToWord(ch, m, up)
        if (shiftState == 1) { shiftState = 0; updateShift() }
    }

    private fun typeAccent(ch: Char) {
        val m = FONTS[fontIdx].map(ch)
        commit(m)
        addToWord(ch, m, false)
    }

    private fun doDelete() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        if (lens.isNotEmpty()) {
            val l = lens.removeAt(lens.size - 1)
            mapped.setLength(mapped.length - l)
            wordPlain.setLength(wordPlain.length - 1)
            updateSuggestions()
        }
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
        endWord()
        val opts = currentInputEditorInfo?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    // ---------- Suggestions ----------

    private fun norm(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s.lowercase()) {
            sb.append(
                when (c) {
                    'é', 'è', 'ê', 'ë' -> 'e'
                    'à', 'â', 'ä' -> 'a'
                    'ù', 'û', 'ü' -> 'u'
                    'î', 'ï' -> 'i'
                    'ô', 'ö' -> 'o'
                    'ç' -> 'c'
                    'ñ' -> 'n'
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    private fun addToWord(ch: Char, m: String, up: Boolean) {
        if (wordPlain.isEmpty()) {
            upperFirst = up
            allUpper = true
        }
        if (!up) allUpper = false
        wordPlain.append(ch)
        mapped.append(m)
        lens.add(m.length)
        updateSuggestions()
    }

    private fun resetWord() {
        wordPlain.setLength(0)
        mapped.setLength(0)
        lens.clear()
        updateSuggestions()
    }

    private fun endWord() {
        if (wordPlain.length >= 2) learn(wordPlain.toString())
        resetWord()
    }

    private fun learn(w: String) {
        learned[w] = (learned[w] ?: 0) + 1
        if (learned.size > 1500) {
            val it = learned.entries.iterator()
            repeat(100) { if (it.hasNext()) { it.next(); it.remove() } }
        }
        learnCount++
        if (learnCount % 15 == 0) saveLearned()
    }

    private fun applyCase(s: String): String = when {
        allUpper && wordPlain.length >= 2 -> s.uppercase()
        upperFirst -> s.replaceFirstChar { it.uppercaseChar() }
        else -> s
    }

    private fun computeSuggestions(): List<String> {
        if (wordPlain.isEmpty()) return emptyList()
        val typed = wordPlain.toString()
        val p = norm(typed)
        val scores = HashMap<String, Int>()
        for (i in DICT_WORDS.indices) {
            if (dictNorm[i].startsWith(p)) scores[DICT_WORDS[i]] = 1000 - minOf(i, 999)
        }
        for ((w, c) in learned) {
            if (norm(w).startsWith(p)) scores[w] = (scores[w] ?: 0) + c * 200
        }
        return scores.entries
            .filter { it.key != typed }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length })
            .take(3)
            .map { applyCase(it.key) }
    }

    private fun updateSuggestions() {
        if (sugViews.isEmpty()) return
        val list = computeSuggestions()
        for (i in sugViews.indices) {
            val s = list.getOrNull(i)
            sugViews[i].text = s ?: ""
            sugViews[i].tag = s
        }
    }

    private fun applySuggestion(s: String) {
        val ic = currentInputConnection ?: return
        if (wordPlain.isEmpty()) return
        val before = ic.getTextBeforeCursor(mapped.length, 0)?.toString()
        if (before != mapped.toString()) { resetWord(); return }
        val out = s.map { FONTS[fontIdx].map(it) }.joinToString("")
        ic.beginBatchEdit()
        ic.deleteSurroundingText(mapped.length, 0)
        ic.commitText("$out ", 1)
        ic.endBatchEdit()
        learn(s.lowercase())
        resetWord()
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

    /** fast = true : la touche tape dès que le doigt touche l'écran. */
    private fun key(
        label: String,
        weight: Float = 1f,
        bg: Int = cur.key,
        size: Float = 20f,
        fast: Boolean = false,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        text = label
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(cur.text)
        background = round(bg)
        layoutParams = LinearLayout.LayoutParams(0, dp(46), weight).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
        if (fast) {
            setOnTouchListener { v, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { v.alpha = 0.6f; onClick() }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.alpha = 1f
                }
                true
            }
        } else {
            setOnClickListener { onClick() }
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
                    v.alpha = 0.6f
                    action()
                    handler.postDelayed(r, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f
                    handler.removeCallbacks(r)
                }
            }
            true
        }
    }
}
