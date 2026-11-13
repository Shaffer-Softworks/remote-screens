package tech.shaffersoftworks.remotescreens.server.data.energy

import tech.shaffersoftworks.remotescreens.shared.EnergyBarSeries
import tech.shaffersoftworks.remotescreens.shared.EnergyDistribution
import tech.shaffersoftworks.remotescreens.shared.EnergyNamedSeries
import tech.shaffersoftworks.remotescreens.shared.EnergyRankBar
import tech.shaffersoftworks.remotescreens.shared.EnergySankey
import tech.shaffersoftworks.remotescreens.shared.EnergySankeyLink
import tech.shaffersoftworks.remotescreens.shared.EnergySankeyNode
import tech.shaffersoftworks.remotescreens.shared.EnergyTotalRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private const val ALPHA_OPAQUE = 0xFF000000L
private const val COLOR_BLUE = 0xFF03A9F4L
private const val COLOR_TEAL = 0xFF009688L
private const val COLOR_ORANGE = 0xFFFF9800L
private const val COLOR_RED = 0xFFF44336L
private const val COLOR_PURPLE = 0xFF9C27B0L
private const val COLOR_BROWN = 0xFF795548L
private const val COLOR_GOLD = 0xFFFFC107L
private const val COLOR_GREEN = 0xFF4CAF50L
private const val COLOR_GRAY = 0xFF9E9E9EL
private const val DAY_MS = 86_400_000L
private const val DAYS_PER_WEEK = 7L
private const val PERCENT = 100.0
private const val WEEK_END_OFFSET_DAYS = 6L

private val DEVICE_COLORS = longArrayOf(
    COLOR_BLUE,
    COLOR_BROWN,
    COLOR_TEAL,
    COLOR_RED,
    COLOR_GOLD,
    COLOR_PURPLE,
    COLOR_ORANGE,
    COLOR_GREEN,
)

/** Builds the chart snapshot the player renders from recorder statistics. */
object EnergyDashboardBuilder {
    private val dayLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val hourLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("h a", Locale.US)

    fun build(
        config: EnergyDashboardConfig,
        ui: EnergyDashboardUiState,
        zone: ZoneId,
        gridPoints: List<EnergyStatPoint>,
        devicePoints: Map<String, List<EnergyStatPoint>>,
        fossilKwh: Double?,
        nowMs: Long = System.currentTimeMillis(),
    ): EnergyDashboardSnapshot {
        val labels = bucketLabels(ui.period, gridPoints, zone)
        val gridValues = alignValues(gridPoints, labels.size)
        val gridTotal = gridValues.sum()
        val cost = config.gridPrice?.let { gridTotal * it }
        val deviceSeries = deviceSeries(config, devicePoints, labels.size)
        val deviceTotals = deviceTotals(deviceSeries)
        val untracked = max(0.0, gridTotal - deviceTotals.sumOf { it.kwh })

        val totalConsumed = gridTotal  // later: + max(0, solar - toGrid)
        val lowCarbonKwh = fossilKwh?.let { (totalConsumed - it).coerceAtLeast(0.0) }
        val lowCarbonPercent = if (fossilKwh != null && totalConsumed > 0.0) {
            (1.0 - fossilKwh / totalConsumed).coerceIn(0.0, 1.0) * PERCENT
        } else {
            null  // or 0.0 until protocol/UI support “not calculated”
        }

        return EnergyDashboardSnapshot(
            periodLabel = periodLabel(ui, zone, nowMs),
            isNow = ui.offset == 0,
            electricity = EnergyBarSeries(
                title = "Electricity",
                totalKwh = gridTotal,
                labels = labels,
                values = gridValues,
            ),
            totals = totalsRows(config.gridName, gridTotal, cost),
            devicesDetail = deviceSeries,
            devicesRank = deviceTotals,
            distribution = EnergyDistribution(
                lowCarbonKwh = lowCarbonKwh ?: 0.0,  // or keep nullable if you extend the type
                gridKwh = gridTotal,
                homeKwh = gridTotal,
                homeName = config.homeName,
            ),
            lowCarbonPercent = lowCarbonPercent ?: 0.0,
            sankey = buildSankey(
                config = config,
                gridTotal = gridTotal,
                deviceTotals = deviceTotals,
                untracked = untracked,
            ),
        )
    }

    private fun deviceSeries(
        config: EnergyDashboardConfig,
        devicePoints: Map<String, List<EnergyStatPoint>>,
        bucketCount: Int,
    ): List<EnergyNamedSeries> = config.devices.mapIndexed { index, device ->
        EnergyNamedSeries(
            name = device.name,
            colorArgb = DEVICE_COLORS[index % DEVICE_COLORS.size] or ALPHA_OPAQUE,
            values = alignValues(devicePoints[device.statConsumption].orEmpty(), bucketCount),
        )
    }

    private fun deviceTotals(series: List<EnergyNamedSeries>): List<EnergyRankBar> =
        series.map { named ->
            EnergyRankBar(name = named.name, kwh = named.values.sum(), colorArgb = named.colorArgb)
        }.sortedByDescending { it.kwh }

    private fun totalsRows(gridName: String, gridTotal: Double, cost: Double?): List<EnergyTotalRow> = listOf(
        EnergyTotalRow(source = gridName, energyKwh = gridTotal, cost = cost),
        EnergyTotalRow(source = "Grid total", energyKwh = gridTotal, cost = cost),
    )

    private fun buildSankey(
        config: EnergyDashboardConfig,
        gridTotal: Double,
        deviceTotals: List<EnergyRankBar>,
        untracked: Double,
    ): EnergySankey {
        if (gridTotal <= 0.0) return EnergySankey()
        val nodes = mutableListOf(
            EnergySankeyNode(id = "grid", label = config.gridName, column = 0, colorArgb = COLOR_BLUE),
            EnergySankeyNode(id = "home", label = config.homeName, column = 1, colorArgb = COLOR_TEAL),
        )
        val links = mutableListOf(
            EnergySankeyLink(fromId = "grid", toId = "home", kwh = gridTotal),
        )
        val byArea = linkedMapOf<String, Double>()
        for (device in config.devices) {
            val total = deviceTotals.firstOrNull { it.name == device.name }?.kwh ?: 0.0
            if (total <= 0.0) continue
            val areaKey = device.areaId ?: "untracked"
            val areaLabel = device.areaName ?: "Untracked"
            byArea[areaKey] = (byArea[areaKey] ?: 0.0) + total
            val areaNodeId = "area.$areaKey"
            if (nodes.none { it.id == areaNodeId }) {
                nodes += EnergySankeyNode(
                    id = areaNodeId,
                    label = areaLabel,
                    column = 2,
                    colorArgb = COLOR_GRAY,
                )
            }
            val deviceId = "device.${device.statConsumption}"
            val color = deviceTotals.firstOrNull { it.name == device.name }?.colorArgb ?: COLOR_BLUE
            nodes += EnergySankeyNode(id = deviceId, label = device.name, column = 3, colorArgb = color)
            links += EnergySankeyLink(fromId = "home", toId = areaNodeId, kwh = total)
            links += EnergySankeyLink(fromId = areaNodeId, toId = deviceId, kwh = total)
        }
        if (untracked > 0.0) {
            nodes += EnergySankeyNode(
                id = "untracked",
                label = "Untracked consumption",
                column = 3,
                colorArgb = COLOR_GRAY,
            )
            links += EnergySankeyLink(fromId = "home", toId = "untracked", kwh = untracked)
        }
        return EnergySankey(nodes = nodes, links = links)
    }

    private fun bucketLabels(
        period: EnergyDashboardPeriod,
        points: List<EnergyStatPoint>,
        zone: ZoneId,
    ): List<String> {
        if (points.isEmpty()) return emptyList()
        val formatter = when (period) {
            EnergyDashboardPeriod.DAY -> hourLabel
            EnergyDashboardPeriod.WEEK, EnergyDashboardPeriod.MONTH -> dayLabel
        }
        return points.map { point ->
            Instant.ofEpochMilli(point.startMs).atZone(zone).format(formatter)
        }
    }

    private fun alignValues(points: List<EnergyStatPoint>, size: Int): List<Double> {
        if (size <= 0 || points.isEmpty()) {
            return if (size <= 0) emptyList() else List(size) { 0.0 }
        }
        val values = points.take(size).map { it.change }
        return if (values.size >= size) {
            values.take(size)
        } else {
            values + List(size - values.size) { 0.0 }
        }
    }

    private fun periodLabel(ui: EnergyDashboardUiState, zone: ZoneId, nowMs: Long): String {
        val zoned = Instant.ofEpochMilli(nowMs).atZone(zone)
        val shifted = when (ui.period) {
            EnergyDashboardPeriod.DAY -> zoned.plusDays(ui.offset.toLong())
            EnergyDashboardPeriod.WEEK -> zoned.plusWeeks(ui.offset.toLong())
            EnergyDashboardPeriod.MONTH -> zoned.plusMonths(ui.offset.toLong())
        }
        return when (ui.period) {
            EnergyDashboardPeriod.DAY -> shifted.format(dayLabel)
            EnergyDashboardPeriod.WEEK -> {
                val start = shifted.minusDays((shifted.dayOfWeek.value - 1).toLong())
                "${start.format(dayLabel)} – ${start.plusDays(WEEK_END_OFFSET_DAYS).format(dayLabel)}"
            }
            EnergyDashboardPeriod.MONTH -> shifted.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))
        }
    }

    fun window(
        ui: EnergyDashboardUiState,
        zone: ZoneId,
        nowMs: Long = System.currentTimeMillis(),
    ): Pair<Long, Long> {
        val zoned = Instant.ofEpochMilli(nowMs).atZone(zone)
        val end = when (ui.period) {
            EnergyDashboardPeriod.DAY -> {
                val day = zoned.toLocalDate().plusDays(ui.offset.toLong() + 1L)
                day.atStartOfDay(zone).toInstant().toEpochMilli()
            }
            EnergyDashboardPeriod.WEEK -> {
                val startOfWeek = zoned.toLocalDate()
                    .minusDays((zoned.dayOfWeek.value - 1).toLong())
                    .plusWeeks(ui.offset.toLong() + 1L)
                startOfWeek.atStartOfDay(zone).toInstant().toEpochMilli()
            }
            EnergyDashboardPeriod.MONTH -> {
                val month = zoned.toLocalDate().withDayOfMonth(1).plusMonths(ui.offset.toLong() + 1L)
                month.atStartOfDay(zone).toInstant().toEpochMilli()
            }
        }
        val start = when (ui.period) {
            EnergyDashboardPeriod.DAY -> end - DAY_MS
            EnergyDashboardPeriod.WEEK -> end - DAYS_PER_WEEK * DAY_MS
            EnergyDashboardPeriod.MONTH -> {
                val monthStart = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
                    .minusMonths(1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
                monthStart
            }
        }
        return start to end
    }

    fun recorderPeriod(ui: EnergyDashboardUiState): String = when (ui.period) {
        EnergyDashboardPeriod.DAY -> "hour"
        EnergyDashboardPeriod.WEEK, EnergyDashboardPeriod.MONTH -> "day"
    }
}
