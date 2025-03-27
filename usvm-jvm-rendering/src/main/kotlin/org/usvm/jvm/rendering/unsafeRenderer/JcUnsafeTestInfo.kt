package org.usvm.jvm.rendering.unsafeRenderer

import org.jacodb.api.jvm.JcMethod
import org.usvm.jvm.rendering.testRenderer.JcTestInfo

open class JcUnsafeTestInfo(method: JcMethod, isExceptional: Boolean): JcTestInfo(method, isExceptional)