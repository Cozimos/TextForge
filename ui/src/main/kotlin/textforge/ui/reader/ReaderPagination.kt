package textforge.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

// ---------------------------------------------------------------------------
// Plain data (parsed once from JSON, off the main thread)
// ---------------------------------------------------------------------------

internal data class Segment(
    val text: String,
    val italic: Boolean,
    val highlighted: Boolean
)

internal data class ChapterBlock(
    val heading: Boolean,
    val title: String,
    val fullHighlight: Boolean,
    val segments: List<Segment>
)

internal fun JSONArray?.toBlocks(): List<ChapterBlock> {
    val arr = this ?: return emptyList()

    return List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        val heading = o.getString("type") == "heading"

        ChapterBlock(
            heading = heading,
            title = if (heading) o.getString("title") else "",
            fullHighlight = !heading && o.optBoolean("fullHighlight", false),
            segments = if (heading) {
                emptyList()
            } else {
                val s = o.getJSONArray("segments")
                List(s.length()) { j ->
                    val g = s.getJSONObject(j)
                    Segment(
                        text = g.getString("text"),
                        italic = g.optBoolean("italic"),
                        highlighted = g.optBoolean("highlighted")
                    )
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Page model + cache
// ---------------------------------------------------------------------------

internal data class ReaderPageBlock(
    val text: AnnotatedString,
    val heading: Boolean,
    val fullHighlight: Boolean
)

/** Everything that changes the layout. Chapter content is NOT part of it. */
internal data class ReaderLayoutKey(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val fontScale: Float,
    val style: TextStyle
)

internal class ReaderPaginationCache(private val maxEntries: Int = 8) {

    private class Entry(
        val key: ReaderLayoutKey,
        val pages: List<List<ReaderPageBlock>>
    )

    private val entries = LinkedHashMap<Int, Entry>(16, 0.75f, true)

    @Synchronized
    fun get(chapter: Int, key: ReaderLayoutKey): List<List<ReaderPageBlock>>? =
        entries[chapter]?.takeIf { it.key == key }?.pages

    @Synchronized
    fun put(chapter: Int, key: ReaderLayoutKey, pages: List<List<ReaderPageBlock>>) {
        entries[chapter] = Entry(key, pages)
        while (entries.size > maxEntries) {
            val eldest = entries.keys.iterator()
            eldest.next()
            eldest.remove()
        }
    }

    /** Call when a chapter's text changes (find & replace). */
    @Synchronized
    fun invalidate(chapter: Int) {
        entries.remove(chapter)
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}

// ---------------------------------------------------------------------------
// Composable entry point
// ---------------------------------------------------------------------------

@Composable
internal fun rememberReaderPages(
    chapter: Int,
    cache: ReaderPaginationCache,
    load: suspend () -> List<ChapterBlock>,
    key: ReaderLayoutKey,
    density: Density
): List<List<ReaderPageBlock>> {

    val measurer = rememberTextMeasurer()

    // Synchronous hit (prefetched): no disk, no recompute, no blank frame.
    val cached = remember(chapter, key) { cache.get(chapter, key) }

    var computed by remember(chapter, key) {
        mutableStateOf<List<List<ReaderPageBlock>>?>(null)
    }

    LaunchedEffect(chapter, key) {
        if (cached != null) return@LaunchedEffect

        cache.get(chapter, key)?.let {
            computed = it
            return@LaunchedEffect
        }

        val blocks = load()

        val result = withContext(Dispatchers.Default) {
            calculateReaderPages(blocks, key, density, measurer)
        }

        if (result.isNotEmpty()) cache.put(chapter, key, result)
        computed = result
    }

    return cached ?: computed ?: emptyList()
}

// ---------------------------------------------------------------------------
// Pagination
// ---------------------------------------------------------------------------

internal fun calculateReaderPages(
    blocks: List<ChapterBlock>,
    key: ReaderLayoutKey,
    density: Density,
    measurer: TextMeasurer
): List<List<ReaderPageBlock>> {

    val pages = mutableListOf<List<ReaderPageBlock>>()
    var page = mutableListOf<ReaderPageBlock>()
    var used = 0

    val widthPx = key.widthPx.coerceAtLeast(1)
    val pageHeight = key.heightPx
    val paragraphPad = with(density) { 8.dp.roundToPx() } * 2
    val headingPad = with(density) { 10.dp.roundToPx() } * 2

    fun finishPage() {
        if (page.isNotEmpty()) {
            pages.add(page)
            page = mutableListOf()
            used = 0
        }
    }

    for (block in blocks) {
        val heading = block.heading
        val fullHighlight = block.fullHighlight

        val text = if (heading) {
            AnnotatedString(block.title)
        } else {
            AnnotatedString.Builder().apply {
                for (segment in block.segments) {
                    pushStyle(
                        SpanStyle(
                            fontStyle = if (segment.italic) {
                                FontStyle.Italic
                            } else {
                                FontStyle.Normal
                            },
                            background = if (segment.highlighted && !fullHighlight) {
                                Color(0xFFD5EEE9)
                            } else {
                                Color.Transparent
                            }
                        )
                    )
                    append(segment.text)
                    pop()
                }
            }.toAnnotatedString()
        }

        if (text.isEmpty()) continue

        val pad = if (heading) headingPad else paragraphPad

        // Measure the whole block once, then cut it on line boundaries.
        val layout = measurer.measure(
            text = text,
            style = key.style,
            constraints = Constraints(maxWidth = widthPx)
        )
        val lineCount = layout.lineCount

        var line = 0
        while (line < lineCount) {
            val room = (pageHeight - used - pad).toFloat()
            val top = layout.getLineTop(line)

            var last = line - 1
            while (last + 1 < lineCount &&
                layout.getLineBottom(last + 1) - top <= room
            ) last++

            if (last < line) {
                if (page.isNotEmpty()) {
                    finishPage()
                    continue
                }
                last = line // empty page, still too small: force one line
            }

            page.add(
                ReaderPageBlock(
                    text = text.subSequence(
                        layout.getLineStart(line),
                        layout.getLineEnd(last)
                    ),
                    heading = heading,
                    fullHighlight = fullHighlight
                )
            )

            used += ceil(layout.getLineBottom(last) - top).toInt() + pad
            line = last + 1

            if (line < lineCount) finishPage()
        }
    }

    finishPage()
    return pages
}
