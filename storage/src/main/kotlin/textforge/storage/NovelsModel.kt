package textforge.storage

data class NovelMetadata(
    val id: String,
    val title: String,
    val sourceUri: String
)

data class ReadingProgress(
    val chapterNumber: Int,
    val blockIndex: Int,
    val byteOffset: Long
)
