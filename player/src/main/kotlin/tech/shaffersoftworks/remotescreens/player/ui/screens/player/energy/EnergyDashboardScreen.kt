@file:Suppress("MagicNumber")

package tech.shaffersoftworks.remotescreens.player.ui.screens.player.energy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import tech.shaffersoftworks.remotescreens.shared.ServerMessage

private val PAGE_BG = Color(0xFF121212)
private val INK = Color(0xFFE8E8E8)
private val ACCENT = Color(0xFF03A9F4)

/** Native HA-style Energy dashboard over the Remote Compose page hole. */
@Composable
fun EnergyDashboardScreen(
    message: ServerMessage.EnergyDashboard,
    onAction: (verb: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(PAGE_BG)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    Modifier.weight(2f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ElectricityCard(message.electricity)
                    TotalsCard(message.totals)
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DistributionCard(message.distribution)
                    GaugeCard(message.lowCarbonPercent)
                }
            }
            DevicesDetailCard(message.devicesDetail)
            DevicesRankCard(message.devicesRank)
            SankeyCard(message.sankey)
        }
        PeriodBar(
            label = message.periodLabel,
            isNow = message.isNow,
            onPrev = { onAction("energy_dash_prev") },
            onNext = { onAction("energy_dash_next") },
            onNow = { onAction("energy_dash_now") },
            onPeriod = { onAction("energy_dash_period") },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        )
    }
}

@Composable
private fun PeriodBar(
    label: String,
    isNow: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onNow: () -> Unit,
    onPeriod: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .background(Color(0xEE1A1A1A), RoundedCornerShape(28.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = INK,
            modifier = Modifier.clickable(onClick = onPeriod).padding(horizontal = 8.dp),
        )
        TextButton(onClick = onNow, enabled = !isNow) {
            Text(if (isNow) "Now" else "Now", color = if (isNow) ACCENT else INK)
        }
        Text("<", color = INK, modifier = Modifier.clickable(onClick = onPrev).padding(8.dp))
        Text(">", color = INK, modifier = Modifier.clickable(onClick = onNext).padding(8.dp))
    }
}

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun EnergyDashboardScreenPreview() {
    RemoteScreensTheme {
        EnergyDashboardScreen(
            message = previewDashboardMessage(),
            onAction = {},
        )
    }
}

@Preview(name = "Period bar", widthDp = 420, heightDp = 72)
@Composable
private fun PeriodBarPreview() {
    RemoteScreensTheme {
        Box(Modifier.background(PAGE_BG).padding(12.dp), contentAlignment = Alignment.Center) {
            PeriodBar(
                label = "Nov 12",
                isNow = true,
                onPrev = {},
                onNext = {},
                onNow = {},
                onPeriod = {},
            )
        }
    }
}
