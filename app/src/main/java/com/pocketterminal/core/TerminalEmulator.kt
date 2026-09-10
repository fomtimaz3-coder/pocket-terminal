package com.pocketterminal.core

import java.nio.charset.StandardCharsets

data class TerminalCell(
    val value: Char,
    val foreground: Int,
    val background: Int,
    val bold: Boolean = false,
    val underline: Boolean = false
)

data class TerminalLine(val cells: List<TerminalCell>)

data class TerminalSnapshot(
    val lines: List<TerminalLine>,
    val cursorRow: Int,
    val cursorColumn: Int
)

/**
 * A bounded terminal screen model. It handles the ANSI controls produced by
 * Android's interactive shell and keeps the model independent of Compose.
 */
class TerminalEmulator(
    private val maxRows: Int = 2000,
    private val columns: Int = 120
) {
    private val lines = ArrayList<MutableList<TerminalCell>>()
    private var cursorRow = 0
    private var cursorColumn = 0
    private var foreground = GREEN
    private var background = BLACK
    private var bold = false
    private var underline = false
    private var parserState = ParserState.NORMAL
    private val controlSequence = StringBuilder()
    private var pendingUtf8 = ByteArray(0)

    init {
        ensureLine(0)
    }

    @Synchronized
    fun processBytes(bytes: ByteArray, length: Int) {
        if (length <= 0) return
        val incoming = bytes.copyOf(length)
        val combined = pendingUtf8 + incoming
        val safeLength = safeUtf8Length(combined)
        pendingUtf8 = combined.copyOfRange(safeLength, combined.size)
        val text = String(combined, 0, safeLength, StandardCharsets.UTF_8)
        text.forEach(::processChar)
    }

    @Synchronized
    fun clear() {
        lines.clear()
        cursorRow = 0
        cursorColumn = 0
        ensureLine(0)
    }

    @Synchronized
    fun snapshot(): TerminalSnapshot {
        return TerminalSnapshot(
            lines = lines.map { TerminalLine(it.toList()) },
            cursorRow = cursorRow,
            cursorColumn = cursorColumn
        )
    }

    private fun processChar(character: Char) {
        when (parserState) {
            ParserState.NORMAL -> when (character) {
                '\u001B' -> {
                    parserState = ParserState.ESC
                    controlSequence.clear()
                }
                '\n' -> newline()
                '\r' -> cursorColumn = 0
                '\b' -> cursorColumn = (cursorColumn - 1).coerceAtLeast(0)
                '\t' -> cursorColumn = ((cursorColumn / 8) + 1) * 8
                '\u0007' -> Unit
                else -> if (!character.isISOControl()) put(character)
            }

            ParserState.ESC -> when (character) {
                '[' -> {
                    parserState = ParserState.CSI
                    controlSequence.clear()
                }
                'c' -> {
                    clear()
                    parserState = ParserState.NORMAL
                }
                else -> parserState = ParserState.NORMAL
            }

            ParserState.CSI -> {
                controlSequence.append(character)
                if (character in '@'..'~') {
                    applyCsi(controlSequence.toString())
                    parserState = ParserState.NORMAL
                    controlSequence.clear()
                }
            }
        }
    }

    private fun put(character: Char) {
        if (cursorColumn >= columns) {
            cursorColumn = 0
            newline()
        }
        val line = ensureLine(cursorRow)
        while (line.size < cursorColumn) line += blankCell()
        val cell = TerminalCell(character, foreground, background, bold, underline)
        if (cursorColumn < line.size) line[cursorColumn] = cell else line += cell
        cursorColumn++
    }

    private fun newline() {
        cursorRow++
        ensureLine(cursorRow)
        if (lines.size > maxRows) {
            lines.removeAt(0)
            cursorRow--
        }
    }

    private fun applyCsi(sequence: String) {
        if (sequence.isEmpty()) return
        val command = sequence.last()
        val raw = sequence.dropLast(1)
        val privateMode = raw.startsWith("?")
        val params = raw.removePrefix("?").split(';').mapNotNull { it.toIntOrNull() }
        val first = params.firstOrNull()?.takeUnless { it == 0 } ?: 1

        when (command) {
            'A' -> cursorRow = (cursorRow - first).coerceAtLeast(0)
            'B' -> {
                cursorRow = (cursorRow + first).coerceAtMost(maxRows - 1)
                ensureLine(cursorRow)
            }
            'C' -> cursorColumn = (cursorColumn + first).coerceAtMost(columns)
            'D' -> cursorColumn = (cursorColumn - first).coerceAtLeast(0)
            'G', '`' -> cursorColumn = ((params.firstOrNull() ?: 1) - 1).coerceIn(0, columns)
            'd' -> {
                cursorRow = ((params.firstOrNull() ?: 1) - 1).coerceIn(0, maxRows - 1)
                ensureLine(cursorRow)
            }
            'H', 'f' -> {
                cursorRow = ((params.getOrNull(0) ?: 1) - 1).coerceIn(0, maxRows - 1)
                cursorColumn = ((params.getOrNull(1) ?: 1) - 1).coerceIn(0, columns)
                ensureLine(cursorRow)
            }
            'J' -> eraseDisplay(params.firstOrNull() ?: 0)
            'K' -> eraseLine(params.firstOrNull() ?: 0)
            'm' -> applySgr(params)
            'h', 'l' -> if (privateMode && params.contains(25)) Unit
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            2 -> clear()
            0 -> {
                eraseLine(0)
                for (row in cursorRow + 1 until lines.size) lines[row].clear()
            }
            1 -> {
                for (row in 0 until cursorRow) lines[row].clear()
                eraseLine(1)
            }
        }
    }

    private fun eraseLine(mode: Int) {
        val line = ensureLine(cursorRow)
        when (mode) {
            2 -> line.clear()
            0 -> while (line.size > cursorColumn) line.removeAt(line.lastIndex)
            1 -> for (index in 0..cursorColumn.coerceAtMost(line.lastIndex)) line[index] = blankCell()
        }
    }

    private fun applySgr(params: List<Int>) {
        if (params.isEmpty()) {
            resetStyle()
            return
        }
        var index = 0
        while (index < params.size) {
            when (val code = params[index]) {
                0 -> resetStyle()
                1 -> bold = true
                4 -> underline = true
                22 -> bold = false
                24 -> underline = false
                in 30..37 -> foreground = ANSI_COLORS[code - 30]
                39 -> foreground = GREEN
                in 40..47 -> background = ANSI_COLORS[code - 40]
                49 -> background = BLACK
                in 90..97 -> foreground = ANSI_BRIGHT_COLORS[code - 90]
                in 100..107 -> background = ANSI_BRIGHT_COLORS[code - 100]
                38, 48 -> {
                    if (params.getOrNull(index + 1) == 5) {
                        val color = params.getOrNull(index + 2)
                        if (color != null) {
                            if (code == 38) foreground = xtermColor(color)
                            else background = xtermColor(color)
                            index += 2
                        }
                    }
                }
            }
            index++
        }
    }

    private fun resetStyle() {
        foreground = GREEN
        background = BLACK
        bold = false
        underline = false
    }

    private fun ensureLine(row: Int): MutableList<TerminalCell> {
        while (lines.size <= row) lines.add(mutableListOf())
        return lines[row]
    }

    private fun blankCell() = TerminalCell(' ', foreground, background, bold, underline)

    private fun safeUtf8Length(data: ByteArray): Int {
        if (data.isEmpty()) return 0
        var index = data.lastIndex
        var continuation = 0
        while (index >= 0 && data[index].toInt() and 0xC0 == 0x80) {
            continuation++
            index--
        }
        if (index < 0) return 0
        val lead = data[index].toInt() and 0xFF
        val expected = when {
            lead and 0x80 == 0 -> 1
            lead and 0xE0 == 0xC0 -> 2
            lead and 0xF0 == 0xE0 -> 3
            lead and 0xF8 == 0xF0 -> 4
            else -> 1
        }
        return if (continuation > 0 && continuation + 1 < expected) index else data.size
    }

    private enum class ParserState { NORMAL, ESC, CSI }

    companion object {
        const val BLACK = 0xFF0B0D10.toInt()
        const val GREEN = 0xFF9BE15D.toInt()
        private val ANSI_COLORS = intArrayOf(
            0xFF101318.toInt(), 0xFFE06C75.toInt(), 0xFF98C379.toInt(), 0xFFE5C07B.toInt(),
            0xFF61AFEF.toInt(), 0xFFC678DD.toInt(), 0xFF56B6C2.toInt(), 0xFFD7DAE0.toInt()
        )
        private val ANSI_BRIGHT_COLORS = intArrayOf(
            0xFF5C6370.toInt(), 0xFFE88388.toInt(), 0xFFA8D98B.toInt(), 0xFFFFD580.toInt(),
            0xFF79BFFF.toInt(), 0xFFD99AF0.toInt(), 0xFF70D4DD.toInt(), 0xFFFFFFFF.toInt()
        )

        private fun xtermColor(code: Int): Int {
            if (code < 16) return (ANSI_COLORS + ANSI_BRIGHT_COLORS)[code.coerceIn(0, 15)]
            if (code in 232..255) {
                val value = 8 + (code - 232) * 10
                return 0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
            }
            val palette = intArrayOf(0, 95, 135, 175, 215, 255)
            val normalized = code - 16
            val red = palette[normalized / 36]
            val green = palette[(normalized / 6) % 6]
            val blue = palette[normalized % 6]
            return 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
        }
    }
}