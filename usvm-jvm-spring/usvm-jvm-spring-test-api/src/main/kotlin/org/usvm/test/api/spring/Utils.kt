package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.MethodNotFoundException
import org.jacodb.api.jvm.ext.superClasses

internal fun JcClasspath.findJcMethod(cName: String, mName: String, parametersTypeNames: List<String>? = null): JcMethod {
    val enclosingClass = this.findClassOrNull(cName) ?: error("Class $cName not found in classpath")
    val allMethods = (listOf(enclosingClass) + enclosingClass.superClasses).flatMap { it.declaredMethods }
    for (method in allMethods) {
        if (method.name != mName)
            continue

        if (parametersTypeNames == null)
            return method

        if (parametersTypeNames.size != method.parameters.size)
            continue

        if (parametersTypeNames.zip(method.parameters).all { (t, p) -> p.type.typeName == t })
            return method
    }

    throw MethodNotFoundException("$mName not found")
}

internal val String.decapitalized: String
    get() = replaceFirstChar { it.lowercase() }

internal val String.capitalized: String
    get() = replaceFirstChar { it.titlecase() }
