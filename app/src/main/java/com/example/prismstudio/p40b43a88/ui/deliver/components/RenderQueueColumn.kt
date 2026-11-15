package com.example.prismstudio.p40b43a88.ui.deliver.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.RenderStatus
import com.example.prismstudio.p40b43a88.ui.deliver.RenderJobUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.HorizontalDivider
import com.pico.spatial.ui.design.LinearProgressIndicator
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text

/** `render-queue-column`: queued / running / finished renders and their actions. */
@Composable
fun RenderQueueColumn(
    jobs: List<RenderJobUi>,
    lastRenderSummary: String?,
    renderAllReady: Boolean,
    onRenderAll: () -> Unit,
    onCancel: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Finished renders are listed: `clear-done-button` can take them off. */
    clearDoneEnabled: Boolean = false,
    onClearDone: () -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("渲染队列", style = PrismType.title, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.weight(1f), fit = true)
            if (clearDoneEnabled) {
                Button(
                    onClick = onClearDone,
                    size = ButtonDefaults.Regular,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) { Text("清除已完成") }
            }
            // Never disabled: here SpatialUI's disabled button drew its label as solid blocks on the device — Latin
            // (rev 165), then Chinese too (rev 169), Regular or Small, primary or quiet colours — while the same button
            // beside the viewer drew fine. With nothing to render it is greyed, and a press says why.
            Button(
                onClick = onRenderAll,
                size = ButtonDefaults.Small,
                colors = if (renderAllReady) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelTertiary)
                },
            ) { Text("全部渲染") }
        }
        lastRenderSummary?.let { summary ->
            Text(
                summary, style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (jobs.isEmpty()) {
            Text("队列为空。设置好参数后点“加入渲染队列”。", style = PrismType.label, color = PicoTheme.colorScheme.labelTertiary)
        } else {
            val list = rememberLazyListState()
            // The job just added, or the one that starts rendering, comes into view: a project's queue keeps its finished
            // jobs, and in a long one the new job and its 取消 button were below the bottom (R18).
            val rendering = jobs.indexOfFirst { it.status == RenderStatus.RENDERING }
            LaunchedEffect(jobs.size, rendering) {
                val target = if (rendering >= 0) rendering else jobs.lastIndex
                val shown = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
                if (shown == null || shown.offset < 0 || shown.offset + shown.size > list.layoutInfo.viewportEndOffset) list.animateScrollToItem(target)
            }
            LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(jobs, key = { it.id }) { job -> JobBlock(job, onCancel, onOpen, onRetry) }
            }
        }
    }
}

@Composable
private fun JobBlock(job: RenderJobUi, onCancel: (String) -> Unit, onOpen: (String) -> Unit, onRetry: (String) -> Unit) {
    val quiet = ButtonDefaults.buttonColors(
        containerColor = PicoTheme.colorScheme.fillTertiary,
        contentColor = PicoTheme.colorScheme.labelPrimary,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(job.name, style = PrismType.body, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(job.statusLabel, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
        }
        Text(job.detail, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        job.rangeLabel?.let { Text(it, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary) }
        if (job.status == RenderStatus.RENDERING) {
            LinearProgressIndicator(
                progress = { job.progress },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "渲染进度 ${(job.progress * 100).toInt()}%".localized() },
            )
        }
        job.error?.let { Text(it, style = PrismType.caption, color = PicoTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (job.cancellable) Button(onClick = { onCancel(job.id) }, size = ButtonDefaults.Small, colors = quiet) { Text("取消") }
            if (job.status.mediaSaved) Button(onClick = { onOpen(job.id) }, size = ButtonDefaults.Small, colors = quiet) { Text(job.openLabel) }
            job.retryLabel?.let { label -> Button(onClick = { onRetry(job.id) }, size = ButtonDefaults.Small, colors = quiet) { Text(label) } }
        }
        HorizontalDivider()
    }
}
