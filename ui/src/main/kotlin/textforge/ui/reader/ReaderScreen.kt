
package textforge.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
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

@Composable
fun ReaderScreen(novelId: String) {
    val context = LocalContext.current

    val chapters = remember(novelId) {
        NovelStore.getChapters(context, novelId)
    }

    val progress = remember(novelId) {
        NovelStore.getProgress(context, novelId)
    }

    val chapterNumber = progress.chapterNumber.coerceIn(
        0, (chapters.size - 1).coerceAtLeast(0)
    )

    val blocks = remember(novelId, chapterNumber) {
        NovelStore.getChapterCache(context, novelId, chapterNumber)
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = progress.blockIndex.coerceIn(
            0, (blocks.length() - 1).coerceAtLeast(0)
        )
    )

    LaunchedEffect(listState, novelId, chapterNumber) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { blockIndex ->
                NovelStore.saveProgress(
                    context,
                    novelId,
                    ReadingProgress(
                        chapterNumber = chapterNumber,
                        blockIndex = blockIndex,
                        byteOffset = chapters[chapterNumber].startByte
                    )
                )
            }
    }
    
    @Composable
fun ReaderScreen(
    novelId: String,
    onBack: () -> Unit
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
    TextButton(onClick = onBack) {
        Text("← Library")
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.Modifier.weight(1f),
        contentPadding = PaddingValues(16.dp)
    ) {
        itemsIndexed(
            List(blocks.length()) { blocks.getJSONObject(it)}
        ) { _, block ->
            when (block.getString("type")) {
                "heading" -> Text(
                    text = block.getString("title"),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                "paragraph" -> Text(
                    text = block.getString("text"),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    novel: NovelItem,
    document: NovelDocument,
    onBack: () -> Unit
) {

    var showMenu by remember { mutableStateOf(false) }
    var showTOC by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }

    var searchText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }

    var currentChapter by remember { mutableIntStateOf(0) }

    val scrollState = rememberLazyListState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,

        topBar = {
            TopAppBar(
                title = {
                    Text(novel.title, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },

        bottomBar = {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp),
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

    ) { padding ->

        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = 22.dp,
                vertical = 24.dp
            )
        ) {

            itemsIndexed(document.blocks) { index, block ->

                when (block) {

                    is NovelBlock.Heading -> {

                        Surface(
                            color = Color(0xFFDCEEFF),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {

                            Text(
                                block.title,
                                modifier = Modifier.padding(14.dp),
                                color = Color(0xFF174B80),
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    is NovelBlock.Paragraph -> {

                        val text = buildAnnotatedString {

                            block.segments.forEach { segment ->

                                withStyle(
                                    SpanStyle(
                                        background = if (segment.highlighted)
                                            Color(0xFFDCEEFF)
                                        else Color.Transparent,

                                        fontStyle = if (segment.italic)
                                            FontStyle.Italic
                                        else FontStyle.Normal
                                    )
                                ) {
                                    append(segment.text)
                                }
                            }
                        }

                        if (block.fullHighlight) {

                            Surface(
                                color = Color(0xFFDCEEFF),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {

                                Text(
                                    text,
                                    modifier = Modifier.padding(14.dp),
                                    fontSize = 17.sp,
                                    lineHeight = 29.sp
                                )
                            }

                        } else {

                            Text(
                                text,
                                fontSize = 17.sp,
                                lineHeight = 29.sp,
                                modifier = Modifier.padding(vertical = 9.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Reader bottom sheet

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
                        onClick = {
                            currentChapter =
                                (currentChapter - 1).coerceAtLeast(0)
                        }
                    ) {
                        Text("< Previous")
                    }

                    OutlinedButton(
                        onClick = {
                            currentChapter =
                                (currentChapter + 1)
                                    .coerceAtMost(document.toc.lastIndex)
                        }
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

    // TOC dialog

    if (showTOC) {

        AlertDialog(
            onDismissRequest = { showTOC = false },
            title = { Text("Table of Contents") },
            text = {
                LazyColumn {
                    itemsIndexed(document.toc) { index, entry ->

                        TextButton(
                            onClick = {
                                currentChapter = index
                                showTOC = false
                            }
                        ) {
                            Text(entry.title)
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

    // Search dialog

    if (showSearch) {

        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("Search Novel") },
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

    // Replace dialog

    if (showReplace) {

        AlertDialog(
            containerColor = NovelColors.MenuBackground,
            onDismissRequest = { showReplace = false },
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
            confirmButton = {
                TextButton(onClick = { showReplace = false }) {
                    Text("Replace")
                }
            }
        )
    }
}
