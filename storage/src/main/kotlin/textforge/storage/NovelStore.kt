package textforge.storage

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import textforge.parser.NovelBlock
import textforge.parser.ChapterIndexer
import textforge.parser.ChapterIndex
import textforge.parser.NovelParser
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

object NovelStore {
    private const val MAX_CACHE_CHAPTERS = 4
    private const val MAX_MEMORY_BYTES = 16L * 1024 * 1024

    private const val SESSION_PREFS = "reading_session"
    private const val LAST_NOVEL_ID = "last_novel_id"

    fun saveLastNovelId(context: Context, id: String) {
    context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(LAST_NOVEL_ID, id)
        .apply()
}

    fun getRestorableNovelId(context: Context): String? {
        return try {
            val id = context.getSharedPreferences(
                SESSION_PREFS, Context.MODE_PRIVATE
            ).getString(LAST_NOVEL_ID, null) ?: return null
            val dir = File(context.filesDir, "novels/$id")
            if (!dir.isDirectory) return null
            val metadata = File(dir, "metadata.json")
            val index = File(dir, "index.json")
            val progress = File(dir, "progress.json")
            if (!metadata.isFile || !index.isFile || !progress.isFile) return null
            if (JSONObject(metadata.readText()).optString("id") != id) return null
            id
        } catch (_: Exception) {
            null
        }
    }

    private data class CachedChapter(
    val novelId: String,
    val chapter: Int,
    val blocks: JSONArray,
    val estimatedBytes: Long
    )

    private val cacheLock = Any()
    private val memoryCache = mutableListOf<CachedChapter>()
    private val pendingLoads = mutableListOf<Pair<String, Int>>()
    private val prefetchExecutor = Executors.newFixedThreadPool(2)

    private var activeNovelId: String? = null
    private var windowStart = 0
    private var windowEnd = -1
    private var currentChapter = 0
    private var memoryBytes = 0L

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

        try {
            buildCache(context, id)
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw e
        }

        return id
    }

    private val buildLock = Any()

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
        }

        JSONObject().put("chapters", indexJson)
            .write(File(dir, "index.json"))

        saveProgress(
            context,
            id,
            ReadingProgress(0, 0, chapters.firstOrNull()?.startByte ?: 0L, 0)
        )
    }

    private fun ensureChapter(context: Context, id: String, chapter: Int): File {
        val dir = folder(context, id)
        val file = File(dir, "chapters/%03d.json".format(chapter))

        synchronized(buildLock) {
            if (file.exists()) return file

            val meta = getChapters(context, id).first { it.number == chapter }
            val chapterBytes = ByteArray((meta.endByte - meta.startByte).toInt())
            java.io.RandomAccessFile(File(dir, "source.md"), "r").use {
                it.seek(meta.startByte)
                it.readFully(chapterBytes)
            }

            val document = NovelParser.parse(chapterBytes.toString(Charsets.UTF_8))
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

            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            JSONObject().put("blocks", blocksJson).write(tmp)
            tmp.renameTo(file)
        }
        return file
    }

    fun getLibrary(context: Context): List<NovelMetadata> {
        return root(context).listFiles()?.mapNotNull { dir ->
            val file = File(dir, "metadata.json")
            if (!file.exists()) return@mapNotNull null
            if (!File(dir, "index.json").exists()) return@mapNotNull null

            val json = JSONObject(file.readText())
            NovelMetadata(
                id = json.getString("id"),
                title = json.getString("title"),
                sourceUri = json.getString("sourceUri")
            )
        } ?: emptyList()
    }

    /** Remove a book and all of its on-disk chapter/progress caches. */
    fun deleteNovel(context: Context, id: String): Boolean {
        val removed = synchronized(buildLock) {
            File(root(context), id).deleteRecursively()
        }
        synchronized(cacheLock) {
            memoryCache.removeAll { it.novelId == id }
            memoryBytes = memoryCache.sumOf { entry -> entry.estimatedBytes }
            pendingLoads.removeAll { it.first == id }
            if (activeNovelId == id) {
                activeNovelId = null
                windowStart = 0
                windowEnd = -1
                currentChapter = 0
            }
        }
        val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(LAST_NOVEL_ID, null) == id) {
            prefs.edit().remove(LAST_NOVEL_ID).apply()
        }
        return removed
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
    synchronized(cacheLock) {
        memoryCache.firstOrNull {
            it.novelId == id && it.chapter == chapter
        }?.let {
            return it.blocks
        }

        if (activeNovelId != id) {
            memoryCache.clear()
            memoryBytes = 0L
            activeNovelId = id
            windowStart = chapter
            windowEnd = chapter
            currentChapter = chapter
        }
    }

    val file = ensureChapter(context, id, chapter)

    val blocks = JSONObject(file.readText()).getJSONArray("blocks")

    synchronized(cacheLock) {
        val existing = memoryCache.firstOrNull {
            it.novelId == id && it.chapter == chapter
        }

        if (existing != null) return existing.blocks

        if (activeNovelId == id &&
            chapter in windowStart..windowEnd
        ) {
            addToMemoryCache(id, chapter, blocks, file.length())
        }
    }

    return blocks
}
    private fun addToMemoryCache(
    id: String,
    chapter: Int,
    blocks: JSONArray,
    fileBytes: Long
) {
    val estimatedBytes = fileBytes * 3L

    if (estimatedBytes > MAX_MEMORY_BYTES) return

    memoryCache.removeAll {
        it.novelId == id && it.chapter == chapter
    }

    memoryBytes = memoryCache.sumOf { it.estimatedBytes }

    memoryCache.add(
        CachedChapter(id, chapter, blocks, estimatedBytes)
    )

    memoryBytes += estimatedBytes

    while (
        memoryCache.size > MAX_CACHE_CHAPTERS ||
        memoryBytes > MAX_MEMORY_BYTES
    ) {
        val farthest = memoryCache.maxByOrNull {
            kotlin.math.abs(it.chapter - currentChapter)
        } ?: break

        memoryCache.remove(farthest)
        memoryBytes -= farthest.estimatedBytes
    }
}

fun updateChapterWindow(
    context: Context,
    id: String,
    current: Int,
    chapterCount: Int,
    backward: Int = 1,
    forward: Int = 2
) {
    if (chapterCount <= 0) return

    val chapter = current.coerceIn(0, chapterCount - 1)

    synchronized(cacheLock) {
        if (activeNovelId != id) {
            memoryCache.clear()
            memoryBytes = 0L
            activeNovelId = id
            windowStart = maxOf(0, chapter - backward)
            windowEnd = minOf(chapterCount - 1, chapter + forward)
        } else if (
            chapter < windowStart ||
            chapter > windowEnd ||
            chapter < currentChapter
        ) {
            windowStart = maxOf(0, chapter - backward)
            windowEnd = minOf(chapterCount - 1, chapter + forward)
        } else {
            windowStart = maxOf(0, chapter - backward)

            if (chapter >= windowEnd - 2) {
                windowEnd = minOf(
                    chapterCount - 1,
                    chapter + forward
                )
            }
        }

        currentChapter = chapter

        val iterator = memoryCache.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()

            if (
                entry.novelId != id ||
                entry.chapter !in windowStart..windowEnd
            ) {
                memoryBytes -= entry.estimatedBytes
                iterator.remove()
            }
        }
    }

    val appContext = context.applicationContext

    for (target in windowStart..windowEnd) {
        val shouldLoad = synchronized(cacheLock) {
            val cached = memoryCache.any {
                it.novelId == id && it.chapter == target
            }

            val pending = id to target

            if (cached || pending in pendingLoads) {
                false
            } else {
                pendingLoads.add(pending)
                true
            }
        }

        if (!shouldLoad) continue

        prefetchExecutor.execute {
            try {
                val file = ensureChapter(appContext, id, target)

                run {
                    val blocks = JSONObject(file.readText())
                        .getJSONArray("blocks")

                    synchronized(cacheLock) {
                        if (
                            activeNovelId == id &&
                            target in windowStart..windowEnd
                        ) {
                            addToMemoryCache(
                                id,
                                target,
                                blocks,
                                file.length()
                            )
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                synchronized(cacheLock) {
                    pendingLoads.remove(id to target)
                }
            }
        }
    }
}

    fun getChapterWindowCache(
    context: Context,
    id: String,
    current: Int,
    chapterCount: Int,
    backward: Int = 1,
    forward: Int = 2
): List<Pair<Int, JSONArray>> {
    if (chapterCount <= 0) return emptyList()

    val chapter = current.coerceIn(0, chapterCount - 1)

    updateChapterWindow(
        context = context,
        id = id,
        current = chapter,
        chapterCount = chapterCount,
        backward = backward,
        forward = forward
    )

    val start = maxOf(0, chapter - backward)
    val end = minOf(chapterCount - 1, chapter + forward)

    return (start..end).map { target ->
        target to getChapterCache(context, id, target)
    }
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
            put("pageIndex", progress.pageIndex)
        }.write(File(folder(context, id), "progress.json"))
    }

    fun getProgress(context: Context, id: String): ReadingProgress {
        val file = File(folder(context, id), "progress.json")
        if (!file.exists()) return ReadingProgress(0, 0, 0L)

        val json = JSONObject(file.readText())
        return ReadingProgress(
            json.getInt("chapterNumber"),
            json.getInt("blockIndex"),
            json.getLong("byteOffset"),
            json.optInt("pageIndex", 0)
        )
    }

    private fun JSONObject.write(file: File) {
        file.writeText(toString(2))
    }
}
