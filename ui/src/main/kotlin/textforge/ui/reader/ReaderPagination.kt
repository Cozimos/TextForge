package textforge.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.json.JSONArray

internal data class ReaderPageBlock(
    val text: AnnotatedString,
    val heading: Boolean,
    val fullHighlight: Boolean
)

@Composable
internal fun rememberReaderPages(
    blocks: JSONArray,
    widthPx: Int,
    heightPx: Int,
    density: Density,
    style: TextStyle
): List<List<ReaderPageBlock>> {

    val measurer = rememberTextMeasurer()

    return remember(blocks, widthPx, heightPx, density, style) {
        val pages = mutableListOf<List<ReaderPageBlock>>()
        var page = mutableListOf<ReaderPageBlock>()
        var usedHeight = 0

        val paragraphPadding = with(density) { 8.dp.roundToPx() }
        val headingPadding = with(density) { 12.dp.roundToPx() }

        fun finishPage() {
            if (page.isNotEmpty()) {
                pages.add(page)
                page = mutableListOf()
                usedHeight = 0
            }
        }

        for (i in 0 until blocks.length()) {
            val block = blocks.getJSONObject(i)
            val heading = block.getString("type") == "heading"
            val fullHighlight =
                !heading && block.optBoolean("fullHighlight", false)

            val text = if (heading) {
                AnnotatedString(block.getString("title"))
            } else {
                AnnotatedString.Builder().apply {
                    val segments = block.getJSONArray("segments")

                    for (j in 0 until segments.length()) {
                        val segment = segments.getJSONObject(j)
                        val highlighted = segment.optBoolean("highlighted")

                        pushStyle(
                            SpanStyle(
                                fontStyle = if (
                                    segment.optBoolean("italic")
                                ) {
                                    FontStyle.Italic
                                } else {
                                    FontStyle.Normal
                                },
                                background = if (
                                    highlighted && !fullHighlight
                                ) {
                                    Color(0xFFD5EEE9)
                                } else {
                                    Color.Transparent
                                }
                            )
                        )

                        append(segment.getString("text"))
                        pop()
                    }
                }.toAnnotatedString()
            }

            if (text.isEmpty()) continue

            val padding = if (heading) {
                headingPadding
            } else {
                paragraphPadding
            }

            var start = 0

            while (start < text.length) {
                val available = heightPx - usedHeight - padding * 2

                if (available <= 0 && page.isNotEmpty()) {
                    finishPage()
                    continue
                }

                val remaining = text.subSequence(start, text.length)

                val layout = measurer.measure(
                    text = remaining,
                    style = style,
                    constraints = Constraints(
                        maxWidth = widthPx.coerceAtLeast(1),
                        maxHeight = available.coerceAtLeast(1)
                    )
                )

                val end = if (layout.didOverflowHeight) {
                    layout.getLineEnd(
                        (layout.lineCount - 1).coerceAtLeast(0),
                        visibleEnd = true
                    ).coerceIn(1, remaining.length)
                } else {
                    remaining.length
                }

                page.add(
                    ReaderPageBlock(
                        text = remaining.subSequence(0, end),
                        heading = heading,
                        fullHighlight = fullHighlight
                    )
                )

                usedHeight += layout.size.height + padding * 2
                start += end

                if (start < text.length) {
                    finishPage()
                }
            }
        }

        finishPage()

        pages.ifEmpty {
            mutableListOf(mutableListOf())
        }
    }
}
