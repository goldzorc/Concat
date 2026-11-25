// SPDX-License-Identifier: AGPL-3.0-or-later

package app.concat.editor.ui.editor

import java.util.Locale

/** One subtitle: start and end on the timeline in seconds, and its words. */
data class SubtitleLine(val start: Double, val end: Double, val text: String)

/** Reading and writing SRT and ASS files. */
object Subtitles {
    fun parse(name: String, content: String): List<SubtitleLine> {
        val text = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val ass = name.lowercase().let { it.endsWith(".ass") || it.endsWith(".ssa") } || text.contains("[Events]")
        val lines = if (ass) parseAss(text) else parseSrt(text)
        return lines.filter { it.text.isNotBlank() && it.end > it.start }.sortedBy { it.start }
    }

    private fun parseSrt(text: String): List<SubtitleLine> {
        val out = ArrayList<SubtitleLine>()
        for (block in text.split(Regex("\n{2,}"))) {
            val rows = block.trim().split("\n")
            val at = rows.indexOfFirst { it.contains("-->") }
            if (at < 0) continue
            val times = rows[at].split("-->")
            if (times.size < 2) continue
            val start = clock(times[0]) ?: continue
            val end = clock(times[1]) ?: continue
            out.add(SubtitleLine(start, end, clean(rows.drop(at + 1).joinToString("\n"))))
        }
        return out
    }

    private fun parseAss(text: String): List<SubtitleLine> {
        val out = ArrayList<SubtitleLine>()
        var inEvents = false
        var fields = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
        for (raw in text.split("\n")) {
            val row = raw.trim()
            if (row.startsWith("[") && row.endsWith("]")) {
                inEvents = row.equals("[Events]", ignoreCase = true)
                continue
            }
            if (!inEvents) continue
            if (row.startsWith("Format:", ignoreCase = true)) {
                fields = row.substringAfter(':').split(",").map { it.trim().lowercase() }
            } else if (row.startsWith("Dialogue:", ignoreCase = true)) {
                val parts = row.substringAfter(':').trimStart().split(",", limit = fields.size)
                val startAt = fields.indexOf("start")
                val endAt = fields.indexOf("end")
                val textAt = fields.indexOf("text")
                if (startAt < 0 || endAt < 0 || textAt < 0 || parts.size <= textAt) continue
                val start = clock(parts[startAt]) ?: continue
                val end = clock(parts[endAt]) ?: continue
                out.add(SubtitleLine(start, end, clean(parts[textAt])))
            }
        }
        return out
    }

    private fun clean(raw: String): String = raw
        .replace(Regex("\\{[^}]*\\}"), "")
        .replace(Regex("<[^>]+>"), "")
        .replace("\\N", "\n")
        .replace("\\n", "\n")
        .replace("\\h", " ")
        .trim()

    /** "1:02:03.45", "00:02:03,450" or "02:03.5" as seconds. */
    private fun clock(raw: String): Double? {
        val token = raw.trim().split(Regex("\\s+")).firstOrNull() ?: return null
        val parts = token.replace(',', '.').split(':')
        if (parts.size < 2 || parts.size > 3) return null
        val seconds = parts[parts.size - 1].toDoubleOrNull() ?: return null
        val minutes = parts[parts.size - 2].toDoubleOrNull() ?: return null
        var hours = 0.0
        if (parts.size == 3) hours = parts[0].toDoubleOrNull() ?: return null
        return hours * 3600 + minutes * 60 + seconds
    }

    fun toSrt(lines: List<SubtitleLine>): String {
        val out = StringBuilder()
        lines.forEachIndexed { i, line ->
            out.append(i + 1).append('\n')
            out.append(srtStamp(line.start)).append(" --> ").append(srtStamp(line.end)).append('\n')
            out.append(line.text.trim()).append("\n\n")
        }
        return out.toString()
    }

    private fun srtStamp(seconds: Double): String {
        val ms = Math.round(seconds.coerceAtLeast(0.0) * 1000)
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, ms % 1000)
    }

    fun toAss(lines: List<SubtitleLine>, width: Int, height: Int): String {
        val out = StringBuilder()
        out.append("[Script Info]\n")
        out.append("ScriptType: v4.00+\n")
        out.append("PlayResX: ").append(width).append('\n')
        out.append("PlayResY: ").append(height).append("\n\n")
        out.append("[V4+ Styles]\n")
        out.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
        out.append("Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,1,2,10,10,30,1\n\n")
        out.append("[Events]\n")
        out.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
        for (line in lines) {
            out.append("Dialogue: 0,").append(assStamp(line.start)).append(',').append(assStamp(line.end))
            out.append(",Default,,0,0,0,,").append(line.text.trim().replace("\n", "\\N")).append('\n')
        }
        return out.toString()
    }

    private fun assStamp(seconds: Double): String {
        val cs = Math.round(seconds.coerceAtLeast(0.0) * 100)
        return String.format(Locale.US, "%d:%02d:%02d.%02d", cs / 360000, cs / 6000 % 60, cs / 100 % 60, cs % 100)
    }
}
