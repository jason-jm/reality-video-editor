package com.example.prismstudio.p40b43a88.ui.components

import android.app.LocaleManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.example.prismstudio.p40b43a88.domain.i18n.AppLanguage
import com.example.prismstudio.p40b43a88.domain.i18n.I18n
import com.pico.spatial.ui.design.LocalContentColor
import com.pico.spatial.ui.design.LocalTextStyle

/** The language the studio shows; changing it recomposes everything, so every text redraws in it at once. */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ZH_HANS }
private val LocalTextLocales = staticCompositionLocalOf { LocaleList("zh-Hans") }

/** The explicit choice, or the first supported language in Android's current preference order. */
@Composable
fun ProvideAppLanguage(chosen: AppLanguage?, content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val localeManager = remember(context) { context.getSystemService(LocaleManager::class.java) }
    val language = chosen ?: run {
        // Resources may reorder or drop locales when resolving Android XML. Our UI catalogs are
        // independent of that resource set, so use the requested order from the platform.
        val requested = localeManager?.applicationLocales
        val system = localeManager?.systemLocales ?: configuration.locales
        remember(requested, system) {
            AppLanguage.forLocales(buildList {
                if (requested != null) for (index in 0 until requested.size()) add(requested[index])
                for (index in 0 until system.size()) add(system[index])
            })
        }
    }
    val locales = remember(language) { LocaleList(if (language == AppLanguage.PSEUDO) "en" else language.tag) }
    // Text drawn outside composition (canvas labels, toasts) reads it here.
    I18n.language = language
    CompositionLocalProvider(
        LocalAppLanguage provides language,
        LocalTextLocales provides locales,
        LocalTextStyle provides LocalTextStyle.current.copy(localeList = locales),
        LocalLayoutDirection provides LayoutDirection.Ltr,
        content = content,
    )
}

/**
 * Set inside a control whose label must stay on one line whatever the language — a segment, a chip ([SegmentItem],
 * [ButtonChip], [ToggleableChip]): a word longer than the room shrinks a little ([FIT_MIN_SCALE]) before it is cut.
 */
val LocalFitText = staticCompositionLocalOf { false }

/** How small a fitted label may get, as a share of its size. */
private const val FIT_MIN_SCALE = 0.72f

/** [text] (said in the Chinese source) in the language shown — as said in [context] when that has its own words. */
@Composable
@ReadOnlyComposable
fun tr(text: String, context: String? = null): String = I18n.translate(text, context, LocalAppLanguage.current)

/** Format a translated template without interpreting user-supplied names or filenames as interface text. */
@Composable
@ReadOnlyComposable
fun trFormat(template: String, vararg args: String): String = I18n.format(template, *args, language = LocalAppLanguage.current)

@Composable
@ReadOnlyComposable
fun projectDisplayName(name: String): String = I18n.projectName(name, LocalAppLanguage.current)

/**
 * The studio's Text: SpatialUI's, with what it shows in the language shown ([tr]). Pass [translate] false for the user's
 * own words (titles, names, notes), which are never translated. With [fit] (the default inside segments and chips) it
 * keeps to one line, shrinking to fit before it is cut. A [context] picks the words said there ([tr]).
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
    translate: Boolean = true,
    fit: Boolean = LocalFitText.current,
    fitLines: Int = 1,
    context: String? = null,
) {
    val textStyle = style.copy(localeList = LocalTextLocales.current)
    if (fit) {
        val merged = textStyle.merge(
            color = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } },
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            lineHeight = lineHeight,
            fontFamily = fontFamily,
            textDecoration = textDecoration,
            fontStyle = fontStyle,
            letterSpacing = letterSpacing,
        )
        val size = if (merged.fontSize.isSpecified) merged.fontSize else 14.sp
        BasicText(
            text = if (translate) tr(text, context) else text,
            modifier = modifier,
            style = merged,
            onTextLayout = onTextLayout,
            overflow = TextOverflow.Ellipsis,
            softWrap = fitLines > 1,
            maxLines = fitLines,
            autoSize = TextAutoSize.StepBased(minFontSize = size * FIT_MIN_SCALE, maxFontSize = size, stepSize = 0.5.sp),
        )
        return
    }
    com.pico.spatial.ui.design.Text(
        text = if (translate) tr(text, context) else text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = textStyle,
    )
}

/** The studio's Icon: SpatialUI's, its description (read aloud, and found by QA) in the language shown. */
@Composable
fun Icon(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    com.pico.spatial.ui.design.Icon(painter, contentDescription?.let { tr(it) }, modifier, tint)
}
