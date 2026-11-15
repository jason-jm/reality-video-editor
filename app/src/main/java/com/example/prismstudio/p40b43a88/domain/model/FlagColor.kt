package com.example.prismstudio.p40b43a88.domain.model

/**
 * Resolve's flags: marks an editor puts on source media while reviewing (to fix, approved, for the client …). A flag
 * belongs to the media, so every clip made from it shows it, and media can carry several at once. Resolve's flags
 * share the marker palette; these are its first seven.
 */
enum class FlagColor(val label: String) {
    BLUE("蓝"),
    CYAN("青"),
    GREEN("绿"),
    YELLOW("黄"),
    RED("红"),
    PINK("粉"),
    PURPLE("紫"),
}
