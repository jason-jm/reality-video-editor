package com.example.prismstudio.p40b43a88.ui.projects.components

import com.example.prismstudio.p40b43a88.BuildConfig.PRODUCT_NAME
import com.example.prismstudio.p40b43a88.ui.components.tabMovesFocus
import com.pico.spatial.ui.design.IconButton
import androidx.compose.foundation.layout.Arrangement
import com.example.prismstudio.p40b43a88.ui.components.LanguageMenuButton
import com.example.prismstudio.p40b43a88.domain.i18n.AppLanguage
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SearchField
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.TitleAlignment
import com.pico.spatial.ui.design.TitleBar

/** `library-title-bar`: title + project search in the title slot, "新建项目" in the trailing slot. */
@Composable
fun LibraryTitleBar(
    query: String,
    onQuery: (String) -> Unit,
    onNewProject: () -> Unit,
    modifier: Modifier = Modifier,
    language: AppLanguage? = null,
    languages: List<AppLanguage> = AppLanguage.selectable,
    onLanguage: (AppLanguage?) -> Unit = {},
    onAbout: () -> Unit = {},
) {
    TitleBar(
        modifier = modifier.fillMaxWidth(),
        titleAlignment = TitleAlignment.Center,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("项目库", style = PrismType.title, color = PicoTheme.colorScheme.labelPrimary)
                SearchField(
                    value = query,
                    onValueChange = onQuery,
                    onSearch = onQuery,
                    placeholder = { Text("搜索项目") },
                    modifier = Modifier.width(360.dp).tabMovesFocus(),
                )
            }
        },
        trailingActions = {
            // `about-button`: the version and the open-source notices (release R3).
            IconButton(onClick = onAbout) {
                Icon(painterResource(R.drawable.ic_info_circle), contentDescription = "关于 ${PRODUCT_NAME}")
            }
            LanguageMenuButton(language, languages, onLanguage)
            Button(
                onClick = onNewProject,
                size = ButtonDefaults.Regular,
                leadingIcon = { Icon(painterResource(R.drawable.ic_plus), contentDescription = null) },
            ) { Text("新建项目") }
        },
    )
}
