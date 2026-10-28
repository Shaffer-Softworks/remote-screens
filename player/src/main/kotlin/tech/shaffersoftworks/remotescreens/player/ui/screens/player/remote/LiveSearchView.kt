package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import tech.shaffersoftworks.remotescreens.player.ui.theme.teal_200
import tech.shaffersoftworks.remotescreens.shared.MusicSearchRow
import kotlin.time.Duration.Companion.milliseconds

private val Teal = Color(0xFF14E1BA)
private val Ink = Color.White
private val RowBg = Color(0xFF333333)
private const val SEARCH_DEBOUNCE_MS = 400L
private const val PREVIEW_QUERY = "daft punk"

private val PREVIEW_SEARCH_ROWS = listOf(
    MusicSearchRow(
        title = "Digital Love",
        subtitle = "Daft Punk",
        uri = "library://track/digital-love",
        imagePathOrUrl = "https://picsum.photos/id/203/200.jpg",
    ),
    MusicSearchRow(
        title = "One More Time",
        uri = "library://track/one-more-time",
        imagePathOrUrl = "https://picsum.photos/id/203/200.jpg",
    ),
)

/** Search state belongs to the mounted document overlay. */
@Composable
internal fun LiveSearchView(
    search: SlotSearch,
    onQueryChange: (String, String) -> Unit,
    onPlayUri: (String, String) -> Unit,
    onEnqueueUri: (String, String) -> Unit,
    imageLoader: ImageLoader,
) {
    var loading by remember(search.tileId, search.revision, search.rows) { mutableStateOf(false) }
    SearchContent(
        search = search,
        rows = search.rows,
        isLoading = loading,
        onQuery = { query ->
            loading = query.isNotBlank()
            onQueryChange(search.tileId, query)
        },
        onPlay = { onPlayUri(search.tileId, it) },
        onEnqueue = { onEnqueueUri(search.tileId, it) },
        imageLoader = imageLoader,
    )
}

@Composable
private fun SearchContent(
    search: SlotSearch,
    rows: List<MusicSearchRow>,
    isLoading: Boolean,
    onQuery: (String) -> Unit,
    onPlay: (String) -> Unit,
    onEnqueue: (String) -> Unit,
    initialText: String = "",
    imageLoader: ImageLoader,
) {
    var text by remember(search.tileId, search.revision) { mutableStateOf(initialText) }
    val currentOnQuery by rememberUpdatedState(onQuery)
    LaunchedEffect(text, search.tileId, search.revision) {
        if (text.isBlank()) {
            currentOnQuery("")
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS.milliseconds)
        currentOnQuery(text)
    }
    val showResults = text.isNotBlank() || rows.isNotEmpty()
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search library") },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    TextButton(onClick = { text = "" }, enabled = text.isNotBlank()) {
                        Text(
                            "×",
                            color = Ink.copy(alpha = if (text.isNotBlank()) 0.9f else 0.35f),
                            fontSize = 22.sp,
                        )
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                focusedBorderColor = Teal,
                unfocusedBorderColor = Ink.copy(alpha = 0.5f),
                cursorColor = Ink,
                focusedLabelColor = Ink,
                unfocusedLabelColor = Ink.copy(alpha = 0.7f),
            ),
        )
        Spacer(Modifier.height(10.dp))
        if (showResults) {
            Text(
                "Results",
                color = Ink.copy(alpha = 0.95f),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        when {
            isLoading && text.isNotBlank() -> {
                Row(
                    Modifier.fillMaxWidth().height(120.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = Teal)
                }
            }
            text.isNotBlank() && rows.isEmpty() && !isLoading -> {
                Text("No results", color = Ink.copy(alpha = 0.8f), fontSize = 14.sp)
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(rows, key = { it.uri + it.title }) { row ->
                        SearchRow(
                            musicSearchRow = row,
                            onPlay = { onPlay(row.uri) },
                            onEnqueue = { onEnqueue(row.uri) },
                            imageLoader = imageLoader,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(
    musicSearchRow: MusicSearchRow,
    onPlay: () -> Unit,
    onEnqueue: () -> Unit,
    imageLoader: ImageLoader,
) {
    val context = LocalContext.current
    val thumbShape = RoundedCornerShape(8.dp)
    val iconBg = teal_200.copy(alpha = 0.32f)// else 0.18f)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RowBg.copy(alpha = 0.9f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(onClick = onPlay)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(thumbShape)
                    .background(
                        if (musicSearchRow.imagePathOrUrl != null) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        } else {
                            iconBg
                        },
                        thumbShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                musicSearchRow.imagePathOrUrl?.let {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(it)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    musicSearchRow.title,
                    color = Ink,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                musicSearchRow.subtitle?.let {
                    Text(
                        it,
                        color = Ink.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        var menuOpen by remember(musicSearchRow.uri, musicSearchRow.title) { mutableStateOf(false) }
        Box {
            TextButton(onClick = { menuOpen = true }) {
                Text("⋮", color = Ink.copy(alpha = 0.75f), fontSize = 18.sp)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    onClick = {
                        menuOpen = false
                        onEnqueue()
                    },
                )
            }
        }
    }
}

@Preview(name = "Empty", widthDp = 400, heightDp = 780)
@Composable
private fun SearchContentEmptyPreview() {
    val previewLoader = ImageLoader.Builder(LocalContext.current).build()

    RemoteScreensTheme {
        SearchPreviewBackdrop {
            SearchContent(
                search = previewSlotSearch(),
                rows = emptyList(),
                isLoading = false,
                onQuery = {},
                onPlay = {},
                onEnqueue = {},
                imageLoader = previewLoader,
            )
        }
    }
}

@Preview(name = "Loading", widthDp = 400, heightDp = 780)
@Composable
private fun SearchContentLoadingPreview() {
    val previewLoader = ImageLoader.Builder(LocalContext.current).build()

    RemoteScreensTheme {
        SearchPreviewBackdrop {
            SearchContent(
                search = previewSlotSearch(),
                rows = emptyList(),
                isLoading = true,
                onQuery = {},
                onPlay = {},
                onEnqueue = {},
                initialText = PREVIEW_QUERY,
                imageLoader = previewLoader,
            )
        }
    }
}

@Preview(name = "No results", widthDp = 400, heightDp = 780)
@Composable
private fun SearchContentNoResultsPreview() {
    val previewLoader = ImageLoader.Builder(LocalContext.current).build()

    RemoteScreensTheme {
        SearchPreviewBackdrop {
            SearchContent(
                search = previewSlotSearch(),
                rows = emptyList(),
                isLoading = false,
                onQuery = {},
                onPlay = {},
                onEnqueue = {},
                initialText = PREVIEW_QUERY,
                imageLoader = previewLoader,
            )
        }
    }
}

@Preview(name = "Results", widthDp = 400, heightDp = 780)
@Composable
private fun SearchContentResultsPreview() {
    val previewLoader = ImageLoader.Builder(LocalContext.current).build()

    RemoteScreensTheme {
        SearchPreviewBackdrop {
            SearchContent(
                search = previewSlotSearch(),
                rows = PREVIEW_SEARCH_ROWS,
                isLoading = false,
                onQuery = {},
                onPlay = {},
                onEnqueue = {},
                initialText = PREVIEW_QUERY,
                imageLoader = previewLoader,
            )
        }
    }
}

@Preview(name = "Row", widthDp = 400, heightDp = 780)
@Composable
private fun SearchRowPreview() {
    val previewLoader = ImageLoader.Builder(LocalContext.current).build()

    RemoteScreensTheme {
        SearchPreviewBackdrop {
            SearchRow(
                musicSearchRow = PREVIEW_SEARCH_ROWS.first(),
                onPlay = {},
                onEnqueue = {},
                imageLoader = previewLoader,
            )
        }
    }
}

@Preview(name = "Row, title only", widthDp = 400, heightDp = 780)
@Composable
private fun SearchRowTitleOnlyPreview() {
    val previewLoader = ImageLoader.Builder(LocalContext.current).build()

    RemoteScreensTheme {
        SearchPreviewBackdrop {
            SearchRow(
                musicSearchRow = PREVIEW_SEARCH_ROWS.last(),
                onPlay = {},
                onEnqueue = {},
                imageLoader = previewLoader,
            )
        }
    }
}

@Preview(name = "Backdrop", widthDp = 400, heightDp = 780)
@Composable
private fun SearchPreviewBackdropPreview() {
    RemoteScreensTheme {
        SearchPreviewBackdrop {
            Text(text = "Search", color = Ink)
        }
    }
}

/** Dark plate so white search ink is visible. The live overlay sits on the document, which supplies this. */
@Composable
private fun SearchPreviewBackdrop(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(RowBg),
    ) {
        content()
    }
}

private fun previewSlotSearch(): SlotSearch = SlotSearch(
    revision = 1,
    componentId = 1,
    tileId = "music-search",
    playerEntityId = "media_player.kitchen",
)

@Preview(name = "Search overlay", widthDp = 800, heightDp = 480)
@Composable
private fun LiveSearchViewPreview() {
    val previewLoader = ImageLoader.Builder(LocalContext.current).build()

    RemoteScreensTheme {
        SearchPreviewBackdrop {
            LiveSearchView(
                previewSlotSearch(),
                { _, _ -> },
                { _, _ -> },
                { _, _ -> },
                imageLoader = previewLoader,
            )
        }
    }
}
