package com.example.prismstudio.p40b43a88.domain.model

/** The license a component ships under; [id] is how libraries.tsv names it. */
enum class OssLicense(val id: String, val label: String, val hasText: Boolean) {
    APACHE_2("apache-2.0", "Apache License 2.0", hasText = true),
    MIT("mit", "MIT License", hasText = true),
    /** PICO's own SDK modules: used under the PICO developer agreement, whose text is PICO's to publish. */
    PICO_SDK("pico-sdk", "PICO 开发者协议", hasText = false),
    ;

    companion object {
        fun byId(id: String): OssLicense? = entries.firstOrNull { it.id == id }
    }
}

/** One library the release build ships (group:artifact:version). */
data class OpenSourceComponent(val group: String, val artifact: String, val version: String, val license: OssLicense)

/** Libraries of one project shown together on the About sheet. */
data class OpenSourceFamily(val name: String, val license: OssLicense, val components: List<OpenSourceComponent>)
