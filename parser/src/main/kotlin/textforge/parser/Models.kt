package textforge.parser

data class ChapterIndex(
    val number: Int,
    val title: String,
    val startByte: Long,
    val endByte: Long
)
