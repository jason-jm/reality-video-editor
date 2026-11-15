package com.example.prismstudio.p40b43a88

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.prismstudio.p40b43a88.data.AppGraph
import com.example.prismstudio.p40b43a88.data.repository.SharedPrefsLanguageRepository
import com.example.prismstudio.p40b43a88.domain.i18n.AppLanguage
import com.example.prismstudio.p40b43a88.domain.i18n.I18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguagePickerTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val app = instrument.targetContext
    private val automation = instrument.uiAutomation

    private fun node(label: String): AccessibilityNodeInfo? {
        val roots = listOfNotNull(automation.rootInActiveWindow) + automation.windows.mapNotNull { it.root }
        val queue = ArrayDeque(roots)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current.packageName == app.packageName && (current.text?.toString() == label ||
                    current.contentDescription?.toString() == label)) return current
            for (idx in 0 until current.childCount) current.getChild(idx)?.let(queue::addLast)
        }
        return null
    }

    private fun await(label: String): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            node(label)?.let { return it }
            SystemClock.sleep(150)
        }
        error("No accessible control for '$label'")
    }

    private fun click(label: String) {
        var target: AccessibilityNodeInfo? = await(label)
        while (target != null) {
            if (target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            target = target.parent
        }
        error("Cannot click '$label'")
    }

    @Test
    fun systemFontsContainTheLettersUsedByEveryPackagedCatalog() {
        for (language in AppLanguage.selectable) {
            val tag = if (language == AppLanguage.ZH_HANS) "en" else language.tag
            val paint = android.graphics.Paint().apply { textLocale = java.util.Locale.forLanguageTag(language.tag) }
            val letters = mutableSetOf<Int>()
            requireNotNull(I18n::class.java.getResourceAsStream("/i18n/$tag.tsv")).bufferedReader().useLines { rows ->
                rows.filter { '\t' in it && !it.startsWith("#") }.forEach {
                    val text = if (language == AppLanguage.ZH_HANS) it.substringBefore('\t') else it.substringAfter('\t')
                    text.codePoints().filter(Character::isLetter).forEach(letters::add)
                }
            }
            assertTrue("Missing catalog: ${language.tag}", letters.size > 25)
            val unsupported = letters.filterNot { paint.hasGlyph(String(Character.toChars(it))) }
            assertTrue("${language.tag}: missing glyphs $unsupported", unsupported.isEmpty())
        }
    }

    @Test(timeout = 90_000)
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun followingTheSystemUsesTheRequestedOrderEvenIfAndroidReordersResourceLocales() {
        val manager = requireNotNull(app.getSystemService(android.app.LocaleManager::class.java))
        val override = manager.applicationLocales
        val prefs = AppGraph.from(app).language
        val initial = prefs.language.value
        try {
            prefs.setLanguage(null)
            app.startActivity(requireNotNull(app.packageManager.getLaunchIntentForPackage(app.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            for ((order, language) in listOf("ar-EG,en-GB" to AppLanguage.EN, "it-IT,ja-JP" to AppLanguage.JA)) {
                manager.applicationLocales = android.os.LocaleList.forLanguageTags(order)
                await(I18n.translate("界面语言", language))
            }
        } finally {
            manager.applicationLocales = override
            prefs.setLanguage(initial)
        }
    }

    @Test(timeout = 150_000)
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun pickerOffersEveryEndonymAndPersistsTheSelectedLanguage() {
        val prefs = AppGraph.from(app).language
        val initial = prefs.language.value
        val info = automation.serviceInfo
        val previousFlags = info.flags
        try {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
            prefs.setLanguage(AppLanguage.EN)
            app.startActivity(requireNotNull(app.packageManager.getLaunchIntentForPackage(app.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            click("Interface language")
            for (language in AppLanguage.selectable) await(language.nativeName)
            val order = listOf(AppLanguage.RU) + AppLanguage.selectable.filterNot { it == AppLanguage.RU }
            for (language in order) {
                click(language.nativeName)
                val button = I18n.translate("界面语言", language)
                await(button)
                assertEquals(language, SharedPrefsLanguageRepository(app).language.value)
                click(button)
            }
            click(I18n.translate("跟随系统", order.last()))
            assertTrue("System default removes the saved override", SharedPrefsLanguageRepository(app).language.value == null)
        } finally {
            prefs.setLanguage(initial)
            info.flags = previousFlags
            automation.serviceInfo = info
        }
    }
}
