package com.example.prismstudio.p40b43a88.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.prismstudio.p40b43a88.domain.model.ClipColor
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.domain.model.MarkerColor
import com.example.prismstudio.p40b43a88.domain.model.TitleColor

/**
 * App-owned colours from the accepted design (`theme.brandColors`). They paint domain visuals —
 * clips, playhead, scopes, meters, picture areas — and never replace a native SpatialUI role.
 */
object PrismColors {
    val VideoClip = Color(0xFF4C78C8) // design-style: fixed-figma-color brandColors.videoClip
    val VideoClipSelected = Color(0xFF7AA2EA) // design-style: fixed-figma-color brandColors.videoClipSelected
    val AudioClip = Color(0xFF3E9A6B) // design-style: fixed-figma-color brandColors.audioClip
    val AudioClipSelected = Color(0xFF5DBB89) // design-style: fixed-figma-color brandColors.audioClipSelected
    val TitleClip = Color(0xFF8E6BD1) // design-style: fixed-figma-color brandColors.titleClip
    val TitleClipSelected = Color(0xFFA88AE6) // design-style: fixed-figma-color brandColors.titleClipSelected
    val Playhead = Color(0xFFF25C3B) // design-style: fixed-figma-color brandColors.playhead
    val MarkerFlag = Color(0xFFF2B33D) // design-style: fixed-figma-color brandColors.markerFlag
    // Marker colours (rev 46), Resolve's marker palette in part.
    val MarkerBlue = Color(0xFF4C8DF6) // design-style: fixed-figma-color brandColors.markerBlue
    val MarkerCyan = Color(0xFF3CC8E0) // design-style: fixed-figma-color brandColors.markerCyan
    val MarkerGreen = Color(0xFF52D273) // design-style: fixed-figma-color brandColors.markerGreen
    val MarkerYellow = Color(0xFFF2C14E) // design-style: fixed-figma-color brandColors.markerYellow
    val MarkerRed = Color(0xFFF2543D) // design-style: fixed-figma-color brandColors.markerRed
    val MarkerPink = Color(0xFFF06BB0) // design-style: fixed-figma-color brandColors.markerPink
    val MarkerPurple = Color(0xFF9B7BF2) // design-style: fixed-figma-color brandColors.markerPurple

    /** Resolve's flags share the marker palette. */
    fun flag(color: FlagColor): Color = when (color) {
        FlagColor.BLUE -> MarkerBlue
        FlagColor.CYAN -> MarkerCyan
        FlagColor.GREEN -> MarkerGreen
        FlagColor.YELLOW -> MarkerYellow
        FlagColor.RED -> MarkerRed
        FlagColor.PINK -> MarkerPink
        FlagColor.PURPLE -> MarkerPurple
    }

    fun marker(color: MarkerColor): Color = when (color) {
        MarkerColor.BLUE -> MarkerBlue
        MarkerColor.CYAN -> MarkerCyan
        MarkerColor.GREEN -> MarkerGreen
        MarkerColor.YELLOW -> MarkerYellow
        MarkerColor.RED -> MarkerRed
        MarkerColor.PINK -> MarkerPink
        MarkerColor.PURPLE -> MarkerPurple
    }
    // Clip colours (rev 51), dark enough for a clip's white name; Resolve's labels in part.
    val ClipRed = Color(0xFFC04A42) // design-style: fixed-figma-color brandColors.clipRed
    val ClipOrange = Color(0xFFCC6A2C) // design-style: fixed-figma-color brandColors.clipOrange
    val ClipYellow = Color(0xFFA8861C) // design-style: fixed-figma-color brandColors.clipYellow
    val ClipLime = Color(0xFF6C962C) // design-style: fixed-figma-color brandColors.clipLime
    val ClipTeal = Color(0xFF2A8C8C) // design-style: fixed-figma-color brandColors.clipTeal
    val ClipNavy = Color(0xFF34508F) // design-style: fixed-figma-color brandColors.clipNavy
    val ClipPink = Color(0xFFB8507F) // design-style: fixed-figma-color brandColors.clipPink
    val ClipBrown = Color(0xFF7E5A3C) // design-style: fixed-figma-color brandColors.clipBrown

    fun clip(color: ClipColor): Color = when (color) {
        ClipColor.RED -> ClipRed
        ClipColor.ORANGE -> ClipOrange
        ClipColor.YELLOW -> ClipYellow
        ClipColor.LIME -> ClipLime
        ClipColor.TEAL -> ClipTeal
        ClipColor.NAVY -> ClipNavy
        ClipColor.PINK -> ClipPink
        ClipColor.BROWN -> ClipBrown
    }

    // Title text colours (rev 47); a swatch shows each on the picture black, black on white.
    val TitleWhite = Color(0xFFFFFFFF) // design-style: fixed-figma-color brandColors.titleWhite
    val TitleYellow = Color(0xFFFFD84D) // design-style: fixed-figma-color brandColors.titleYellow
    val TitleAmber = Color(0xFFF2B33D) // design-style: fixed-figma-color brandColors.titleAmber
    val TitleRed = Color(0xFFF2543D) // design-style: fixed-figma-color brandColors.titleRed
    val TitleCyan = Color(0xFF7FE3FF) // design-style: fixed-figma-color brandColors.titleCyan
    val TitleGreen = Color(0xFF8BF28B) // design-style: fixed-figma-color brandColors.titleGreen
    val TitleBlack = Color(0xFF141414) // design-style: fixed-figma-color brandColors.titleBlack

    fun title(color: TitleColor): Color = when (color) {
        TitleColor.WHITE -> TitleWhite
        TitleColor.YELLOW -> TitleYellow
        TitleColor.AMBER -> TitleAmber
        TitleColor.RED -> TitleRed
        TitleColor.CYAN -> TitleCyan
        TitleColor.GREEN -> TitleGreen
        TitleColor.BLACK -> TitleBlack
    }

    /** The plate a title colour's swatch sits on: the picture, or white under black text. */
    fun titlePlate(color: TitleColor): Color = if (color.dark) TitleWhite else PictureBlack

    val InOutRange = Color(0x40F2B33D) // design-style: fixed-figma-color brandColors.inOutRange
    val Waveform = Color(0xFFCDEFD9) // design-style: fixed-figma-color brandColors.waveform
    val PictureBlack = Color(0xFF0B0B0C) // design-style: fixed-figma-color brandColors.pictureBlack
    val ScopeLuma = Color(0xFFDDE8E2) // design-style: fixed-figma-color brandColors.scopeLuma
    val ScopeRed = Color(0xFFFF5A5A) // design-style: fixed-figma-color brandColors.scopeRed
    val ScopeGreen = Color(0xFF52D273) // design-style: fixed-figma-color brandColors.scopeGreen
    val ScopeBlue = Color(0xFF4F8DFF) // design-style: fixed-figma-color brandColors.scopeBlue
    val MeterSafe = Color(0xFF4CD07D) // design-style: fixed-figma-color brandColors.meterSafe
    val MeterHot = Color(0xFFF2C14E) // design-style: fixed-figma-color brandColors.meterHot
    val MeterClip = Color(0xFFF2543D) // design-style: fixed-figma-color brandColors.meterClip

    /** Poster/frame colours carried as ARGB longs by the domain model. */
    fun argb(value: Long): Color = Color(value.toInt())
}
