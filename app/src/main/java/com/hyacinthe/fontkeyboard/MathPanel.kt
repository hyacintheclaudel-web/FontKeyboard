package com.hyacinthe.fontkeyboard

import android.content.Context
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MathPanel(
    private val ctx: Context,
    private val ui: Ui,
    private val onSymbol: (String) -> Unit
) {
    private var cat = 0

    fun addTo(root: LinearLayout) {
        val tabs = ArrayList<TextView>()
        val grid = LinearLayout(ctx)
        grid.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(ctx)
        scroll.layoutParams = LinearLayout.LayoutParams(ui.MATCH, ui.dp(170))
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(grid)

        fun fill() {
            for (i in tabs.indices) ui.select(tabs[i], i == cat)
            grid.removeAllViews()
            scroll.scrollTo(0, 0)
            val symbols = MATH_CATEGORIES[cat].second.split(" ").filter { it.isNotEmpty() }
            for (chunk in symbols.chunked(8)) {
                val row = ui.hrow()
                for (s in chunk) row.addView(ui.key(s, size = 18f) { onSymbol(s) })
                for (k in 0 until 8 - chunk.size) {
                    val spacer = View(ctx)
                    val lp = LinearLayout.LayoutParams(0, ui.dp(46), 1f)
                    lp.setMargins(ui.dp(2), ui.dp(3), ui.dp(2), ui.dp(3))
                    spacer.layoutParams = lp
                    row.addView(spacer)
                }
                grid.addView(row)
            }
        }

        val bar = HorizontalScrollView(ctx)
        bar.isHorizontalScrollBarEnabled = false
        bar.layoutParams = LinearLayout.LayoutParams(ui.MATCH, ui.WRAP)
        val tabRow = LinearLayout(ctx)
        tabRow.orientation = LinearLayout.HORIZONTAL
        for (i in MATH_CATEGORIES.indices) {
            val t = ui.chip(MATH_CATEGORIES[i].first, 14f, 12, 7)
            t.setOnClickListener {
                cat = i
                fill()
            }
            tabs.add(t)
            tabRow.addView(t)
        }
        bar.addView(tabRow)
        root.addView(bar)
        root.addView(scroll)
        fill()
    }
}
