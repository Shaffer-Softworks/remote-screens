@file:Suppress("MagicNumber")

package tech.shaffersoftworks.remotescreens.player.ui.screens.player.energy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import tech.shaffersoftworks.remotescreens.shared.EnergyBarSeries
import tech.shaffersoftworks.remotescreens.shared.EnergyDashboardTheme
import tech.shaffersoftworks.remotescreens.shared.EnergyDistribution
import tech.shaffersoftworks.remotescreens.shared.EnergyNamedSeries
import tech.shaffersoftworks.remotescreens.shared.EnergyRankBar
import tech.shaffersoftworks.remotescreens.shared.EnergySankey
import tech.shaffersoftworks.remotescreens.shared.EnergySankeyLink
import tech.shaffersoftworks.remotescreens.shared.EnergySankeyNode
import tech.shaffersoftworks.remotescreens.shared.EnergyTotalRow
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import tech.shaffersoftworks.remotescreens.shared.Slot

private const val CARD_RADIUS_DP = 12
private const val GAUGE_SWEEP_DEG = 180f
private const val GAUGE_START_DEG = 180f
private const val LEGEND_MAX = 6
private const val RANK_BAR_MAX = 10

@Composable
internal fun ElectricityCard(series: EnergyBarSeries) {
    val locale = LocalLocale.current.platformLocale
    EnergyCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Electricity", color = LocalEnergyColors.current.ink, fontWeight = FontWeight.SemiBold)
            Text(
                text = String.format(locale, "%.2f %s", series.totalKwh, series.unit),
                color = LocalEnergyColors.current.inkDim,
            )
        }
        Spacer(Modifier.height(8.dp))
        ElectricityChart(series)
    }
}

@Composable
internal fun TotalsCard(rows: List<EnergyTotalRow>) {
    val locale = LocalLocale.current.platformLocale
    EnergyCard {
        Text("Totals", color = LocalEnergyColors.current.ink, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Source", color = LocalEnergyColors.current.inkDim, fontSize = 12.sp)
            Text("Energy", color = LocalEnergyColors.current.inkDim, fontSize = 12.sp)
            Text("Cost", color = LocalEnergyColors.current.inkDim, fontSize = 12.sp)
        }
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = row.source,
                    color = LocalEnergyColors.current.ink,
                    fontSize = 13.sp,
                    modifier = Modifier.width(80.dp),
                )
                Text(
                    text = String.format(locale, "%.2f kWh", row.energyKwh),
                    color = LocalEnergyColors.current.ink,
                    fontSize = 13.sp,
                )
                Text(
                    text = row.cost?.let { String.format(locale, "$%.2f", it) } ?: "—",
                    color = LocalEnergyColors.current.ink,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
internal fun DevicesDetailCard(series: List<EnergyNamedSeries>) {
    EnergyCard {
        Text("Individual devices detail", color = LocalEnergyColors.current.ink, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (series.isEmpty()) {
            Text("No device data", color = LocalEnergyColors.current.inkDim)
            return@EnergyCard
        }
        DevicesStackedChart(series)

        Column (
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            series.take(LEGEND_MAX).forEach { named ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(Color(named.colorArgb.toInt()), RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = named.name,
                        color = LocalEnergyColors.current.inkDim,
                        fontSize = 11.sp,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 8.sp,
                            maxFontSize = 12.sp,
                            stepSize = 2.sp, // optional step increment (defaults to 0.25.sp)
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun DevicesRankCard(bars: List<EnergyRankBar>) {
    val locale = LocalLocale.current.platformLocale
    EnergyCard {
        Text(
            text = "Individual devices",
            color = LocalEnergyColors.current.ink,
            fontWeight = FontWeight.SemiBold,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.sp,
                maxFontSize = 12.sp,
                stepSize = 2.sp, // optional step increment (defaults to 0.25.sp)
            ),
        )

        Spacer(Modifier.height(8.dp))

        val maxKwh = bars.maxOfOrNull { it.kwh }?.coerceAtLeast(0.01) ?: 1.0
        bars.take(RANK_BAR_MAX).forEach { bar ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = bar.name,
                    color = LocalEnergyColors.current.ink,
                    fontSize = 13.sp,
                    modifier = Modifier.width(140.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .background(LocalEnergyColors.current.track, RoundedCornerShape(4.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((bar.kwh / maxKwh).toFloat().coerceIn(0f, 1f))
                            .background(Color(bar.colorArgb.toInt()), RoundedCornerShape(4.dp)),
                    )
                }
                Text(
                    text = String.format(locale, "%.1f", bar.kwh),
                    color = LocalEnergyColors.current.inkDim,
                    fontSize = 12.sp,
                    modifier = Modifier.width(48.dp).padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun DistributionCard(distribution: EnergyDistribution) {
    EnergyCard {
        Text("Energy distribution", color = LocalEnergyColors.current.ink, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DistNode("Low-carbon", distribution.lowCarbonKwh, Color(0xFF4CAF50))
            DistNode(distribution.homeName.ifBlank { "Home" }, distribution.homeKwh, LocalEnergyColors.current.accent)
            DistNode("Grid", distribution.gridKwh, Color(0xFF90CAF9))
        }
    }
}

@Composable
internal fun DistNode(label: String, kwh: Double, color: Color) {
    val locale = LocalLocale.current.platformLocale
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
        Spacer(Modifier.width(8.dp))
        Text(label, color = LocalEnergyColors.current.ink, modifier = Modifier.weight(1f))
        Text(String.format(locale, "%.1f kWh", kwh), color = LocalEnergyColors.current.inkDim)
    }
}

@Composable
internal fun GaugeCard(percent: Double) {
    val locale = LocalLocale.current.platformLocale
    EnergyCard {
        Text("Low-carbon electricity consumed", color = LocalEnergyColors.current.ink, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        val colors = LocalEnergyColors.current
        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(160.dp)) {
                val stroke = 18.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                drawArc(
                    color = colors.track,
                    startAngle = GAUGE_START_DEG,
                    sweepAngle = GAUGE_SWEEP_DEG,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = colors.accent,
                    startAngle = GAUGE_START_DEG,
                    sweepAngle = (GAUGE_SWEEP_DEG * (percent / 100.0).coerceIn(0.0, 1.0)).toFloat(),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Text(
                text = String.format(locale, "%.0f%%", percent),
                color = LocalEnergyColors.current.ink,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

@Composable
internal fun SankeyCard(sankey: EnergySankey) {
    val locale = LocalLocale.current.platformLocale
    EnergyCard {
        Text(
            text = "Energy flow",
            color = LocalEnergyColors.current.ink,
            fontWeight = FontWeight.SemiBold,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.sp,
                maxFontSize = 12.sp,
                stepSize = 2.sp, // optional step increment (defaults to 0.25.sp)
            ),
        )

        Spacer(Modifier.height(8.dp))

        if (sankey.nodes.isEmpty()) {
            Text("No flow data", color = LocalEnergyColors.current.inkDim)
            return@EnergyCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sankey.links.take(12).forEach { link ->
                val from = sankey.nodes.firstOrNull { it.id == link.fromId }?.label ?: link.fromId
                val to = sankey.nodes.firstOrNull { it.id == link.toId }?.label ?: link.toId
                Text(
                    text = String.format(locale, "%s → %s  %.1f kWh", from, to, link.kwh),
                    color = LocalEnergyColors.current.inkDim,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
internal fun EnergyCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(LocalEnergyColors.current.card, RoundedCornerShape(CARD_RADIUS_DP.dp))
            .padding(12.dp),
    ) {
        content()
    }
}

internal fun previewDashboardMessage(): ServerMessage.EnergyDashboard =
    ServerMessage.EnergyDashboard(
        slot = Slot.MAIN,
        rev = 1,
        page = 0,
        tileId = "ed1",
        componentId = 0x5F_0000,
        periodLabel = "Nov 12",
        isNow = true,
        electricity = EnergyBarSeries(
            title = "Electricity",
            totalKwh = 33.44,
            labels = emptyList(),
            values = PREVIEW_ELECTRICITY_VALUES,
        ),
        totals = listOf(
            EnergyTotalRow("Grid", 33.44, 7.2),
            EnergyTotalRow("Grid total", 33.44, 7.2),
        ),
        devicesDetail = PREVIEW_DEVICE_SERIES,
        devicesRank = listOf(
            EnergyRankBar("Rack AC", 12.0, 0xFF03A9F4),
            EnergyRankBar("Garage", 5.0, 0xFF795548),
            EnergyRankBar("Kitchen", 3.2, 0xFFFF9800),
            EnergyRankBar("Office", 2.1, 0xFF9C27B0),
        ),
        distribution = EnergyDistribution(13.9, 33.4, 33.4, "Shaffer Home"),
        lowCarbonPercent = 42.0,
        sankey = EnergySankey(
            nodes = listOf(
                EnergySankeyNode("grid", "Grid", 0),
                EnergySankeyNode("home", "Home", 1),
                EnergySankeyNode("rack", "Rack AC", 3),
                EnergySankeyNode("garage", "Garage", 3),
            ),
            links = listOf(
                EnergySankeyLink("grid", "home", 33.4),
                EnergySankeyLink("home", "rack", 12.0),
                EnergySankeyLink("home", "garage", 5.0),
            ),
        ),
        theme = EnergyDashboardTheme(
            page = 0xFF494949,
            card = 0xFF333333,
            ink = 0xFFFFFFFF,
            inkDim = 0xFF939393,
            accent = 0xFF14E1BA,
            track = 0xFF1B1B1B,
        ),
    )

@Preview(name = "Electricity card", widthDp = 520, heightDp = 220)
@Composable
private fun ElectricityCardPreview() {
    RemoteScreensTheme {
        Box(Modifier.background(LocalEnergyColors.current.page).padding(12.dp)) {
            ElectricityCard(
                series = EnergyBarSeries(
                    title = "Electricity",
                    totalKwh = 33.44,
                    labels = emptyList(),
                    values = PREVIEW_ELECTRICITY_VALUES,
                ),
            )
        }
    }
}

@Preview(name = "Totals card", widthDp = 520, heightDp = 210)
@Composable
private fun TotalsCardPreview() {
    RemoteScreensTheme {
        Box(Modifier.background(LocalEnergyColors.current.page).padding(12.dp)) {
            TotalsCard(
                rows = listOf(
                    EnergyTotalRow("Grid", 33.44, 7.2),
                    EnergyTotalRow("Grid total", 33.44, 7.2),
                ),
            )
        }
    }
}

@Preview(name = "Devices detail card", widthDp = 520, heightDp = 280)
@Composable
private fun DevicesDetailCardPreview() {
    RemoteScreensTheme {
        Box(Modifier.background(LocalEnergyColors.current.page).padding(12.dp)) {
            DevicesDetailCard(series = PREVIEW_DEVICE_SERIES)
        }
    }
}

@Preview(name = "Devices detail card empty", widthDp = 520, heightDp = 100)
@Composable
private fun DevicesDetailCardEmptyPreview() {
    RemoteScreensTheme {
        Box(Modifier.background(LocalEnergyColors.current.page).padding(12.dp)) {
            DevicesDetailCard(series = emptyList())
        }
    }
}

@Preview(name = "Devices rank card", widthDp = 520, heightDp = 220)
@Composable
private fun DevicesRankCardPreview() {
    RemoteScreensTheme {
        Box(Modifier.background(LocalEnergyColors.current.page).padding(12.dp)) {
            DevicesRankCard(bars = previewDashboardMessage().devicesRank)
        }
    }
}

@Preview(name = "Distribution card", widthDp = 320, heightDp = 180)
@Composable
private fun DistributionCardPreview() {
    RemoteScreensTheme {
        Box(Modifier.background(LocalEnergyColors.current.page).padding(12.dp)) {
            DistributionCard(
                distribution = EnergyDistribution(13.9, 33.4, 33.4, "Shaffer Home"),
            )
        }
    }
}

@Preview(name = "Dist node", widthDp = 320, heightDp = 64)
@Composable
private fun DistNodePreview() {
    RemoteScreensTheme {
        Box(Modifier.background(LocalEnergyColors.current.card).padding(12.dp)) {
            DistNode("Low-carbon", 13.9, Color(0xFF4CAF50))
        }
    }
}

@Preview(name = "Gauge card", widthDp = 320, heightDp = 220)
@Composable
private fun GaugeCardPreview() {
    RemoteScreensTheme {
        Box(Modifier.background(LocalEnergyColors.current.page).padding(12.dp)) {
            GaugeCard(percent = 42.0)
        }
    }
}

@Preview(name = "Sankey card", widthDp = 520, heightDp = 200)
@Composable
private fun SankeyCardPreview() {
    RemoteScreensTheme {
        Box(Modifier.background(LocalEnergyColors.current.page).padding(12.dp)) {
            SankeyCard(sankey = previewDashboardMessage().sankey)
        }
    }
}

@Preview(name = "Energy card", widthDp = 320, heightDp = 100)
@Composable
private fun EnergyCardPreview() {
    RemoteScreensTheme {
        Box(Modifier.background(LocalEnergyColors.current.page).padding(12.dp)) {
            EnergyCard {
                Text("Card chrome", color = LocalEnergyColors.current.ink)
            }
        }
    }
}
