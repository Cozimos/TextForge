package textforge.storage

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import textforge.parser.NovelBlock
import textforge.parser.ChapterIndexer
import textforge.parser.NovelParser
import java.io.File
import java.util.UUID

object NovelStore {

    private fun root(context: Context) =
        File(context.filesDir, "novels").apply { mkdirs() }

    private fun folder(context: Context, id: String) =
        File(root(context), id).apply { mkdirs() }

    fun importNovel(context: Context, uri: Uri): String {
        val id = UUID.randomUUID().toString()
        val dir = folder(context, id)

        val source = File(dir, "source.md")

        context.contentResolver.openInputStream(uri)!!.use { input ->
            source.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val title = context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else "Untitled"
        } ?: "Untitled"

        JSONObject().apply {
            put("id", id)
            put("title", title)
            put("sourceUri", uri.toString())
        }.write(File(dir, "metadata.json"))

        buildCache(context, id)

        return id
    }

    fun buildCache(context: Context, id: String) {
        val dir = folder(context, id)
        val bytes = File(dir, "source.md").readBytes()
        val chapters = ChapterIndexer.index(bytes)

        val indexJson = JSONArray()

        chapters.forEach { chapter ->
            indexJson.put(
                JSONObject().apply {
                    put("number", chapter.number)
                    put("title", chapter.title)
                    put("startByte", chapter.startByte)
                    put("endByte", chapter.endByte)
                }
            )

            val chapterBytes = bytes.copyOfRange(
                chapter.startByte.toInt(),
                chapter.endByte.toInt()
            )

            val document = NovelParser.parse(
                chapterBytes.toString(Charsets.UTF_8)
            )

            val blocksJson = JSONArray()

            document.blocks.forEach { block ->
                blocksJson.put(
                    when (block) {
                        is NovelBlock.Heading -> JSONObject().apply {
                            put("type", "heading")
                            put("level", block.level)
                            put("title", block.title)
                        }

                        is NovelBlock.Paragraph -> JSONObject().apply {
                            put("type", "paragraph")
                            put("segments", JSONArray().apply {
    block.segments.forEach { segment ->
        put(JSONObject().apply {
            put("text", segment.text)
            put("highlighted", segment.highlighted)
            put("italic", segment.italic)
        })
    }
})
                            put("fullHighlight", block.fullHighlight)
                        }
                    }
                )
            }

            val chapterDir = File(dir, "chapters").apply { mkdirs() }
            JSONObject().put("blocks", blocksJson)
                .write(File(chapterDir, "%03d.json".format(chapter.number)))
        }

        JSONObject().put("chapters", indexJson)
            .write(File(dir, "index.json"))

        saveProgress(
            context,
            id,
            ReadingProgress(0, 0, chapters.firstOrNull()?.startByte ?: 0L)
        )
    }

    fun getLibrary(context: Context): List<NovelMetadata> {
        return root(context).listFiles()?.mapNotNull { dir ->
            val file = File(dir, "metadata.json")
            if (!file.exists()) return@mapNotNull null

            val json = JSONObject(file.readText())
            NovelMetadata(
                id = json.getString("id"),
                title = json.getString("title"),
                sourceUri = json.getString("sourceUri")
            )
        } ?: emptyList()
    }

    fun getChapters(context: Context, id: String): List<ChapterIndex> {
        val json = JSONObject(
            File(folder(context, id), "index.json").readText()
        )

        val array = json.getJSONArray("chapters")

        return List(array.length()) { i ->
            val item = array.getJSONObject(i)
            ChapterIndex(
                item.getInt("number"),
                item.getString("title"),
                item.getLong("startByte"),
                item.getLong("endByte")
            )
        }
    }

    fun getChapterCache(
        context: Context,
        id: String,
        chapter: Int
    ): JSONArray {
        val file = File(
            folder(context, id),
            "chapters/%03d.json".format(chapter)
        )
        return JSONObject(file.readText()).getJSONArray("blocks")
    }

    fun saveProgress(
        context: Context,
        id: String,
        progress: ReadingProgress
    ) {
        JSONObject().apply {
            put("chapterNumber", progress.chapterNumber)
            put("blockIndex", progress.blockIndex)
            put("byteOffset", progress.byteOffset)
        }.write(File(folder(context, id), "progress.json"))
    }

    fun getProgress(context: Context, id: String): ReadingProgress {
        val file = File(folder(context, id), "progress.json")
        if (!file.exists()) return ReadingProgress(0, 0, 0L)

        val json = JSONObject(file.readText())
        return ReadingProgress(
            json.getInt("chapterNumber"),
            json.getInt("blockIndex"),
            json.getLong("byteOffset")
        )
    }

    private fun JSONObject.write(file: File) {
        file.writeText(toString(2))
    }
}
