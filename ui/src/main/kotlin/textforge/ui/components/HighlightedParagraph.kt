package textforge.ui.components

@Composable
fun HighlightedParagraph(
    text: AnnotatedString,
    modifier: Modifier = Modifier
) {

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        color = Color(0xFFDCEEFF),
        shape = RoundedCornerShape(12.dp)
    ) {

        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            color = Color(0xFF252525),
            fontSize = 17.sp,
            lineHeight = 29.sp
        )
    }
}
