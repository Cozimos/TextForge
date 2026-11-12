package textforge.ui.components

@Composable
fun HeadingComponent(
    title: String,
    level: Int
) {

    val fontSize = when (level) {
        1 -> 28.sp
        2 -> 24.sp
        3 -> 21.sp
        else -> 19.sp
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        color = Color(0xFFDCEEFF),
        shape = RoundedCornerShape(10.dp)
    ) {

        Text(
            text = title,
            modifier = Modifier.padding(14.dp),
            color = Color(0xFF174B80),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            lineHeight = fontSize * 1.4
        )
    }
}
