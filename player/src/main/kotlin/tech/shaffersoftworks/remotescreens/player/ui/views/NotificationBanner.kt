package tech.shaffersoftworks.remotescreens.player.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.shaffersoftworks.remotescreens.player.R
import tech.shaffersoftworks.remotescreens.shared.ServerMessage

private const val ICON_VIEWPORT = 24f
private const val MAX_TOP_INSET_DIVISOR = 4

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun NotificationBannerPreview() {
    tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme {
        NotificationBanner(
            banner = ServerMessage.NotificationBanner(
                id = "preview",
                title = "Laundry is ready",
                message = "The washing machine has finished.",
                buttons = listOf("Acknowledge"),
                topInsetDp = 48f,
                iconPath = "M12,22A2,2 0 0,0 14,20H10A2,2 0 0,0 12,22M18,16V11C18,7.93 16.36,5.36 13.5,4.68V4" +
                    "A1.5,1.5 0 0,0 10.5,4V4.68C7.63,5.36 6,7.92 6,11V16L4,18V19H20V18Z",
            ),
            onAction = {},
        )
    }
}

/** Only the card intercepts touches; the dashboard outside it remains interactive. */
@Composable
fun NotificationBanner(
    banner: ServerMessage.NotificationBanner,
    onAction: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val top = banner.topInsetDp.coerceAtLeast(0f).dp.coerceAtMost(maxHeight / MAX_TOP_INSET_DIVISOR)
        val cardHeight = (maxHeight - top - 24.dp).coerceAtLeast(48.dp)
        val tone = when (banner.level) {
            "danger" -> MaterialTheme.colorScheme.error
            "warning" -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }
        val path = remember(banner.iconPath) {
            runCatching { PathParser().parsePathString(banner.iconPath).toPath() }.getOrNull()
        }
        Surface(
            modifier = Modifier.padding(top = top + 8.dp, start = 12.dp, end = 12.dp)
                .widthIn(max = 720.dp).fillMaxWidth().heightIn(max = cardHeight)
                .semantics { liveRegion = LiveRegionMode.Polite },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    if (path != null) {
                        Canvas(Modifier.padding(top = 4.dp).size(24.dp)) {
                            scale(size.width / ICON_VIEWPORT, pivot = Offset.Zero) {
                                drawPath(path, tone)
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(banner.title, style = MaterialTheme.typography.titleMedium)
                        if (banner.message.isNotBlank()) {
                            Text(
                                banner.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val dismissLabel = stringResource(R.string.dismiss_notification)
                    IconButton(
                        onClick = { onAction(null) },
                        modifier = Modifier.semantics { contentDescription = dismissLabel },
                    ) {
                        Text("×", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                if (banner.buttons.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        banner.buttons.forEachIndexed { index, label ->
                            TextButton(onClick = { onAction(index) }) { Text(label) }
                        }
                    }
                }
            }
        }
    }
}
