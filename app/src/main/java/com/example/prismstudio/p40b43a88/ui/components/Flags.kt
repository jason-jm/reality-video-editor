package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.i18n.labelled
import com.example.prismstudio.p40b43a88.domain.i18n.listed
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.layout.offset

/**
 * `flags` parts: Resolve's flags on a picture's corner (a pool tile, a strip thumbnail) — small flags in their colours
 * on a dark pill, as the duration badge sits on a poster. Nothing when there are none.
 */
@Composable
fun FlagMarks(flags: List<FlagColor>, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    if (flags.isEmpty()) return
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .drawBehind { drawRect(PrismColors.PictureBlack, alpha = 0.72f) }
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics { contentDescription = labelled("旗标".localized(), listed(flags.map { "${it.label}色".localized() })) },
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        flags.forEach { Icon(painterResource(R.drawable.ic_flag), contentDescription = null, tint = PrismColors.flag(it), modifier = Modifier.size(size)) }
    }
}

/**
 * `flag-row`: the flag colours to put on or take off media (each lit while the media carries it) and 清除 for all of
 * them — in the clip menu, and on the Media page for the selected media.
 */
@Composable
fun FlagToggles(
    flags: Collection<FlagColor>,
    onToggle: (FlagColor) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 32.dp,
) {
    Row(
        modifier.semantics { contentDescription = "旗标".localized() },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlagColor.entries.forEach { color -> FlagToggle(color, color in flags, enabled, size) { onToggle(color) } }
        ButtonChip(
            label = { Text("清除") },
            onClick = onClear,
            enabled = enabled && flags.isNotEmpty(),
            chipSize = ChipsDefaults.Small,
            modifier = Modifier.semantics { contentDescription = "清除旗标".localized() },
        )
    }
}

/** One flag colour: lit on a plate and lifted while carried, faint otherwise. */
@Composable
private fun FlagToggle(color: FlagColor, on: Boolean, enabled: Boolean, size: Dp, onClick: () -> Unit) {
    val plate = PicoTheme.colorScheme.fillSecondary
    Box(
        modifier = Modifier
            .size(size)
            .offset(z = if (on) 8.dp else 0.dp)
            .clip(CircleShape)
            .spatialHoverEffect(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.selected = on }
            .drawBehind { if (on) drawRect(plate) }
            .semantics { contentDescription = ("${color.label}色旗标" + if (on) "（已标）" else "").localized() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_flag),
            contentDescription = null,
            tint = PrismColors.flag(color).copy(alpha = if (on) 1f else FAINT),
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

private const val FAINT = 0.45f
