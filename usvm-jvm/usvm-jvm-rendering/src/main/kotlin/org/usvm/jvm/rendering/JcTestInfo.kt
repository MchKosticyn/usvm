package org.usvm.jvm.rendering

import org.jacodb.api.jvm.JcMethod

interface JcTestInfo {
    val method: JcMethod
}

data class JcUnitTestInfo(override val method: JcMethod) : JcTestInfo

data class JcSpringTestInfo(override val method: JcMethod) : JcTestInfo
