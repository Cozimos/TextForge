package textforge.ui.components
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned


@Composable
fun HighlightedParagraph(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    onTextPositioned: (LayoutCoordinates) -> Unit = {}
) {

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        color = Color(0xFFD5EEE9),
        shape = RoundedCornerShape(3.dp)
    ) {

    Text(
        text = text,
        modifier = Modifier
            .padding(4.dp)
            .onGloballyPositioned(onTextPositioned),
        color = Color(0xFF252525),
        fontSize = 17.sp,
        lineHeight = 18.sp,
        onTextLayout = onTextLayout
        )
    }
}
