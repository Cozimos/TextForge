package textforge.parser

object NovelParser {
    fun parse(source: String): NovelDocument =
        NovelMarkdownParser.parse(source)
}
