package tech.shaffersoftworks.remotescreens.player.ui.screens.player.assist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.shaffersoftworks.remotescreens.player.data.device.assist.AssistUiState
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import tech.shaffersoftworks.remotescreens.shared.AssistPhases

private val MIC_SIZE = 48.dp
private val MIC_INSET = 12.dp
private val CAPTION_BOTTOM = 72.dp
private const val MIC_STROKE = 4f
private const val HEAD_RADIUS_DIVISOR = 4f
private const val HEAD_Y_DIVISOR = 2.4f
private const val STEM_END = 0.82f
private const val SLASH_START = 0.2f
private const val SLASH_END = 0.8f
private val LISTENING = Color(0xFF3D7FD6)
private val MUTED = Color(0xFF9E9E9E)

/** Trailing header mic. Native so a phase change does not rebuild the document. */
@Composable
fun AssistHeaderMic(
    assist: AssistUiState,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (assist.isMuted || assist.phase == AssistPhases.MUTED) MUTED else LISTENING

    Box(
        modifier = modifier.size(MIC_SIZE).clickable(onClick = onToggleMute),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(MIC_INSET)) {
            val radius = size.minDimension / HEAD_RADIUS_DIVISOR
            val headY = size.height / HEAD_Y_DIVISOR

            drawCircle(
                color = tint,
                radius = radius,
                center = Offset(x = size.width / 2f, y = headY),
                style = Stroke(width = MIC_STROKE),
            )

            drawLine(
                color = tint,
                start = Offset(x = size.width / 2f, y = headY + radius),
                end = Offset(x = size.width / 2f, y = size.height * STEM_END),
                strokeWidth = MIC_STROKE,
                cap = StrokeCap.Round,
            )

            if (assist.isMuted || assist.phase == AssistPhases.MUTED) {
                drawLine(
                    color = tint,
                    start = Offset(x = size.width * SLASH_START, y = size.height * SLASH_START),
                    end = Offset(x = size.width * SLASH_END, y = size.height * SLASH_END),
                    strokeWidth = MIC_STROKE,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Heard line, then the reply, on a page that is not Assist. Clears when speech ends. */
@Composable
fun AssistCaption(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = LISTENING,
        fontSize = 18.sp,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Preview(name = "Assist mic", widthDp = 800, heightDp = 480)
@Composable
private fun AssistChromePreview() {
    RemoteScreensTheme {
        Box(Modifier.fillMaxSize()) {
            AssistHeaderMic(
                assist = AssistUiState(phase = AssistPhases.LISTENING),
                onToggleMute = {},
                modifier = Modifier.align(Alignment.TopEnd).padding(MIC_INSET),
            )

            AssistCaption(
                text = "Turn off the kitchen lights",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = CAPTION_BOTTOM),
            )
        }
    }
}
