package textforge.ui.components
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
