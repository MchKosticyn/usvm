package org.usvm.jvm.rendering.spring.unitTestRenderer

import org.jacodb.api.jvm.JcMethod
import org.usvm.jvm.rendering.testRenderer.JcTestInfo

data class JcSpringUnitTestInfo(override val method: JcMethod) : JcTestInfo()

