package tech.shaffersoftworks.remotescreens.player.ui.screens.player.assist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.shaffersoftworks.remotescreens.player.data.device.assist.AssistTurn
import tech.shaffersoftworks.remotescreens.player.data.device.assist.AssistUiState
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import tech.shaffersoftworks.remotescreens.shared.AssistPhases
import tech.shaffersoftworks.remotescreens.shared.AssistTheme
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import tech.shaffersoftworks.remotescreens.shared.Slot

private val SIDE_WIDTH = 350.dp
private val STACK_BREAK = 700.dp
private val PAGE_PAD = 20.dp
private val ACCENT_BAR_WIDTH = 100.dp
private val ACCENT_BAR_HEIGHT = 10.dp
private val KEY_SIZE = 88.dp

/** Native Assist conversation. Partial lines stay here so the Remote Compose document is not rebuilt. */
@Composable
fun AssistScreen(
    page: ServerMessage.AssistPage,
    assist: AssistUiState,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = page.theme.toColors()

    val side = @Composable {
        AssistSide(
            phase = phaseLine(assist = assist, wakeWord = page.wakeWord),
            pipeline = page.pipelineLabel,
            isMuted = assist.isMuted || assist.phase == AssistPhases.MUTED,
            colors = colors,
            onToggleMute = onToggleMute,
            fillWidth = false,
        )
    }

    BoxWithConstraints(modifier.fillMaxSize().background(colors.page)) {
        if (maxWidth < STACK_BREAK) {
            Column {
                AssistSide(
                    phase = phaseLine(assist = assist, wakeWord = page.wakeWord),
                    pipeline = page.pipelineLabel,
                    isMuted = assist.isMuted || assist.phase == AssistPhases.MUTED,
                    colors = colors,
                    onToggleMute = onToggleMute,
                    fillWidth = true,
                )

                AssistConversation(assist = assist, colors = colors, modifier = Modifier.weight(1f))
            }
        } else {
            Row {
                side()
                AssistConversation(assist = assist, colors = colors, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AssistSide(
    phase: String,
    pipeline: String,
    isMuted: Boolean,
    colors: AssistColors,
    onToggleMute: () -> Unit,
    fillWidth: Boolean,
) {
    Column(
        modifier = Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(SIDE_WIDTH).fillMaxHeight())
            .background(colors.page)
            .padding(top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .padding(start = PAGE_PAD)
                .width(ACCENT_BAR_WIDTH)
                .height(ACCENT_BAR_HEIGHT)
                .background(colors.accent),
        )

        Text(
            text = "ASSIST",
            color = colors.ink,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = PAGE_PAD, top = 10.dp, bottom = 8.dp),
        )

        Text(
            text = phase,
            color = colors.ink,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = PAGE_PAD),
        )

        Column(
            modifier = Modifier
                .padding(PAGE_PAD)
                .width(KEY_SIZE)
                .height(KEY_SIZE)
                .background(colors.accent)
                .clickable(onClick = onToggleMute),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = if (isMuted) "Unmute" else "Mute", color = colors.page, fontSize = 16.sp)
        }

        Text(
            text = pipeline.ifBlank { "Preferred" },
            color = colors.inkDim,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = PAGE_PAD),
        )
    }
}

@Composable
private fun AssistConversation(assist: AssistUiState, colors: AssistColors, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(PAGE_PAD),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        assist.turns.forEach { turn ->
            Text(text = turn.speaker, color = colors.inkDim, fontSize = 14.sp)
            Text(text = turn.text, color = colors.ink, fontSize = 22.sp, fontWeight = FontWeight.Normal)
        }

        val live = assist.caption

        if (live != null) {
            Text(text = live, color = colors.accent, fontSize = 22.sp)
        } else if (assist.phase == AssistPhases.LISTENING) {
            Text(text = "Say ${assist.wakeWord}", color = colors.inkDim, fontSize = 22.sp)
        }
    }
}

private fun phaseLine(assist: AssistUiState, wakeWord: String): String = when {
    assist.isMuted || assist.phase == AssistPhases.MUTED -> "Muted"
    assist.phase == AssistPhases.HEARING -> "Hearing you"
    assist.phase == AssistPhases.THINKING -> "Thinking"
    assist.phase == AssistPhases.SPEAKING -> "Speaking"
    assist.phase == AssistPhases.UNAVAILABLE -> "Unavailable"
    else -> "Listening for $wakeWord"
}

private data class AssistColors(val page: Color, val ink: Color, val inkDim: Color, val accent: Color)

private fun AssistTheme.toColors(): AssistColors = AssistColors(
    page = Color(page.toInt()),
    ink = Color(ink.toInt()),
    inkDim = Color(inkDim.toInt()),
    accent = Color(accent.toInt()),
)

@Preview(name = "Assist", widthDp = 800, heightDp = 480)
@Composable
private fun AssistScreenPreview() {
    RemoteScreensTheme {
        AssistScreen(
            page = ServerMessage.AssistPage(
                slot = Slot.MAIN,
                rev = 1,
                page = 0,
                tileId = "assist",
                componentId = 0x62_0000,
                wakeWord = "Okay Nabu",
                pipelineLabel = "Preferred",
            ),
            assist = AssistUiState(
                phase = AssistPhases.SPEAKING,
                wakeWord = "Okay Nabu",
                turns = listOf(
                    AssistTurn(speaker = "You", text = "Turn off the kitchen lights"),
                    AssistTurn(speaker = "Assist", text = "The kitchen lights are off"),
                ),
            ),
            onToggleMute = {},
        )
    }
}

@Preview(name = "Assist side", widthDp = 800, heightDp = 480)
@Composable
private fun AssistSidePreview() {
    RemoteScreensTheme {
        AssistSide(
            phase = "Speaking",
            pipeline = "Preferred",
            isMuted = false,
            colors = AssistTheme().toColors(),
            onToggleMute = {},
            fillWidth = false,
        )
    }
}

@Preview(name = "Assist conversation", widthDp = 800, heightDp = 480)
@Composable
private fun AssistConversationPreview() {
    RemoteScreensTheme {
        AssistConversation(
            assist = AssistUiState(
                phase = AssistPhases.SPEAKING,
                wakeWord = "Okay Nabu",
                turns = listOf(
                    AssistTurn(speaker = "You", text = "Turn off the kitchen lights"),
                    AssistTurn(speaker = "Assist", text = "The kitchen lights are off"),
                ),
            ),
            colors = AssistTheme().toColors(),
            modifier = Modifier,
        )
    }
}
