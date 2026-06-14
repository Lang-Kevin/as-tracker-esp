package com.kevin.armswing.ui.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel

@Composable
fun OmegaLineChart(
    velocityHistory: List<Float>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(velocityHistory) {
        if (velocityHistory.isNotEmpty()) {
            modelProducer.runTransaction {
                lineModel { series(velocityHistory) }
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(rememberLineCartesianLayer()),
        modelProducer = modelProducer,
        modifier = modifier
    )
}
