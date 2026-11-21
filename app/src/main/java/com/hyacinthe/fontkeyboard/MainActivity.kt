package com.hyacinthe.fontkeyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        col.addView(TextView(this).apply {
            text = "Font Keyboard"
            textSize = 26f
        })
        col.addView(TextView(this).apply {
            text = "\n1. Active le clavier dans les réglages.\n2. Choisis-le comme clavier actuel.\n3. Teste-le dans la zone ci-dessous.\n"
            textSize = 16f
        })
        col.addView(Button(this).apply {
            text = "1. Activer le clavier"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        col.addView(Button(this).apply {
            text = "2. Choisir le clavier"
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        })
        col.addView(EditText(this).apply {
            hint = "3. Écris ici pour tester"
        })

        setContentView(ScrollView(this).apply { addView(col) })
    }
}
