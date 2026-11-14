package textforge.ui.dictionary

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.abs

/** Native Kotlin, single-dictionary StarDict lookup with SDCV-inspired query modes. */
object StarDictManager {
    private const val PREFS = "stardict_preferences"
    private const val KEY_NAME = "dictionary_name"
    private const val CACHE_DIR = "stardict"

    data class Entry(val headword: String, val definition: String)
    data class LookupResult(
        val entry: Entry? = null,
        val matchedBy: MatchType = MatchType.NONE,
        val suggestions: List<String> = emptyList()
    )
    enum class MatchType { EXACT, SYNONYM, FUZZY, NONE }
    enum class SearchMode { HEADWORD, WILDCARD, FULL_TEXT }
    private data class IndexEntry(val word: String, val offset: Long, val size: Int)

    @Volatile private var loadedDirectory: File? = null
    @Volatile private var loadedName: String? = null
    @Volatile private var loadedEntries: List<IndexEntry>? = null
    @Volatile private var loadedByWord: Map<String, IndexEntry>? = null
    @Volatile private var synonyms: Map<String, String>? = null

    fun installedName(context: Context): String? {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, null) ?: return null
        val dir = File(context.filesDir, CACHE_DIR)
        return if (File(dir, "dictionary.idx").isFile && File(dir, "dictionary.dict").isFile) name else null
    }

    /** Imports one dictionary folder. Original source files are never modified. */
    fun installFromFolder(context: Context, treeUri: Uri): String {
        val folder = DocumentFile.fromTreeUri(context, treeUri) ?: error("The selected folder cannot be opened.")
        require(folder.isDirectory) { "Please select a dictionary folder." }
        val files = folder.listFiles().filter { it.isFile }
        val ifos = files.filter { it.name?.endsWith(".ifo", true) == true }
        require(ifos.size == 1) {
            if (ifos.isEmpty()) "No .ifo file was found. Select the folder containing the dictionary files."
            else "Select the subfolder for exactly one dictionary."
        }
        val ifo = ifos.single()
        val base = ifo.name!!.substringBeforeLast('.')
        val idx = files.firstOrNull { it.name.equals("$base.idx", true) }
            ?: files.firstOrNull { it.name.equals("$base.idx.gz", true) }
            ?: error("The matching .idx or .idx.gz file was not found.")
        val dict = files.firstOrNull { it.name.equals("$base.dict", true) }
            ?: files.firstOrNull { it.name.equals("$base.dict.dz", true) }
            ?: error("The matching .dict or .dict.dz file was not found.")

        val temp = File(context.filesDir, "${CACHE_DIR}_import").apply { deleteRecursively(); mkdirs() }
        copyDocument(context, idx, File(temp, "source.idx"))
        val idxTarget = File(temp, "dictionary.idx")
        if (idx.name!!.endsWith(".gz", true)) decompressGzip(File(temp, "source.idx"), idxTarget)
        else File(temp, "source.idx").copyTo(idxTarget, overwrite = true)
        File(temp, "source.idx").delete()

        val dictTarget = File(temp, "dictionary.dict")
        context.contentResolver.openInputStream(dict.uri)?.use { input ->
            val source = if (dict.name!!.endsWith(".dz", true)) GZIPInputStream(BufferedInputStream(input)) else input
            source.use { input -> FileOutputStream(dictTarget).use { output -> input.copyTo(output) } }
        } ?: error("Could not read the dictionary data file.")

        // .syn is optional. Its entries point to zero-based records in .idx.
        files.firstOrNull { it.name.equals("$base.syn", true) }?.let { syn ->
            copyDocument(context, syn, File(temp, "dictionary.syn"))
        }
        val displayName = readBookName(context, ifo) ?: base
        val parsed = parseIndex(idxTarget)
        require(parsed.isNotEmpty()) { "The dictionary index is empty or invalid." }
        val target = File(context.filesDir, CACHE_DIR)
        target.deleteRecursively()
        check(temp.renameTo(target)) { "Could not save the imported dictionary." }
        loadedDirectory = target
        loadedName = displayName
        loadedEntries = parsed
        loadedByWord = parsed.associateBy { normalize(it.word) }
        synonyms = parseSynonyms(File(target, "dictionary.syn"), parsed)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NAME, displayName).apply()
        return displayName
    }

    /** SDCV-like automatic lookup: exact, synonym, then fuzzy suggestions. */
    fun lookupSmart(context: Context, rawWord: String): LookupResult {
        val word = cleanWord(rawWord)
        if (word.isBlank()) return LookupResult()
        ensureLoaded(context)
        val key = normalize(word)
        val byWord = loadedByWord ?: return LookupResult()
        byWord[key]?.let { return LookupResult(readEntry(it), MatchType.EXACT) }
        val synonymTarget = synonyms?.get(key)
        if (synonymTarget != null) byWord[normalize(synonymTarget)]?.let {
            return LookupResult(readEntry(it), MatchType.SYNONYM)
        }
        val suggestions = fuzzySuggestions(word, loadedEntries.orEmpty())
        return LookupResult(matchedBy = if (suggestions.isEmpty()) MatchType.NONE else MatchType.FUZZY, suggestions = suggestions)
    }

    /** Backwards-compatible exact lookup used by existing call sites. */
    fun lookup(context: Context, rawWord: String): Entry? = lookupSmart(context, rawWord).entry

    /** Search dictionary headwords using exact substring or shell-style * and ? wildcards. */
    fun searchHeadwords(context: Context, query: String, wildcard: Boolean = false, limit: Int = 100): List<String> {
        ensureLoaded(context)
        val entries = loadedEntries.orEmpty()
        if (query.isBlank()) return emptyList()
        return if (wildcard) {
            val regex = wildcardRegex(query)
            entries.asSequence().filter { regex.matches(it.word) }.take(limit).map { it.word }.toList()
        } else {
            val q = normalize(query)
            entries.asSequence().filter { normalize(it.word).contains(q) }.take(limit).map { it.word }.toList()
        }
    }

    /** Optional SDCV-style full-text search. Bounded to avoid unbounded UI work. */
    fun searchFullText(context: Context, query: String, limit: Int = 50): List<Entry> {
        ensureLoaded(context)
        val q = normalize(query)
        if (q.isBlank()) return emptyList()
        val result = ArrayList<Entry>()
        for (item in loadedEntries.orEmpty()) {
            if (result.size >= limit) break
            val entry = readEntry(item) ?: continue
            if (normalize(entry.definition).contains(q)) result += entry
        }
        return result
    }

    private fun ensureLoaded(context: Context) {
        if (loadedEntries != null && loadedDirectory?.isDirectory == true) return
        synchronized(this) {
            if (loadedEntries != null && loadedDirectory?.isDirectory == true) return
            val dir = File(context.filesDir, CACHE_DIR)
            val idx = File(dir, "dictionary.idx")
            if (!idx.isFile || !File(dir, "dictionary.dict").isFile) return
            val parsed = parseIndex(idx)
            loadedDirectory = dir
            loadedName = installedName(context)
            loadedEntries = parsed
            loadedByWord = parsed.associateBy { normalize(it.word) }
            synonyms = parseSynonyms(File(dir, "dictionary.syn"), parsed)
        }
    }

    private fun parseIndex(file: File): List<IndexEntry> {
        val result = ArrayList<IndexEntry>()
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            while (true) {
                val bytes = ArrayList<Byte>()
                var b = input.read()
                if (b < 0) break
                while (b != 0) {
                    bytes.add(b.toByte())
                    b = input.read()
                    if (b < 0) return result
                }
                val offset = try { input.readInt().toLong() and 0xffffffffL } catch (_: Exception) { break }
                val size = try { input.readInt() } catch (_: Exception) { break }
                if (size < 0) break
                val word = ByteArray(bytes.size) { bytes[it] }.toString(Charsets.UTF_8)
                if (word.isNotBlank()) result += IndexEntry(word, offset, size)
            }
        }
        return result
    }

    private fun parseSynonyms(file: File, entries: List<IndexEntry>): Map<String, String> {
        if (!file.isFile) return emptyMap()
        val result = HashMap<String, String>()
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            while (true) {
                val bytes = ArrayList<Byte>()
                var b = input.read()
                if (b < 0) break
                while (b != 0) {
                    bytes += b.toByte()
                    b = input.read()
                    if (b < 0) return result
                }
                val indexNumber = try { input.readInt() } catch (_: Exception) { break }
                if (indexNumber in entries.indices) {
                    val synonym = ByteArray(bytes.size) { bytes[it] }.toString(Charsets.UTF_8)
                    result[normalize(synonym)] = entries[indexNumber].word
                }
            }
        }
        return result
    }

    private fun readEntry(item: IndexEntry): Entry? {
        val dict = File(loadedDirectory ?: return null, "dictionary.dict")
        if (!dict.isFile || item.offset < 0 || item.size < 0 || item.offset + item.size > dict.length()) return null
        val bytes = ByteArray(item.size)
        RandomAccessFile(dict, "r").use { file -> file.seek(item.offset); file.readFully(bytes) }
        val definition = bytes.toString(Charsets.UTF_8).trim()
        return if (definition.isEmpty()) null else Entry(item.word, definition)
    }

    private fun fuzzySuggestions(word: String, entries: List<IndexEntry>): List<String> {
        val target = normalize(word)
        if (target.length > 40) return emptyList()
        val maxDistance = when { target.length <= 4 -> 1; target.length <= 9 -> 2; else -> 3 }
        return entries.asSequence().map { it.word to levenshtein(target, normalize(it.word), maxDistance) }
            .filter { it.second in 1..maxDistance }
            .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { abs(it.first.length - word.length) })
            .take(8).map { it.first }.toList()
    }

    private fun levenshtein(a: String, b: String, cutoff: Int): Int {
        if (abs(a.length - b.length) > cutoff) return cutoff + 1
        var prev = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val cur = IntArray(b.length + 1)
            cur[0] = i + 1
            var rowMin = cur[0]
            for (j in b.indices) {
                cur[j + 1] = minOf(cur[j] + 1, prev[j + 1] + 1, prev[j] + if (a[i] == b[j]) 0 else 1)
                rowMin = minOf(rowMin, cur[j + 1])
            }
            if (rowMin > cutoff) return cutoff + 1
            prev = cur
        }
        return prev[b.length]
    }

    private fun wildcardRegex(pattern: String): Regex {
        val escaped = buildString {
            pattern.forEach { c -> append(when (c) { '*' -> ".*"; '?' -> "."; else -> Regex.escape(c.toString()) }) }
        }
        return Regex("^$escaped$", RegexOption.IGNORE_CASE)
    }

    private fun cleanWord(value: String) = value.trim().trim { !it.isLetterOrDigit() && it != '-' && it != '\'' }
    private fun normalize(value: String) = value.lowercase(Locale.ROOT)

    private fun copyDocument(context: Context, source: DocumentFile, target: File) {
        context.contentResolver.openInputStream(source.uri)?.use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
            ?: error("Could not read ${source.name}.")
    }

    private fun decompressGzip(source: File, target: File) {
        GZIPInputStream(BufferedInputStream(FileInputStream(source))).use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
    }

    private fun readBookName(context: Context, ifo: DocumentFile): String? {
        val text = context.contentResolver.openInputStream(ifo.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return null
        return text.lineSequence().firstOrNull { it.startsWith("bookname=") }?.substringAfter('=')?.trim()?.takeIf { it.isNotEmpty() }
    }
}
