package textforge.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InlineHighlight(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 17.sp,
    lineHeight: TextUnit = 18.sp,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    onTextPositioned: (LayoutCoordinates) -> Unit = {}
) {
    var layoutResult by remember {
        mutableStateOf<TextLayoutResult?>(null)
    }

    Text(
        text = text,
        modifier = modifier
            .onGloballyPositioned(onTextPositioned)
            .drawBehind {
                val layout = layoutResult ?: return@drawBehind

                val ranges = mutableListOf<IntRange>()

                text.spanStyles.forEach { range ->
                    if (range.item.background != Color.Transparent) {
                        ranges.add(range.start until range.end)
                    }
                }

                ranges.forEach { range ->
                    val boxes = range.mapNotNull { index ->
                        if (index >= text.length) return@mapNotNull null
                        layout.getBoundingBox(index)
                    }

                    val grouped = boxes.groupBy { it.top.toInt() }

                    grouped.values.forEach { lineBoxes ->
                        if (lineBoxes.isEmpty()) return@forEach

                        val left = lineBoxes.minOf { it.left } - 2.dp.toPx()
                        val right = lineBoxes.maxOf { it.right } + 2.dp.toPx()
                        val top = lineBoxes.minOf { it.top } - 3.dp.toPx()
                        val bottom = lineBoxes.maxOf { it.bottom } + 3.dp.toPx()

                        drawRoundRect(
                            color = Color(0xFFD5EEE9),
                            topLeft = Offset(left, top),
                            size = Size(
                                right - left,
                                bottom - top
                            ),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )
                    }
                }
            },
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = Color(0xFF252525),
        onTextLayout = {
            layoutResult = it
            onTextLayout(it)
        }
    )
}
