package textforge.ui.reader

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import textforge.storage.NovelStore
import textforge.ui.NovelColors
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.layout.LayoutCoordinates
import org.json.JSONArray
import textforge.parser.ChapterIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.rememberTextMeasurer

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ReaderScreen(
    novelId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity

    // Immersive reader: hide status bar, keep navigation bar.
    // Immersive reader: hide system bars.
DisposableEffect(activity, view) {
    val window = activity?.window

    if (window != null) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, view)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    onDispose {
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, view)
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }
}
    var chapters by remember(novelId) {
    mutableStateOf<List<ChapterIndex>>(emptyList())
    }

    LaunchedEffect(novelId) {
    chapters = withContext(Dispatchers.IO) {
        NovelStore.getChapters(context, novelId)
        }
    }

    val lastChapter = (chapters.size - 1).coerceAtLeast(0)

    var currentChapter by remember(novelId) {
    mutableIntStateOf(0)
    }

    LaunchedEffect(novelId, chapters) {
    if (chapters.isNotEmpty()) {
    val progress = withContext(Dispatchers.IO) {
            NovelStore.getProgress(context, novelId)
        }

        currentChapter = progress.chapterNumber.coerceIn(
            0,
            chapters.lastIndex
            )
        }
    }

    var currentPage by remember(novelId) {
        mutableIntStateOf(0)
    }

    var showBars by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTOC by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }
    var selectedWord by remember { mutableStateOf<String?>(null) }

    val textLayouts = remember(currentChapter, currentPage) {
    mutableStateMapOf<Int, TextLayoutResult>()
    }

    val textCoordinates = remember(currentChapter, currentPage) {
    mutableStateMapOf<Int, LayoutCoordinates>()
    }

    var readerCoordinates by remember {
    mutableStateOf<LayoutCoordinates?>(null)
    }
    var searchText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }

    val tocPageSize = 11

    var tocPage by remember(novelId) {
        mutableIntStateOf(0)
    }

    var blocks by remember(novelId) {
    mutableStateOf<JSONArray?>(null)
    }

    LaunchedEffect(novelId, currentChapter) {
    blocks = withContext(Dispatchers.IO) {
        NovelStore.getChapterCache(context, novelId, currentChapter)
        }
    }

    val paginationCache = remember(novelId) {
    ReaderPaginationCache()
}

    val chapterPages = remember(novelId) {
    mutableStateMapOf<Int, Int>()
}

    fun goTo(chapter: Int) {
    val target = chapter.coerceIn(0, lastChapter)

    if (target != currentChapter) {
        chapterPages[currentChapter] = currentPage
        currentChapter = target
        currentPage = chapterPages[target] ?: 0
        }
    }

    LaunchedEffect(novelId, currentChapter) {
        NovelStore.updateChapterWindow(
            context = context,
            id = novelId,
            current = currentChapter,
            chapterCount = chapters.size
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NovelColors.Background)
    ) {

        // READER PAGE AREA
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val density = LocalDensity.current
            val pageTextStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp,lineHeight = 18.sp
            )

            val widthPx = with(density) {
                (maxWidth - 33.dp).roundToPx()
            }

            val heightPx = with(density) {
                (maxHeight - 30.dp).roundToPx()
            }
            val prefetchMeasurer = rememberTextMeasurer()

            LaunchedEffect(
            novelId,
            currentChapter,
            chapters.size,
            widthPx,
            heightPx,
            density.density,
            density.fontScale,
            pageTextStyle
            ) {
            val window = withContext(Dispatchers.IO) {
            NovelStore.getChapterWindowCache(
            context = context,
            id = novelId,
            current = currentChapter,
            chapterCount = chapters.size
        )
    }

    withContext(Dispatchers.Default) {
        window.forEach { (chapter, chapterBlocks) ->

            if (chapter == currentChapter) return@forEach

            val cached = paginationCache.get(
                chapter,
                chapterBlocks,
                widthPx.coerceAtLeast(1),
                heightPx.coerceAtLeast(1),
                density,
                pageTextStyle
            )

            if (cached == null) {
                calculateReaderPages(
                    chapter = chapter,
                    cache = paginationCache,
                    blocks = chapterBlocks,
                    widthPx = widthPx.coerceAtLeast(1),
                    heightPx = heightPx.coerceAtLeast(1),
                    density = density,
                    style = pageTextStyle,
                    measurer = prefetchMeasurer
                )
            }
        }
    }
}

            val pages = rememberReaderPages(

                chapter = currentChapter,
                cache = paginationCache,

                blocks = blocks ?: JSONArray(),
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
        .onGloballyPositioned {
            readerCoordinates = it
        }
        .pointerInput(currentChapter, currentPage, pages.size) {
            detectTapGestures(
                onTap = { offset ->
                    when {
                        offset.x < size.width * 0.35f -> {
                            if (currentPage > 0) {
                                currentPage--
                            } else if (currentChapter > 0) {
                                goTo(currentChapter - 1)
                            }
                        }

                        offset.x > size.width * 0.75f -> {
                            if (currentPage < pages.lastIndex) {
                                currentPage++
                            } else if (currentChapter < lastChapter) {
                                goTo(currentChapter + 1)
                            }
                        }

                        else -> showBars = !showBars
                    }
                },
                onLongPress = { offset ->
                    val parent = readerCoordinates ?: return@detectTapGestures
                    val pageBlocks = pages.getOrNull(currentPage)
                        ?: return@detectTapGestures

                    selectedWord = null

                    pageBlocks.forEachIndexed { index, block ->
                        val coordinates = textCoordinates[index]
                            ?: return@forEachIndexed

                        val layout = textLayouts[index]
                            ?: return@forEachIndexed

                        if (block.text.isEmpty()) return@forEachIndexed

                        val localOffset = coordinates.localPositionOf(
                            parent,
                            offset
                        )

                        if (
                            localOffset.x < 0f ||
                            localOffset.y < 0f ||
                            localOffset.x > coordinates.size.width.toFloat() ||
                            localOffset.y > coordinates.size.height.toFloat()
                        ) return@forEachIndexed

                        val characterOffset = layout
                            .getOffsetForPosition(localOffset)
                            .coerceIn(0, block.text.length - 1)

                        val wordRange = layout.getWordBoundary(characterOffset)

                        val word = block.text.text
                            .substring(wordRange.start, wordRange.end)
                            .trim()

                        if (word.isNotEmpty()) {
                            selectedWord = word
                            return@forEachIndexed
                        }
                    }
                }
            )
        }
        .padding(10.dp)
) {
    pages.getOrNull(currentPage)?.forEachIndexed { index, pageBlock ->
        val onLayout: (TextLayoutResult) -> Unit = {
            textLayouts[index] = it
        }

        val onPositioned: (LayoutCoordinates) -> Unit = {
            textCoordinates[index] = it
        }

        when {
            pageBlock.heading -> {
                Text(
                    text = pageBlock.text,
                    style = pageTextStyle,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .onGloballyPositioned(onPositioned),
                    onTextLayout = onLayout
                )
            }

            pageBlock.fullHighlight -> {
                textforge.ui.components.HighlightedParagraph(
                    text = pageBlock.text,
                    onTextLayout = onLayout,
                    onTextPositioned = onPositioned
                )
            }

            else -> {
                textforge.ui.components.InlineHighlight(
                    text = pageBlock.text,
                    modifier = Modifier.padding(vertical = 8.dp),
                    onTextLayout = onLayout,
                    onTextPositioned = onPositioned
                )
            }
        }
    }
}
        selectedWord?.let { word ->
    AlertDialog(
        onDismissRequest = {
            selectedWord = null
            },
        title = {
            Text(word)
            },
        text = {
            Text("Dictionary lookup")
            },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedWord = null
                    }
            ) {
                Text("Close")
            }
        }
    )
}
    

        // TOP READER BAR
        AnimatedVisibility(
            visible = showBars,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = NovelColors.MenuBackground,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) {
                        Text("← Library")
                    }

                    Spacer(Modifier.weight(1f))

                    Text("${currentChapter + 1} / ${chapters.size}")
                }
            }
        }

        // BOTTOM READER BAR
        AnimatedVisibility(
            visible = showBars,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = NovelColors.MenuBackground,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FilledTonalButton(
                        onClick = { showMenu = true },
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Reader menu"
                        )

                        Spacer(Modifier.width(8.dp))

                        Text("Reader Menu")
                    }
                }
            }
        }
    }

    // READER MENU
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
                Text(
                    "Reader Tools",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        showMenu = false
                        showTOC = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Table of Contents")
                }

                OutlinedButton(
                    onClick = {
                        showMenu = false
                        showSearch = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Search Word")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { goTo(currentChapter - 1) }
                    ) {
                        Text("< Previous")
                    }

                    OutlinedButton(
                        onClick = { goTo(currentChapter + 1) }
                    ) {
                        Text("Next >")
                    }
                }

                OutlinedButton(
                    onClick = {
                        showMenu = false
                        showReplace = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Find & Replace")
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }

        // TABLE OF CONTENTS
    if (showTOC) {
        Dialog(
            onDismissRequest = { showTOC = false }
        ) {
            Surface(
                color = NovelColors.MenuBackground,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                ) {
                    Text(
                        "Table of Contents",
                        style = MaterialTheme.typography.titleLarge
                    )

                    val tocPageCount =
                        (chapters.size + tocPageSize - 1) / tocPageSize

                    val start = tocPage * tocPageSize
                    val end = minOf(start + tocPageSize, chapters.size)

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 800.dp)
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
                                        Color.Black
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            enabled = tocPage > 0,
                            onClick = { tocPage-- },
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Text("<")
                        }

                        Text(
                            "${tocPage + 1} / ${tocPageCount.coerceAtLeast(1)}"
                        )

                        TextButton(
                            enabled = tocPage < tocPageCount - 1,
                            onClick = { tocPage++ },
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Text(">")
                        }
                    }

                    TextButton(
                        onClick = { showTOC = false },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(
                            horizontal = 8.dp,
                            vertical = 0.dp
                        )
                    ) {
                    Text("Close")
                    }
                }
            }
        }
    }

    // SEARCH
    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            containerColor = NovelColors.MenuBackground,
            title = {
                Text("Search Novel")
            },
            text = {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Search word or phrase") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { showSearch = false }) {
                    Text("Search")
                }
            }
        )
    }

    // FIND AND REPLACE
    if (showReplace) {
        AlertDialog(
            onDismissRequest = { showReplace = false },
            containerColor = NovelColors.MenuBackground,
            title = {
                Text("Find & Replace")
            },
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
            confirmButton = {
                TextButton(onClick = { showReplace = false }) {
                    Text("Replace")
                    }
                }
            )
        }
    }
}
