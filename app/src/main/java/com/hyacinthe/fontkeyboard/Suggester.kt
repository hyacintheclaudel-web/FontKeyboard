package com.hyacinthe.fontkeyboard

import android.content.SharedPreferences

class Suggester(private val prefs: SharedPreferences) {
    val plain = StringBuilder()
    val mapped = StringBuilder()
    private val lens = ArrayList<Int>()
    private var upperFirst = false
    private var allUpper = true
    private val learned = HashMap<String, Int>()
    private var learnCount = 0
    private val dictNorm: List<String> = DICT_WORDS.map { norm(it) }

    init {
        load()
    }

    private fun load() {
        val saved = prefs.getString("learned", "") ?: ""
        for (entry in saved.split(",")) {
            val parts = entry.split(":")
            if (parts.size == 2) {
                val n = parts[1].toIntOrNull()
                if (n != null) learned[parts[0]] = n
            }
        }
    }

    fun save() {
        val sb = StringBuilder()
        for ((w, c) in learned) {
            if (sb.isNotEmpty()) sb.append(",")
            sb.append(w).append(":").append(c)
        }
        prefs.edit().putString("learned", sb.toString()).apply()
    }

    fun norm(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s.lowercase()) {
            val r = when (c) {
                'é', 'è', 'ê', 'ë' -> 'e'
                'à', 'â', 'ä' -> 'a'
                'ù', 'û', 'ü' -> 'u'
                'î', 'ï' -> 'i'
                'ô', 'ö' -> 'o'
                'ç' -> 'c'
                'ñ' -> 'n'
                else -> c
            }
            sb.append(r)
        }
        return sb.toString()
    }

    fun isEmpty(): Boolean = plain.isEmpty()

    fun add(ch: Char, m: String, up: Boolean) {
        if (plain.isEmpty()) {
            upperFirst = up
            allUpper = true
        }
        if (!up) allUpper = false
        plain.append(ch)
        mapped.append(m)
        lens.add(m.length)
    }

    fun removeLast() {
        if (lens.isEmpty()) return
        val l = lens.removeAt(lens.size - 1)
        mapped.setLength(mapped.length - l)
        plain.setLength(plain.length - 1)
    }

    fun reset() {
        plain.setLength(0)
        mapped.setLength(0)
        lens.clear()
    }

    fun end() {
        if (plain.length >= 2) learn(plain.toString())
        reset()
    }

    fun learn(w: String) {
        learned[w] = (learned[w] ?: 0) + 1
        if (learned.size > 1500) {
            val iter = learned.entries.iterator()
            var n = 0
            while (iter.hasNext() && n < 100) {
                iter.next()
                iter.remove()
                n++
            }
        }
        learnCount++
        if (learnCount % 15 == 0) save()
    }

    private fun applyCase(s: String): String {
        if (allUpper && plain.length >= 2) return s.uppercase()
        if (upperFirst && s.isNotEmpty()) return s.substring(0, 1).uppercase() + s.substring(1)
        return s
    }

    fun suggestions(): List<String> {
        val out = ArrayList<String>()
        if (plain.isEmpty()) return out
        val typed = plain.toString()
        val p = norm(typed)
        val scores = HashMap<String, Int>()
        for (i in DICT_WORDS.indices) {
            if (dictNorm[i].startsWith(p)) scores[DICT_WORDS[i]] = 1000 - minOf(i, 999)
        }
        for ((w, c) in learned) {
            if (norm(w).startsWith(p)) scores[w] = (scores[w] ?: 0) + c * 200
        }
        val list = ArrayList<Pair<String, Int>>()
        for ((w, sc) in scores) {
            if (w != typed) list.add(Pair(w, sc))
        }
        list.sortWith(Comparator { a, b ->
            if (a.second != b.second) b.second - a.second else a.first.length - b.first.length
        })
        for (i in 0 until minOf(3, list.size)) out.add(applyCase(list[i].first))
        return out
    }
}
