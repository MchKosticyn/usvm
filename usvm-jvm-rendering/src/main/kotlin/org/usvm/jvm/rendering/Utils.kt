package org.usvm.jvm.rendering

internal val String.normalized: String
    get() = this.replace("<", "").replace(">", "").replace("$", "")
