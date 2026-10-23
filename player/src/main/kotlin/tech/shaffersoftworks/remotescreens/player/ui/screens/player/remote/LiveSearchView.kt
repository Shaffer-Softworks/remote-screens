package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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
    ),
    MusicSearchRow(
        title = "One More Time",
        uri = "library://track/one-more-time",
    ),
)

/**
 * Native Music Assistant search over a document component: TextField + results.
 * Consumes touches so the Remote Compose document underneath does not receive them.
 */
@SuppressLint("ViewConstructor")
class LiveSearchView(context: Context) : FrameLayout(context) {
    private val composeView = ComposeView(context)

    var search: SlotSearch? = null
        private set

    var onQueryChange: (tileId: String, query: String) -> Unit = { _, _ -> }
    var onPlayUri: (tileId: String, uri: String) -> Unit = { _, _ -> }
    var onEnqueueUri: (tileId: String, uri: String) -> Unit = { _, _ -> }

    private var rows: List<MusicSearchRow> = emptyList()
    private var isLoading: Boolean = false

    init {
        isClickable = true
        isFocusable = true
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun show(search: SlotSearch) {
        this.search = search
        rows = search.rows
        render()
        visibility = VISIBLE
    }

    fun setResults(tileId: String, nextRows: List<MusicSearchRow>) {
        if (search?.tileId != tileId) return
        rows = nextRows
        isLoading = false
        render()
    }

    fun markLoading(tileId: String) {
        if (search?.tileId != tileId) return
        isLoading = true
        render()
    }

    fun stop() {
        search = null
        rows = emptyList()
        isLoading = false
        composeView.setContent { }
        visibility = GONE
    }

    private fun render() {
        val active = search ?: return
        val currentRows = rows
        val loading = isLoading
        composeView.setContent {
            SearchContent(
                search = active,
                rows = currentRows,
                isLoading = loading,
                onQuery = { query ->
                    if (query.isNotBlank()) {
                        isLoading = true
                    } else {
                        isLoading = false
                        rows = emptyList()
                    }
                    onQueryChange(active.tileId, query)
                },
                onPlay = { uri -> onPlayUri(active.tileId, uri) },
                onEnqueue = { uri -> onEnqueueUri(active.tileId, uri) },
            )
        }
    }
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
) {
    var text by remember(search.tileId, search.revision) { mutableStateOf(initialText) }
    LaunchedEffect(text) {
        if (text.isBlank()) {
            onQuery("")
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS.milliseconds)
        onQuery(text)
    }
    val showResults = text.isNotBlank() || rows.isNotEmpty()
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 0.dp),
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
                        SearchRow(row, onPlay = { onPlay(row.uri) }, onEnqueue = { onEnqueue(row.uri) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(
    row: MusicSearchRow,
    onPlay: () -> Unit,
    onEnqueue: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RowBg.copy(alpha = 0.9f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onPlay),
        ) {
            Text(
                row.title,
                color = Ink,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.subtitle?.let {
                Text(
                    it,
                    color = Ink.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        var menuOpen by remember(row.uri, row.title) { mutableStateOf(false) }
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
    SearchPreviewBackdrop {
        SearchContent(
            search = previewSlotSearch(),
            rows = emptyList(),
            isLoading = false,
            onQuery = {},
            onPlay = {},
            onEnqueue = {},
        )
    }
}

@Preview(name = "Loading", widthDp = 400, heightDp = 780)
@Composable
private fun SearchContentLoadingPreview() {
    SearchPreviewBackdrop {
        SearchContent(
            search = previewSlotSearch(),
            rows = emptyList(),
            isLoading = true,
            onQuery = {},
            onPlay = {},
            onEnqueue = {},
            initialText = PREVIEW_QUERY,
        )
    }
}

@Preview(name = "No results", widthDp = 400, heightDp = 780)
@Composable
private fun SearchContentNoResultsPreview() {
    SearchPreviewBackdrop {
        SearchContent(
            search = previewSlotSearch(),
            rows = emptyList(),
            isLoading = false,
            onQuery = {},
            onPlay = {},
            onEnqueue = {},
            initialText = PREVIEW_QUERY,
        )
    }
}

@Preview(name = "Results", widthDp = 400, heightDp = 780)
@Composable
private fun SearchContentResultsPreview() {
    SearchPreviewBackdrop {
        SearchContent(
            search = previewSlotSearch(),
            rows = PREVIEW_SEARCH_ROWS,
            isLoading = false,
            onQuery = {},
            onPlay = {},
            onEnqueue = {},
            initialText = PREVIEW_QUERY,
        )
    }
}

@Preview(name = "Row", widthDp = 400, heightDp = 780)
@Composable
private fun SearchRowPreview() {
    SearchPreviewBackdrop {
        SearchRow(
            row = PREVIEW_SEARCH_ROWS.first(),
            onPlay = {},
            onEnqueue = {},
        )
    }
}

@Preview(name = "Row, title only", widthDp = 400, heightDp = 780)
@Composable
private fun SearchRowTitleOnlyPreview() {
    SearchPreviewBackdrop {
        SearchRow(
            row = PREVIEW_SEARCH_ROWS.last(),
            onPlay = {},
            onEnqueue = {},
        )
    }
}

@Preview(name = "Backdrop", widthDp = 400, heightDp = 780)
@Composable
private fun SearchPreviewBackdropPreview() {
    SearchPreviewBackdrop {
        Text(text = "Search", color = Ink)
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
