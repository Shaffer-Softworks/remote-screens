package tech.shaffersoftworks.remotescreens.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter

internal class PortraitCases : Cases("Portrait")
internal class LargeCases : Cases("Large")

@Preview(name = "Portrait", widthDp = 480, heightDp = 800, device = "spec:width=480dp,height=800dp,dpi=160")
@Composable
private fun PortraitPreview(@PreviewParameter(PortraitCases::class) sample: PreviewCase) {
    ServerDocument(sample)
}

@Preview(name = "Large tablet", widthDp = 1280, heightDp = 800, device = "spec:width=1280dp,height=800dp,dpi=160")
@Composable
private fun LargePreview(@PreviewParameter(LargeCases::class) sample: PreviewCase) {
    ServerDocument(sample)
}
