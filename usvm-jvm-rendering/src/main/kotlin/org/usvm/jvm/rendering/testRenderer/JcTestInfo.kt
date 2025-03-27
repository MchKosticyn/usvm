package org.usvm.jvm.rendering.testRenderer

import org.jacodb.api.jvm.JcMethod

abstract class JcTestInfo(val method: JcMethod, val isExceptional: Boolean? = null) {
    val namePrefix: String get() = "${method.name}$isExceptionalSuffix".normalized()

    private val isExceptionalSuffix: String
        get() = when (isExceptional) {
            true -> "Exceptional"
            else -> ""
        }

    override fun hashCode(): Int {
        return method.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JcTestInfo) return false
        return method == other.method
    }

    private fun String.normalized(): String =
        this.replace("<", "").replace(">", "").replace("$", "")
}