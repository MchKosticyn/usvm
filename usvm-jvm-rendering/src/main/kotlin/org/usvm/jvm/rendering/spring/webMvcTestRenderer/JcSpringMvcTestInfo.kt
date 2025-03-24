package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.usvm.jvm.rendering.testRenderer.JcTestInfo

data class JcSpringMvcTestInfo(override val method: JcMethod, val controller: JcClassType) : JcTestInfo() {
    override fun hashCode(): Int {
        return controller.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JcSpringMvcTestInfo) return false
        return controller == other.controller
    }
}
