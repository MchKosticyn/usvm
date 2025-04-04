package org.usvm.jvm.rendering

internal val String.normalized: String
    get() = this.replace("<", "").replace(">", "").replace("$", "")

internal val String.decapitalized: String
    get() = replaceFirstChar { it.lowercase() }

internal val String.capitalized: String
    get() = replaceFirstChar { it.titlecase() }
