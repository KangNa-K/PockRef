package com.pockref.app.data

internal fun pureRefNoteFontSize(html: String): Float {
    val fontSizePattern = Regex("font-size\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(px|pt)", RegexOption.IGNORE_CASE)
    // Qt HTML often declares a default body font before the actual span font.
    val contentStyles = Regex("(?is)<(?:span|p|div|h[1-6])\\b[^>]*>").findAll(html)
    val match = contentStyles.mapNotNull { fontSizePattern.find(it.value) }.firstOrNull()
        ?: fontSizePattern.find(html) ?: return 16f
    val size = match.groupValues[1].toFloatOrNull() ?: return 16f
    val pixels = if (match.groupValues[2].equals("pt", ignoreCase = true)) size * 96f / 72f else size
    return pixels.takeIf { it.isFinite() && it > 0f } ?: 16f
}
