package textforge.ui.components

@Composable
fun NovelTextRenderer(
    block: NovelBlock,
    modifier: Modifier = Modifier
) {

    when (block) {

        is NovelBlock.Heading -> {

            HeadingComponent(
                title = block.title,
                level = block.level
            )
        }

        is NovelBlock.Paragraph -> {

            val annotatedText = buildAnnotatedString {

                block.segments.forEach { segment ->

                    val start = length

                    append(segment.text)

                    val end = length

                    if (segment.highlighted || segment.italic) {

                        addStyle(
                            SpanStyle(
                                background = if (
                                    segment.highlighted &&
                                    !block.fullHighlight
                                ) Color(0xFFDCEEFF)
                                else Color.Transparent,

                                fontStyle = if (segment.italic)
                                    FontStyle.Italic
                                else FontStyle.Normal
                            ),
                            start,
                            end
                        )
                    }
                }
            }

            if (block.fullHighlight) {

                HighlightedParagraph(
                    text = annotatedText,
                    modifier = modifier
                )

            } else {

                InlineHighlight(
                    text = annotatedText,
                    modifier = modifier.padding(vertical = 9.dp)
                )
            }
        }
    }
}
