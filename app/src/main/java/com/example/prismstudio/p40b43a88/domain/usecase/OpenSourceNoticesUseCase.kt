package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.OpenSourceComponent
import com.example.prismstudio.p40b43a88.domain.model.OpenSourceFamily
import com.example.prismstudio.p40b43a88.domain.model.OssLicense

/**
 * The About sheet's open-source notices: the components the release build ships (libraries.tsv, written by
 * devtools/release/gen_licenses.py from the release dependency graph) gathered by project, PICO's SDK first.
 */
class OpenSourceNoticesUseCase {

    /** Parses libraries.tsv: group, artifact, version, license id a line; lines it cannot read are left out. */
    fun parse(tsv: String): List<OpenSourceComponent> = tsv.lineSequence().mapNotNull { line ->
        val cells = line.trim().split('\t')
        if (cells.size != 4) return@mapNotNull null
        val license = OssLicense.byId(cells[3]) ?: return@mapNotNull null
        OpenSourceComponent(cells[0], cells[1], cells[2], license)
    }.toList()

    /** [components] by project (a family's components share one license), in the order the sheet lists them. */
    fun families(components: List<OpenSourceComponent>): List<OpenSourceFamily> =
        components.groupBy { familyOf(it) to it.license }
            .map { (key, list) -> OpenSourceFamily(key.first, key.second, list.sortedWith(compareBy({ it.group }, { it.artifact }))) }
            .sortedWith(compareBy({ ORDER.indexOf(it.name).let { i -> if (i < 0) ORDER.size else i } }, { it.name }))

    /** The family [component] belongs to. */
    fun familyOf(component: OpenSourceComponent): String {
        val g = component.group
        return when {
            component.license == OssLicense.PICO_SDK -> PICO_SDK
            g.startsWith("com.pico.") -> PICO_COMPOSE
            g == "androidx.media3" -> MEDIA3
            g.startsWith("androidx.compose") -> COMPOSE
            g.startsWith("androidx.") -> JETPACK
            g.startsWith("org.jetbrains") -> KOTLIN
            g == "com.google.guava" -> GUAVA
            g == "com.airbnb.android" -> LOTTIE
            g == "org.checkerframework" -> CHECKER
            g in ANNOTATION_GROUPS -> ANNOTATIONS
            else -> g
        }
    }

    companion object {
        const val PICO_SDK = "PICO Spatial SDK"
        const val PICO_COMPOSE = "PICO Compose UI"
        const val MEDIA3 = "AndroidX Media3"
        const val COMPOSE = "AndroidX Compose"
        const val JETPACK = "AndroidX Jetpack"
        const val KOTLIN = "Kotlin / kotlinx"
        const val GUAVA = "Guava"
        const val LOTTIE = "Lottie"
        const val ANNOTATIONS = "Error Prone / J2ObjC / JSpecify / JSR-305 annotations"
        const val CHECKER = "Checker Framework qualifiers"
        private val ANNOTATION_GROUPS = setOf("com.google.errorprone", "com.google.j2objc", "org.jspecify", "com.google.code.findbugs")
        private val ORDER = listOf(PICO_SDK, PICO_COMPOSE, MEDIA3, COMPOSE, JETPACK, KOTLIN, GUAVA, LOTTIE, ANNOTATIONS, CHECKER)
    }
}
