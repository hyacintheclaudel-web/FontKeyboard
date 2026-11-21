package com.hyacinthe.fontkeyboard

/** Une police = une table de correspondance vers les caractères Unicode "fantaisie". */
class FontStyle(
    private val lower: Int = -1,
    private val upper: Int = -1,
    private val digit: Int = -1,
    private val smallCaps: String? = null,
    private val overrides: Map<Char, String> = emptyMap(),
    private val suffix: String = ""
) {
    private fun cp(base: Int, offset: Int) = String(Character.toChars(base + offset))

    fun map(c: Char): String {
        val base = overrides[c] ?: when {
            smallCaps != null && (c in 'a'..'z' || c in 'A'..'Z') ->
                smallCaps[c.lowercaseChar() - 'a'].toString()
            c in 'a'..'z' && lower >= 0 -> cp(lower, c - 'a')
            c in 'A'..'Z' && upper >= 0 -> cp(upper, c - 'A')
            c in '0'..'9' && digit >= 0 -> cp(digit, c - '0')
            else -> c.toString()
        }
        return if (suffix.isNotEmpty() && c != ' ') base + suffix else base
    }
}

private val bubbleDigits: Map<Char, String> =
    (1..9).associate { ('0' + it) to (0x2460 + it - 1).toChar().toString() } + mapOf('0' to "\u24EA")

private val doubleStruck: Map<Char, String> = mapOf(
    'C' to "\u2102", 'H' to "\u210D", 'N' to "\u2115", 'P' to "\u2119",
    'Q' to "\u211A", 'R' to "\u211D", 'Z' to "\u2124"
)

/** Ordre = ordre des pastilles dans la barre du clavier. */
val FONTS: List<FontStyle> = listOf(
    FontStyle(),
    FontStyle(0x1D41A, 0x1D400, 0x1D7CE),
    FontStyle(0x1D44E, 0x1D434, overrides = mapOf('h' to "\u210E")),
    FontStyle(0x1D482, 0x1D468),
    FontStyle(0x1D4EA, 0x1D4D0),
    FontStyle(0x1D586, 0x1D56C),
    FontStyle(0x1D552, 0x1D538, 0x1D7D8, overrides = doubleStruck),
    FontStyle(0x1D5BA, 0x1D5A0, 0x1D7E2),
    FontStyle(0x1D5EE, 0x1D5D4, 0x1D7EC),
    FontStyle(0x1D68A, 0x1D670, 0x1D7F6),
    FontStyle(0x24D0, 0x24B6, overrides = bubbleDigits),
    FontStyle(0x1F130, 0x1F130),
    FontStyle(0x1F170, 0x1F170),
    FontStyle(smallCaps = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ"),
    FontStyle(0xFF41, 0xFF21, 0xFF10),
    FontStyle(suffix = "\u0336"),
    FontStyle(suffix = "\u0332")
)

/** Symboles mathématiques (séparés par des espaces). */
const val MATH_SYMBOLS =
    "+ − × ÷ = ≠ ≈ ± √ ∛ ² ³ ⁿ ½ ¼ ¾ π ∞ ° % ≤ ≥ < > ∈ ∉ ⊂ ⊃ ∪ ∩ " +
    "∑ ∏ ∫ ∂ ∆ ∇ α β γ θ λ μ σ φ ω Ω ∀ ∃ ∅ → ⇒ ⇔ ∧ ∨ ¬ ∥ ⊥ ∠ △ " +
    "( ) [ ] { } | ‰ ℕ ℤ ℚ ℝ ℂ ⁻¹ ⁰ ¹ ⁴ ⁵ ⁶ ⁷ ⁸ ⁹ ₀ ₁ ₂ ₃ ℓ ′ ″ " +
    "∝ ≡ ≅ ∼ ∖ ⌊ ⌋ ⌈ ⌉ ≪ ≫ ∴ ∵ ∘ ⋅ ⁄"
