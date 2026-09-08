package app.votify.mobile.data

data class LyricLine(val timeMs: Long, val text: String)

/**
 * Parsed lyrics. [lines] is time-sorted when [synced] is true; otherwise every line has timeMs = 0
 * and the UI shows them as a plain scrollable text.
 */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean) {

    /** Index of the line that should be highlighted at [positionMs], or -1 before the first one. */
    fun activeIndex(positionMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }

    companion object {
        private val TAG = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

        /** Prefer the LRC text; fall back to plain lyrics; null when there is nothing to show. */
        fun from(response: LyricsResponse): Lyrics? {
            response.syncedLyrics?.takeIf { it.isNotBlank() }?.let { lrc ->
                val parsed = parseLrc(lrc)
                if (parsed.isNotEmpty()) return Lyrics(parsed, synced = true)
            }
            response.plainLyrics?.takeIf { it.isNotBlank() }?.let { plain ->
                val lines = plain.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { LyricLine(0, it) }
                if (lines.isNotEmpty()) return Lyrics(lines, synced = false)
            }
            return null
        }

        /** Parses standard LRC: `[mm:ss.xx] text`, multiple tags per line allowed. */
        fun parseLrc(lrc: String): List<LyricLine> {
            val out = ArrayList<LyricLine>()
            for (raw in lrc.lines()) {
                val tags = TAG.findAll(raw).toList()
                if (tags.isEmpty()) continue
                val text = raw.substring(tags.last().range.last + 1).trim()
                for (t in tags) {
                    val min = t.groupValues[1].toLong()
                    val sec = t.groupValues[2].toLong()
                    val fracRaw = t.groupValues[3]
                    val frac = when (fracRaw.length) {
                        0 -> 0L
                        1 -> fracRaw.toLong() * 100
                        2 -> fracRaw.toLong() * 10
                        else -> fracRaw.take(3).toLong()
                    }
                    out += LyricLine(min * 60_000 + sec * 1000 + frac, text)
                }
            }
            return out.sortedBy { it.timeMs }
        }
    }
}
