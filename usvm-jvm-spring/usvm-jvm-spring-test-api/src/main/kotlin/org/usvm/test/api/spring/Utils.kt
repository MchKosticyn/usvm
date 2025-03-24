package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.toType

internal val JcClasspath.stringType: JcType
    get() = findClassOrNull("java.lang.String")!!.toType()
