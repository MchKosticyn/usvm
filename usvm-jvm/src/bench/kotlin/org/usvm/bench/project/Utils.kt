package org.usvm.bench.project

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.findMethodOrNull

object Utils {
    const val projectFileName = "project.json"
    const val persistenceFileName = "jacodb"
    const val classesDirName = "classes"
}

fun JcClasspath.getMethod(methodId: MethodId): JcMethod? =
    findClassOrNull(methodId.className)?.findMethodOrNull(methodId.methodName, methodId.descriptor)
