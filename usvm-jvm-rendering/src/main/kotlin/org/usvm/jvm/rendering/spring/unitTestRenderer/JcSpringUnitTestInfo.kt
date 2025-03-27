package org.usvm.jvm.rendering.spring.unitTestRenderer

import org.jacodb.api.jvm.JcMethod
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestInfo

open class JcSpringUnitTestInfo(method: JcMethod, isExceptional: Boolean): JcUnsafeTestInfo(method, isExceptional)