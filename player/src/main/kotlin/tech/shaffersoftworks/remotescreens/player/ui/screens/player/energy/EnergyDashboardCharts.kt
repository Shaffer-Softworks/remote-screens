@file:Suppress("MagicNumber")

package tech.shaffersoftworks.remotescreens.player.ui.screens.player.energy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.grouped
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.stacked
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import tech.shaffersoftworks.remotescreens.shared.EnergyBarSeries
import tech.shaffersoftworks.remotescreens.shared.EnergyNamedSeries

internal val ENERGY_CHART_ACCENT = Color(0xFF03A9F4)
internal val ENERGY_CHART_CARD_BG = Color(0xFF1C1C1C)
internal val ENERGY_CHART_INK_DIM = Color(0xFF9E9E9E)

private const val CHART_HEIGHT_DP = 160
private const val STACKED_CHART_HEIGHT_DP = 140
/** Absolute dp; Vico zooms this with the chart, so keep it low vs [COLUMN_COLLECTION_SPACING_DP]. */
private const val COLUMN_THICKNESS_DP = 6
private const val COLUMN_COLLECTION_SPACING_DP = 18
private const val COLUMN_CORNER_PERCENT = 20

internal val PREVIEW_ELECTRICITY_VALUES = listOf(
    0.4, 0.3, 0.2, 0.5, 0.9, 1.4, 1.8, 2.1, 1.9, 1.6, 1.3, 1.1,
    1.5, 1.7, 1.4, 1.2, 1.6, 2.0, 2.2, 1.8, 1.2, 0.9, 0.6, 0.5,
)
internal val PREVIEW_DEVICE_AC = listOf(
    0.2, 0.2, 0.1, 0.3, 0.5, 0.7, 0.9, 1.0, 0.9, 0.8, 0.6, 0.5,
    0.7, 0.8, 0.7, 0.6, 0.8, 1.0, 1.1, 0.9, 0.6, 0.4, 0.3, 0.2,
)
internal val PREVIEW_DEVICE_GARAGE = listOf(
    0.1, 0.1, 0.1, 0.1, 0.2, 0.3, 0.4, 0.4, 0.3, 0.3, 0.2, 0.2,
    0.3, 0.3, 0.2, 0.2, 0.3, 0.4, 0.4, 0.3, 0.2, 0.2, 0.1, 0.1,
)
internal val PREVIEW_DEVICE_SERIES = listOf(
    EnergyNamedSeries("Rack AC", 0xFF03A9F4, PREVIEW_DEVICE_AC),
    EnergyNamedSeries("Garage", 0xFF795548, PREVIEW_DEVICE_GARAGE),
    EnergyNamedSeries("Garage", 0xFF795548, PREVIEW_DEVICE_GARAGE),
    EnergyNamedSeries("Garage", 0xFF795548, PREVIEW_DEVICE_GARAGE),
    EnergyNamedSeries("Garage", 0xFF795548, PREVIEW_DEVICE_GARAGE),
    EnergyNamedSeries("Garage", 0xFF795548, PREVIEW_DEVICE_GARAGE),
    EnergyNamedSeries("Garage", 0xFF795548, PREVIEW_DEVICE_GARAGE),
    EnergyNamedSeries("Garage", 0xFF795548, PREVIEW_DEVICE_GARAGE),
    EnergyNamedSeries("Garage", 0xFF795548, PREVIEW_DEVICE_GARAGE),
    EnergyNamedSeries("Garage", 0xFF795548, PREVIEW_DEVICE_GARAGE)
)

@Composable
internal fun ElectricityChart(series: EnergyBarSeries) {
    val values = series.values.ifEmpty { listOf(0.0) }
    val column = rememberLineComponent(
        fill = fill(ENERGY_CHART_ACCENT),
        thickness = COLUMN_THICKNESS_DP.dp,
        shape = CorneredShape.rounded(allPercent = COLUMN_CORNER_PERCENT),
    )
    EnergyColumnChartHost(
        columnProvider = ColumnCartesianLayer.ColumnProvider.series(column),
        mergeMode = { ColumnCartesianLayer.MergeMode.grouped() },
        buildModel = {
            ColumnCartesianLayerModel.build { series(values) }
        },
        updateProducer = {
            columnSeries { series(values) }
        },
        producerKeys = values,
        modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp),
    )
}

@Composable
internal fun DevicesStackedChart(series: List<EnergyNamedSeries>) {
    val columns = remember(series) {
        series.map { named ->
            LineComponent(
                fill = Fill(named.colorArgb.toInt()),
                thicknessDp = COLUMN_THICKNESS_DP.toFloat(),
                shape = CorneredShape.rounded(allPercent = COLUMN_CORNER_PERCENT),
            )
        }
    }
    EnergyColumnChartHost(
        columnProvider = ColumnCartesianLayer.ColumnProvider.series(columns),
        mergeMode = { ColumnCartesianLayer.MergeMode.stacked() },
        buildModel = {
            ColumnCartesianLayerModel.build {
                series.forEach { named ->
                    series(named.values.ifEmpty { listOf(0.0) })
                }
            }
        },
        updateProducer = {
            columnSeries {
                series.forEach { named ->
                    series(named.values.ifEmpty { listOf(0.0) })
                }
            }
        },
        producerKeys = series,
        modifier = Modifier.fillMaxWidth().height(STACKED_CHART_HEIGHT_DP.dp),
    )
}

/**
 * Compose Preview does not reliably run [LaunchedEffect] / Default-dispatcher work, so Vico's
 * model-producer path stays empty there. Use a synchronous [CartesianChartModel] in inspection
 * mode; keep the producer path for the live player.
 */
@Composable
internal fun EnergyColumnChartHost(
    columnProvider: ColumnCartesianLayer.ColumnProvider,
    mergeMode: (ExtraStore) -> ColumnCartesianLayer.MergeMode,
    buildModel: () -> ColumnCartesianLayerModel,
    updateProducer: CartesianChartModelProducer.Transaction.() -> Unit,
    producerKeys: Any?,
    modifier: Modifier,
) {
    val axisLabel = rememberAxisLabelComponent(color = ENERGY_CHART_INK_DIM)
    val chart = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = columnProvider,
            columnCollectionSpacing = COLUMN_COLLECTION_SPACING_DP.dp,
            mergeMode = mergeMode,
        ),
        startAxis = VerticalAxis.rememberStart(label = axisLabel),
        bottomAxis = HorizontalAxis.rememberBottom(label = axisLabel),
    )
    if (LocalInspectionMode.current) {
        val model = remember(producerKeys) {
            CartesianChartModel(buildModel())
        }
        CartesianChartHost(
            chart = chart,
            model = model,
            modifier = modifier,
        )
    } else {
        val modelProducer = remember { CartesianChartModelProducer() }
        LaunchedEffect(producerKeys) {
            modelProducer.runTransaction(updateProducer)
        }
        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            modifier = modifier,
        )
    }
}

@Preview(name = "Electricity chart", widthDp = 520, heightDp = 184)
@Composable
private fun ElectricityChartPreview() {
    RemoteScreensTheme {
        Box(Modifier.background(ENERGY_CHART_CARD_BG).padding(12.dp)) {
            ElectricityChart(
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

@Preview(name = "Devices stacked chart", widthDp = 520, heightDp = 184)
@Composable
private fun DevicesStackedChartPreview() {
    RemoteScreensTheme {
        Box(Modifier.background(ENERGY_CHART_CARD_BG).padding(12.dp)) {
            DevicesStackedChart(series = PREVIEW_DEVICE_SERIES)
        }
    }
}

@Preview(name = "Energy column chart host", widthDp = 520, heightDp = 184)
@Composable
private fun EnergyColumnChartHostPreview() {
    RemoteScreensTheme {
        Box(Modifier.background(ENERGY_CHART_CARD_BG).padding(12.dp)) {
            val column = rememberLineComponent(
                fill = fill(ENERGY_CHART_ACCENT),
                thickness = COLUMN_THICKNESS_DP.dp,
                shape = CorneredShape.rounded(allPercent = COLUMN_CORNER_PERCENT),
            )
            EnergyColumnChartHost(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(column),
                mergeMode = { ColumnCartesianLayer.MergeMode.grouped() },
                buildModel = {
                    ColumnCartesianLayerModel.build { series(PREVIEW_ELECTRICITY_VALUES) }
                },
                updateProducer = {
                    columnSeries { series(PREVIEW_ELECTRICITY_VALUES) }
                },
                producerKeys = PREVIEW_ELECTRICITY_VALUES,
                modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp),
            )
        }
    }
}
