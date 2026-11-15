package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.repository.LicenseNoticesRepository
import com.example.prismstudio.p40b43a88.domain.model.OpenSourceComponent
import com.example.prismstudio.p40b43a88.domain.model.OssLicense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The About sheet's open-source notices: what the release build ships, by project, with the license texts it owes. */
class OpenSourceNoticesUseCaseTest {
    private val notices = OpenSourceNoticesUseCase()

    @Test
    fun `components are read from libraries tsv and gathered by project, PICO's SDK first`() {
        val tsv = """
            androidx.media3	media3-exoplayer	1.9.3	apache-2.0
            com.pico.spatial.core	core	6.1.9	pico-sdk
            androidx.compose.runtime	runtime	1.8.0	apache-2.0
            com.pico.spatial.ui	ui	1.2.5	apache-2.0
            org.checkerframework	checker-qual	3.43.0	mit
            androidx.core	core	1.13.0	apache-2.0
            not a line
            x	y	1	gpl-3.0
        """.trimIndent()
        val components = notices.parse(tsv)
        assertEquals("a line it cannot read and an unknown license are left out", 6, components.size)
        val families = notices.families(components)
        assertEquals(
            listOf("PICO Spatial SDK", "PICO Compose UI", "AndroidX Media3", "AndroidX Compose", "AndroidX Jetpack", "Checker Framework qualifiers"),
            families.map { it.name },
        )
        assertEquals(OssLicense.PICO_SDK, families.first().license)
        assertEquals(OssLicense.MIT, families.last().license)
        assertEquals("Kotlin / kotlinx", notices.familyOf(OpenSourceComponent("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.9.0", OssLicense.APACHE_2)))
    }

    @Test
    fun `the bundled notices name a known license for every component, and carry the texts the licenses require`() {
        val bundled = LicenseNoticesRepository.Bundled
        val lines = bundled.libraries().lines().filter { it.isNotBlank() }
        val components = notices.parse(bundled.libraries())
        assertEquals("every line read", lines.size, components.size)
        assertTrue("the release build's libraries are listed (${components.size})", components.size > 100)
        assertTrue(components.any { it.group == "androidx.media3" && it.artifact == "media3-transformer" })
        // Every component lands in a named project, none under a bare group id.
        assertEquals(emptyList<String>(), components.map { notices.familyOf(it) }.filter { it.contains('.') }.distinct())
        assertTrue(components.filter { it.group.startsWith("com.pico.spatial") && it.version.startsWith("6.") }.all { it.license == OssLicense.PICO_SDK })
        // The Apache License and the MIT notice travel with the app, as both require; PICO's agreement is PICO's to publish.
        assertTrue(bundled.text(OssLicense.APACHE_2)!!.contains("Version 2.0, January 2004"))
        assertTrue(bundled.text(OssLicense.MIT)!!.contains("Copyright 2004-present by the Checker Framework developers"))
        assertNull(bundled.text(OssLicense.PICO_SDK))
    }
}
