package tech.shaffersoftworks.remotescreens.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter

internal class AlertsCases : Cases("Alerts")

@Preview(name = "Alerts", widthDp = 800, heightDp = 480, device = "spec:width=800dp,height=480dp,dpi=160")
@Composable
private fun AlertsPreview(@PreviewParameter(AlertsCases::class) sample: PreviewCase) {
    ServerDocument(sample)
}
