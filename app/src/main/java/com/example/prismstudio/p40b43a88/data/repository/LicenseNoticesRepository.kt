package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.OssLicense

/** The open-source notices bundled with the app (app/src/main/resources/licenses, see devtools/release/gen_licenses.py). */
interface LicenseNoticesRepository {
    /** libraries.tsv: the components the release build ships. */
    fun libraries(): String

    /** The full text of [license], or null when the app carries none (PICO's agreement is PICO's to publish). */
    fun text(license: OssLicense): String?

    /** The bundled files, read from the app's Java resources (as the interface languages are). */
    object Bundled : LicenseNoticesRepository {
        private fun read(name: String): String? =
            LicenseNoticesRepository::class.java.getResourceAsStream("/licenses/$name")?.bufferedReader()?.use { it.readText() }

        override fun libraries(): String = read("libraries.tsv").orEmpty()

        override fun text(license: OssLicense): String? = when (license) {
            OssLicense.APACHE_2 -> read("apache-2.0.txt")
            OssLicense.MIT -> read("mit-checker-qual.txt")
            OssLicense.PICO_SDK -> null
        }
    }
}
