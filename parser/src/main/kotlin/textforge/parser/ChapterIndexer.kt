package textforge.parser

object ChapterIndexer {
    private val HEADING = Regex("""^#{1,6}\s+(.+)$""")

    fun index(bytes: ByteArray): List<ChapterIndex> {
        val headings = ArrayList<Pair<Long, String>>()
        val size = bytes.size
        val nl = '\n'.code.toByte()
        val hash = '#'.code.toByte()
        var pos = 0

        while (pos < size) {
            var end = pos
            while (end < size && bytes[end] != nl) end++

            if (bytes[pos] == hash) {
                val line = String(bytes, pos, end - pos, Charsets.UTF_8).trimEnd('\r')
                HEADING.find(line)?.let {
                    headings.add(pos.toLong() to it.groupValues[1].trim())
                }
            }
            pos = end + 1
        }

        if (headings.isEmpty()) {
            return listOf(ChapterIndex(0, "Novel", 0L, size.toLong()))
        }

        return headings.mapIndexed { number, heading ->
            ChapterIndex(
                number = number,
                title = heading.second,
                startByte = heading.first,
                endByte = headings.getOrNull(number + 1)?.first ?: size.toLong()
            )
        }
    }
}
