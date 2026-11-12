package your.package.parser

import your.package.storage.ChapterIndex

object ChapterIndexer {

    fun index(bytes: ByteArray): List<ChapterIndex> {
        val lines = mutableListOf<Triple<Long, Long, String>>()
        var position = 0L

        bytes.toString(Charsets.UTF_8)
            .split("\n")
            .forEach { line ->
                val lineBytes = (line + "\n").toByteArray(Charsets.UTF_8)
                lines.add(
                    Triple(position, position + lineBytes.size, line)
                )
                position += lineBytes.size
            }

        val headings = lines.mapIndexedNotNull { i, line ->
            val match = Regex("""^#{1,6}\s+(.+)$""")
                .find(line.third.trimEnd('\r'))

            match?.let { Triple(i, line.first, it.groupValues[1].trim()) }
        }

        if (headings.isEmpty()) {
            return listOf(
                ChapterIndex(0, "Novel", 0L, bytes.size.toLong())
            )
        }

        return headings.mapIndexed { number, heading ->
            val end = headings.getOrNull(number + 1)?.second
                ?: bytes.size.toLong()

            ChapterIndex(
                number = number,
                title = heading.third,
                startByte = heading.second,
                endByte = end
            )
        }
    }
}
