package org.usvm.jvm.rendering.testRenderer

import org.jacodb.api.jvm.JcMethod

abstract class JcTestInfo {
    abstract val method: JcMethod

    override fun hashCode(): Int {
        return method.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JcTestInfo) return false
        return method == other.method
    }

}

data class JcUnitTestInfo(override val method: JcMethod, val throws: Boolean) : JcTestInfo()
