package tech.shaffersoftworks.remotescreens.player.ui.screensaver

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

internal const val SCREENSAVER_FADE_MS = 400

private const val CROSSFADE_MS = 700
private const val KEN_BURNS_MS = 24_000
private const val CLOCK_TICK_SECONDS = 1
private const val EDGE_PADDING_DP = 28
private const val CLOCK_SIZE_SP = 64
private const val DATE_SIZE_SP = 22
private const val CHIP_TEMPERATURE_SP = 22
private const val CHIP_CONDITION_SP = 14
private const val CHIP_ICON_DP = 36
private const val CHIP_CORNER_DP = 18
private const val CHIP_HORIZONTAL_PADDING_DP = 16
private const val CHIP_VERTICAL_PADDING_DP = 10
private const val CHIP_GAP_DP = 12
private const val OPAQUE = 1f
private const val KEN_BURNS_ZOOM = 0.08f
private const val KEN_BURNS_PAN = 0.04f
private const val DRIFT_START = 0.35f
private const val SCRIM_ALPHA = 0.62f
private const val SCRIM_CLEAR_STOP = 0.34f
private const val SCRIM_RETURN_STOP = 0.66f
private const val CHIP_FILL_ALPHA = 0.18f
private const val DATE_ALPHA = 0.9f
private const val SHADOW_BLUR = 16f
private const val SHADOW_DY = 2f
private const val SHADOW_ALPHA = 0.65f
private const val SWATCH_PX = 8
private const val DAY_SWATCH = 0xFF7BA3C9.toInt()
private const val NIGHT_SWATCH = 0xFF1B2430.toInt()
private const val CROSSFADE_SWATCH = 0xFFC4A574.toInt()
private const val PREVIEW_BLEND = 0.5f
private const val PREVIEW_YEAR = 2026
private const val PREVIEW_MONTH = 11
private const val PREVIEW_DAY = 27
private const val DAY_HOUR = 7
private const val DAY_MINUTE = 45
private const val NIGHT_HOUR = 22
private const val NIGHT_MINUTE = 18
private const val EMPTY_HOUR = 10
private const val EMPTY_MINUTE = 18

private val TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMM d")

private val SCRIM = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = SCRIM_ALPHA),
    SCRIM_CLEAR_STOP to Color.Transparent,
    SCRIM_RETURN_STOP to Color.Transparent,
    1f to Color.Black.copy(alpha = SCRIM_ALPHA),
)

private val CLOCK_SHADOW = Shadow(
    color = Color.Black.copy(alpha = SHADOW_ALPHA),
    offset = Offset(x = 0f, y = SHADOW_DY),
    blurRadius = SHADOW_BLUR,
)

private val CONDITION_LABELS = mapOf(
    "clear-night" to "Clear",
    "cloudy" to "Cloudy",
    "exceptional" to "Exceptional",
    "fog" to "Fog",
    "hail" to "Hail",
    "lightning" to "Lightning",
    "lightning-rainy" to "Lightning",
    "partlycloudy" to "Partly cloudy",
    "pouring" to "Pouring",
    "rainy" to "Rainy",
    "snowy" to "Snowy",
    "snowy-rainy" to "Snowy",
    "sunny" to "Sunny",
    "windy" to "Windy",
    "windy-variant" to "Windy",
)

private data class PhotoBlend(
    val current: Bitmap?,
    val previous: Bitmap?,
    val amount: Float,
)

/** Full-screen photo, clock, and weather. The first touch is consumed so the dashboard underneath is not clicked. */
@Composable
internal fun ScreensaverOverlay(
    photo: Bitmap?,
    condition: String?,
    temperature: String?,
    icon: Bitmap?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    now: LocalDateTime? = null,
    heldPrevious: Bitmap? = null,
    heldBlend: Float? = null,
    heldDrift: Float? = null,
) {
    val shownNow = now ?: rememberNow()
    val blend = rememberPhotoBlend(photo = photo, heldPrevious = heldPrevious, heldBlend = heldBlend)
    val drift = heldDrift ?: rememberDrift()
    val driftsRight = (blend.current?.hashCode() ?: 0) % 2 == 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(onDismiss) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)

                        if (event.type == PointerEventType.Press) {
                            event.changes.forEach { change -> change.consume() }
                            onDismiss()
                        }
                    }
                }
            },
    ) {
        ScreensaverPhoto(
            bitmap = blend.previous,
            alpha = OPAQUE - blend.amount,
            drift = drift,
            driftsRight = !driftsRight,
        )

        ScreensaverPhoto(
            bitmap = blend.current,
            alpha = if (blend.previous == null) OPAQUE else blend.amount,
            drift = drift,
            driftsRight = driftsRight,
        )

        Box(Modifier.fillMaxSize().background(SCRIM))

        Column(modifier = Modifier.padding(EDGE_PADDING_DP.dp)) {
            Text(
                text = shownNow.format(TIME_FORMAT),
                color = Color.White,
                fontSize = CLOCK_SIZE_SP.sp,
                fontWeight = FontWeight.Light,
                style = TextStyle(shadow = CLOCK_SHADOW),
            )

            Text(
                text = shownNow.format(DATE_FORMAT),
                color = Color.White.copy(alpha = DATE_ALPHA),
                fontSize = DATE_SIZE_SP.sp,
                fontWeight = FontWeight.Normal,
                style = TextStyle(shadow = CLOCK_SHADOW),
            )
        }

        if (!temperature.isNullOrBlank() || !condition.isNullOrBlank()) {
            WeatherChip(
                condition = condition,
                temperature = temperature.orEmpty(),
                icon = icon,
                modifier = Modifier.align(Alignment.BottomStart).padding(EDGE_PADDING_DP.dp),
            )
        }
    }
}

@Composable
private fun ScreensaverPhoto(
    bitmap: Bitmap?,
    alpha: Float,
    drift: Float,
    driftsRight: Boolean,
) {
    if (bitmap == null || alpha <= 0f) return

    val direction = if (driftsRight) 1f else -1f
    val shift = (drift - DRIFT_START) * direction

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            val zoom = OPAQUE + KEN_BURNS_ZOOM
            scaleX = zoom
            scaleY = zoom
            translationX = shift * size.width * KEN_BURNS_PAN
            translationY = shift * size.height * KEN_BURNS_PAN
            this.alpha = alpha
        },
    )
}

@Composable
private fun WeatherChip(
    condition: String?,
    temperature: String,
    icon: Bitmap?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(CHIP_CORNER_DP.dp))
            .background(Color.White.copy(alpha = CHIP_FILL_ALPHA))
            .padding(horizontal = CHIP_HORIZONTAL_PADDING_DP.dp, vertical = CHIP_VERTICAL_PADDING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP_DP.dp),
    ) {
        if (icon != null) {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(CHIP_ICON_DP.dp),
            )
        }

        Column {
            Text(
                text = temperature,
                color = Color.White,
                fontSize = CHIP_TEMPERATURE_SP.sp,
                fontWeight = FontWeight.Medium,
            )

            if (!condition.isNullOrBlank()) {
                Text(
                    text = CONDITION_LABELS[condition] ?: condition,
                    color = Color.White.copy(alpha = DATE_ALPHA),
                    fontSize = CHIP_CONDITION_SP.sp,
                )
            }
        }
    }
}

@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            now = LocalDateTime.now()
            delay(CLOCK_TICK_SECONDS.seconds)
        }
    }

    return now
}

@Composable
private fun rememberDrift(): Float {
    val transition = rememberInfiniteTransition(label = "screensaverDrift")

    val drift by transition.animateFloat(
        initialValue = DRIFT_START,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = KEN_BURNS_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )

    return drift
}

@Composable
private fun rememberPhotoBlend(photo: Bitmap?, heldPrevious: Bitmap?, heldBlend: Float?): PhotoBlend {
    var current by remember { mutableStateOf(photo) }
    var previous by remember { mutableStateOf<Bitmap?>(null) }
    val amount = remember { Animatable(1f) }

    LaunchedEffect(photo, heldBlend) {
        if (heldBlend != null) return@LaunchedEffect

        when {
            photo == null -> {
                current = null
                previous = null
                amount.snapTo(OPAQUE)
            }
            current == null || current == photo -> {
                current = photo
                previous = null
                amount.snapTo(OPAQUE)
            }
            else -> {
                previous = current
                current = photo
                amount.snapTo(0f)
                amount.animateTo(targetValue = OPAQUE, animationSpec = tween(durationMillis = CROSSFADE_MS))
                previous = null
            }
        }
    }

    return if (heldBlend != null) {
        PhotoBlend(current = photo, previous = heldPrevious, amount = heldBlend)
    } else {
        PhotoBlend(current = current, previous = previous, amount = amount.value)
    }
}

private fun swatch(color: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(SWATCH_PX, SWATCH_PX, Bitmap.Config.ARGB_8888)

    bitmap.eraseColor(color)

    return bitmap
}

private fun previewNow(hour: Int, minute: Int): LocalDateTime =
    LocalDateTime.of(PREVIEW_YEAR, PREVIEW_MONTH, PREVIEW_DAY, hour, minute)

@Preview(name = "Day", widthDp = 800, heightDp = 480)
@Composable
private fun ScreensaverOverlayPreview() {
    RemoteScreensTheme {
        ScreensaverOverlay(
            photo = swatch(DAY_SWATCH),
            condition = "partlycloudy",
            temperature = "72°",
            icon = null,
            onDismiss = {},
            now = previewNow(hour = DAY_HOUR, minute = DAY_MINUTE),
            heldDrift = DRIFT_START,
        )
    }
}

@Preview(name = "Night", widthDp = 800, heightDp = 480)
@Composable
private fun ScreensaverOverlayNightPreview() {
    RemoteScreensTheme {
        ScreensaverOverlay(
            photo = swatch(NIGHT_SWATCH),
            condition = "clear-night",
            temperature = "48°",
            icon = null,
            onDismiss = {},
            now = previewNow(hour = NIGHT_HOUR, minute = NIGHT_MINUTE),
            heldDrift = OPAQUE,
        )
    }
}

@Preview(name = "Crossfade", widthDp = 800, heightDp = 480)
@Composable
private fun ScreensaverOverlayCrossfadePreview() {
    RemoteScreensTheme {
        ScreensaverOverlay(
            photo = swatch(CROSSFADE_SWATCH),
            condition = "partlycloudy",
            temperature = "72°",
            icon = null,
            onDismiss = {},
            now = previewNow(hour = DAY_HOUR, minute = DAY_MINUTE),
            heldPrevious = swatch(DAY_SWATCH),
            heldBlend = PREVIEW_BLEND,
            heldDrift = DRIFT_START,
        )
    }
}

@Preview(name = "Empty library", widthDp = 800, heightDp = 480)
@Composable
private fun ScreensaverOverlayEmptyPreview() {
    RemoteScreensTheme {
        ScreensaverOverlay(
            photo = null,
            condition = "clear-night",
            temperature = "48°",
            icon = null,
            onDismiss = {},
            now = previewNow(hour = EMPTY_HOUR, minute = EMPTY_MINUTE),
            heldDrift = DRIFT_START,
        )
    }
}

@Preview(name = "Photo and chip", widthDp = 800, heightDp = 480)
@Composable
private fun ScreensaverPhotoPreview() {
    RemoteScreensTheme {
        Box(Modifier.fillMaxSize()) {
            ScreensaverPhoto(
                bitmap = swatch(DAY_SWATCH),
                alpha = OPAQUE,
                drift = DRIFT_START,
                driftsRight = true,
            )

            WeatherChip(
                condition = "partlycloudy",
                temperature = "72°",
                icon = null,
                modifier = Modifier.align(Alignment.BottomStart).padding(EDGE_PADDING_DP.dp),
            )
        }
    }
}
