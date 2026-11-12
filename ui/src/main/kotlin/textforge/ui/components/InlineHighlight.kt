package textforge.ui.components

@Composable
fun InlineHighlight(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 17.sp,
    lineHeight: TextUnit = 29.sp
) {

    var layoutResult by remember {
        mutableStateOf<TextLayoutResult?>(null)
    }

    Text(
        text = text,
        modifier = modifier.drawBehind {

            val layout = layoutResult ?: return@drawBehind

            val ranges = mutableListOf<IntRange>()

            var start: Int? = null

            text.spanStyles.forEach { range ->

                if (
                    range.item.background != Color.Transparent
                ) {
                    ranges.add(range.start until range.end)
                }
            }

            ranges.forEach { range ->

                val boxes = range.mapNotNull { index ->

                    if (index >= text.length) return@mapNotNull null

                    layout.getBoundingBox(index)
                }

                val grouped = boxes.groupBy {
                    it.top.toInt()
                }

                grouped.values.forEach { lineBoxes ->

                    if (lineBoxes.isEmpty()) return@forEach

                    val left = lineBoxes.minOf { it.left } - 2.dp.toPx()
                    val right = lineBoxes.maxOf { it.right } + 2.dp.toPx()
                    val top = lineBoxes.minOf { it.top } - 3.dp.toPx()
                    val bottom = lineBoxes.maxOf { it.bottom } + 3.dp.toPx()

                    drawRoundRect(
                        color = Color(0xFFDCEEFF),
                        topLeft = Offset(left, top),
                        size = Size(
                            right - left,
                            bottom - top
                        ),
                        cornerRadius = CornerRadius(5.dp.toPx())
                    )
                }
            }
        },
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = Color(0xFF252525),
        onTextLayout = {
            layoutResult = it
        }
    )
}
