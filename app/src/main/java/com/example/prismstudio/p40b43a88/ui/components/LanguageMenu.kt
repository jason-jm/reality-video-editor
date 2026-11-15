package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.i18n.AppLanguage
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.windows.popup.HorizontalPlacement
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.windows.popup.VerticalPlacement
import com.pico.spatial.ui.design.menu.rememberMenuPositionProvider

/**
 * `language-button`: the interface language — the system's, or one picked from the list (each in its own script, so
 * anyone can find theirs). [choices] are the languages offered (QA builds add the pseudo-locale).
 */
@Composable
fun LanguageMenuButton(chosen: AppLanguage?, choices: List<AppLanguage>, onPick: (AppLanguage?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_language), contentDescription = "界面语言")
        }
        if (open) {
            Menu(
                onDismissRequest = { open = false },
                positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignEnd(), VerticalPlacement.below()),
            ) {
                LanguageItem("跟随系统", translate = true, selected = chosen == null) { open = false; onPick(null) }
                choices.forEach { language ->
                    LanguageItem(language.nativeName, translate = false, selected = chosen == language) { open = false; onPick(language) }
                }
            }
        }
    }
}

@Composable
private fun LanguageItem(title: String, translate: Boolean, selected: Boolean, onClick: () -> Unit) {
    MenuItem(
        title = { Text(title, translate = translate) },
        onClick = onClick,
        trailingIcon = if (selected) {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = "当前语言") }
        } else {
            null
        },
    )
}

/** What a language change says (shown, like every message, in the language just picked). */
fun languageMessage(language: AppLanguage?): String = "界面语言：${language?.nativeName ?: "跟随系统"}"
