package com.hyacinthe.fontkeyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** Transforme un texte normal dans la police donnée. */
fun convertText(s: String, f: FontStyle): String {
    val sb = StringBuilder()
    for (c in s) sb.append(f.map(c))
    return sb.toString()
}

fun readClipboard(ctx: Context): String? {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    val t: CharSequence? = clip.getItemAt(0).coerceToText(ctx)
    return t?.toString()
}

fun writeClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Font Keyboard", text))
}
