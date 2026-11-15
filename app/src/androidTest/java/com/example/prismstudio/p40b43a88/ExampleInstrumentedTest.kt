package com.example.prismstudio.p40b43a88

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import java.util.Locale

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun launcherAndApplicationUseTheSameNameInEveryLocale() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val launch = requireNotNull(app.packageManager.getLaunchIntentForPackage(app.packageName))
        val activity = app.packageManager.getActivityInfo(requireNotNull(launch.component), 0)
        val expected = "Reality Video Editor"
        assertEquals(expected, BuildConfig.PRODUCT_NAME)
        for (tag in listOf("zh-Hans", "zh-Hant-TW", "zh-HK", "en", "fr", "ja", "ar")) {
            val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val resources = app.createConfigurationContext(config).resources
            assertEquals(tag, expected, resources.getString(app.applicationInfo.labelRes))
            val launcherLabel = activity.labelRes.takeIf { it != 0 } ?: app.applicationInfo.labelRes
            assertEquals(tag, expected, resources.getString(launcherLabel))
        }
    }

    @Test
    fun useAppContext() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val target = app.packageManager.getInstrumentationInfo(instrumentation.componentName, 0).targetPackage
        assertEquals(target, app.packageName)
        assertNotNull(app.packageManager.getLaunchIntentForPackage(target))
    }
}
