package your.package.parser

import your.package.storage.NovelBlock
import your.package.storage.NovelDocument

object NovelParser {

    fun parse(source: String): NovelDocument {
        val blocks = mutableListOf<NovelBlock>()
        val paragraph = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                val text = paragraph.joinToString("\n").trim()
                if (text.isNotEmpty()) {
                    blocks.add(
                        NovelBlock.Paragraph(
                            text = text,
                            fullHighlight = false
                        )
                    )
                }
                paragraph.clear()
            }
        }

        source.lines().forEach { line ->
            val heading = Regex("""^(#{1,6})\s+(.+)$""").find(line)

            if (heading != null) {
                flushParagraph()
                blocks.add(
                    NovelBlock.Heading(
                        level = heading.groupValues[1].length,
                        title = heading.groupValues[2].trim()
                    )
                )
            } else if (line.isBlank()) {
                flushParagraph()
            } else {
                paragraph.add(line)
            }
        }

        flushParagraph()
        return NovelDocument(blocks)
    }
}
