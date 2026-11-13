
package textforge.ui.reader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import kotlinx.coroutines.flow.map
import textforge.storage.NovelStore
import textforge.storage.ReadingProgress
import org.json.JSONArray
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import textforge.ui.library.NovelItem
import textforge.parser.NovelDocument
import textforge.parser.NovelBlock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.roundToPx
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ReaderScreen(
    novelId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val chapters = remember(novelId) { NovelStore.getChapters(context, novelId) }
    val progress = remember(novelId) { NovelStore.getProgress(context, novelId) }
    val lastChapter = (chapters.size - 1).coerceAtLeast(0)

    var currentChapter by remember(novelId) {
        mutableIntStateOf(progress.chapterNumber.coerceIn(0, lastChapter))
    }
    var currentPage by remember(novelId) {
    mutableIntStateOf(0)
}
    val window = (view.context as Activity).window

    WindowCompat.setDecorFitsSystemWindows(window, false)

    val controller = WindowInsetsControllerCompat(window, view)

    controller.hide(WindowInsetsCompat.Type.statusBars())
    controller.show(WindowInsetsCompat.Type.navigationBars())

    window.navigationBarColor = android.graphics.Color.TRANSPARENT

    controller.isAppearanceLightNavigationBars = false
    
    var showBars by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTOC by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val tocPageSize = 12
    
    var tocPage by remember(novelId) {
    mutableIntStateOf(0)
    }
    var showReplace by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }

    val blocks = remember(novelId, currentChapter) {
        NovelStore.getChapterCache(context, novelId, currentChapter)
    }
    val items = remember(blocks) { List(blocks.length()) { blocks.getJSONObject(it) } }

    val listState = remember(novelId, currentChapter) {
        LazyListState(
            if (restoring) progress.blockIndex.coerceIn(0, (blocks.length() - 1).coerceAtLeast(0)) else 0
        )
    }

    fun goTo(chapter: Int) {
    val target = chapter.coerceIn(0, lastChapter)

    if (target != currentChapter) {
        currentChapter = target
        currentPage = 0
        }
    }

    LaunchedEffect(listState, novelId, currentChapter) {
        NovelStore.updateChapterWindow(
            context = context,
            id = novelId,
            current = currentChapter,
            chapterCount = chapters.size
        )

        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { blockIndex ->
                NovelStore.saveProgress(
                    context,
                    novelId,
                    ReadingProgress(
                        chapterNumber = currentChapter,
                        blockIndex = blockIndex,
                        byteOffset = chapters[currentChapter].startByte
                    )
                )
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NovelColors.Background)
    ) {
        BoxWithConstraints(
    modifier = Modifier
        .fillMaxSize()
        .systemBarsPadding()
) {
    val density = LocalDensity.current
    val pageTextStyle = MaterialTheme.typography.bodyLarge

    val widthPx = with(density) {
        (maxWidth - 32.dp).roundToPx()
    }

    val heightPx = with(density) {
        (maxHeight - 32.dp).roundToPx()
    }

    val pages = rememberReaderPages(
        blocks = blocks,
        widthPx = widthPx.coerceAtLeast(1),
        heightPx = heightPx.coerceAtLeast(1),
        density = density,
        style = pageTextStyle
    )

    LaunchedEffect(currentChapter, pages.size) {
        currentPage = currentPage.coerceIn(
            0,
            pages.lastIndex.coerceAtLeast(0)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(currentChapter, currentPage, pages.size) {
                detectTapGestures { offset ->
                    when {
                        offset.x < size.width * 0.3f -> {
                            if (currentPage > 0) {
                                currentPage--
                            } else if (currentChapter > 0) {
                                goTo(currentChapter - 1)
                            }
                        }

                        offset.x > size.width * 0.7f -> {
                            if (currentPage < pages.lastIndex) {
                                currentPage++
                            } else if (currentChapter < lastChapter) {
                                goTo(currentChapter + 1)
                            }
                        }

                        else -> showBars = !showBars
                    }
                }
            }
            .padding(16.dp)
    ) {
        pages.getOrNull(currentPage)?.forEach { pageBlock ->

    when {
        pageBlock.heading -> {
            Text(
                text = pageBlock.text,
                style = pageTextStyle,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        pageBlock.fullHighlight -> {
            textforge.ui.components.HighlightedParagraph(
                text = pageBlock.text
            )
        }

        else -> {
            textforge.ui.components.InlineHighlight(
                text = pageBlock.text,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

        AnimatedVisibility(
            visible = showBars,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(color = NovelColors.MenuBackground, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.statusBarsPadding().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) { Text("← Library") }
                    Spacer(Modifier.weight(1f))
                    Text("${currentChapter + 1} / ${chapters.size}")
                }
            }
        }

        AnimatedVisibility(
            visible = showBars,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(color = NovelColors.MenuBackground, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FilledTonalButton(
                        onClick = { showMenu = true },
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Reader menu")
                        Spacer(Modifier.width(8.dp))
                        Text("Reader Menu")
                    }
                }
            }
        }
    }

    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            containerColor = NovelColors.MenuBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .navigationBarsPadding()
            ) {
                Text("Reader Tools", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { showMenu = false; showTOC = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Table of Contents") }

                OutlinedButton(
                    onClick = { showMenu = false; showSearch = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Search Word") }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(onClick = { goTo(currentChapter - 1) }) { Text("< Previous") }
                    OutlinedButton(onClick = { goTo(currentChapter + 1) }) { Text("Next >") }
                }

                OutlinedButton(
                    onClick = { showMenu = false; showReplace = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Find & Replace") }

                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showTOC) {
    AlertDialog(
        onDismissRequest = { showTOC = false },
        containerColor = NovelColors.MenuBackground,
        title = {
            Text("Table of Contents")
        },
        text = {
            Column {

                val tocPageCount =
                    (chapters.size + tocPageSize - 1) / tocPageSize

                val start = tocPage * tocPageSize
                val end = minOf(start + tocPageSize, chapters.size)

                LazyColumn(
                    modifier = Modifier.heightIn(max = 600.dp)
                ) {
                    itemsIndexed(chapters.subList(start, end)) { index, chapter ->
                        val chapterIndex = start + index

                        TextButton(
                            onClick = {
                                goTo(chapterIndex)
                                showTOC = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            contentPadding = PaddingValues(
                                horizontal = 8.dp,
                                vertical = 0.dp
                            )
                        ) {
                            Text(
                                chapter.title,
                                color = if (chapterIndex == currentChapter) {
                                    Color(0xFFFFCC80)
                                } else {
                                    Color.White
                                },
                                fontWeight = if (chapterIndex == currentChapter) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = tocPage > 0,
                        onClick = { tocPage-- }
                    ) {
                        Text("<")
                    }

                    Text(
                        "${tocPage + 1} / ${tocPageCount.coerceAtLeast(1)}"
                    )

                    TextButton(
                        enabled = tocPage < tocPageCount - 1,
                        onClick = { tocPage++ }
                    ) {
                        Text(">")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showTOC = false }) {
                Text("Close")
            }
        }
    )
}

    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            containerColor = NovelColors.MenuBackground,
            title = { Text("Search Novel") },
            text = {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Search word or phrase") },
                    singleLine = true
                )
            },
            confirmButton = { TextButton(onClick = { showSearch = false }) { Text("Search") } }
        )
    }

    if (showReplace) {
        AlertDialog(
            onDismissRequest = { showReplace = false },
            containerColor = NovelColors.MenuBackground,
            title = { Text("Find & Replace") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text("Find") }
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        label = { Text("Replace with") }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showReplace = false }) { Text("Replace") } }
        )
    }
}
