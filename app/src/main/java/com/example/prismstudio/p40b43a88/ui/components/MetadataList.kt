package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme

/**
 * The `metadata-list` renderer: label / value rows, keys tertiary, values primary. The key column is [keyWidth], or as
 * wide as the longest key in the language shown ("Timeline format"), up to [MAX_KEY_WIDTH].
 */
@Composable
fun MetadataList(
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 32.dp,
    keyWidth: Dp = 96.dp,
) {
    val keyColumn = sliderLabelWidth(rows.map { it.first }, keyWidth, MAX_KEY_WIDTH)
    Column(modifier) {
        rows.forEach { (key, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = rowHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(key, style = PrismType.label, color = PicoTheme.colorScheme.labelTertiary, modifier = Modifier.width(keyColumn), fit = true)
                Text(
                    value,
                    style = PrismType.label,
                    color = PicoTheme.colorScheme.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The widest a key column grows for long words. */
private val MAX_KEY_WIDTH = 160.dp
