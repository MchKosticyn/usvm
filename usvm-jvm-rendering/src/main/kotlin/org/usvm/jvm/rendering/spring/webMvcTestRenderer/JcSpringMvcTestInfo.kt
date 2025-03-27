package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.toType
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestInfo

class JcSpringMvcTestInfo(method: JcMethod, isExceptional: Boolean) : JcSpringUnitTestInfo(method, isExceptional) {
    val controller by lazy { method.enclosingClass.toType() }

    override fun hashCode(): Int {
        return method.enclosingClass.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JcSpringMvcTestInfo) return false
        return controller == other.controller
    }
}
