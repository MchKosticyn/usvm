package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.MethodNotFoundException
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findMethodOrNull
import org.jacodb.api.jvm.ext.toType

fun JcClasspath.findJcMethod(cName: String, mName: String, parametersTypeNames: List<String>? = null): JcTypedMethod {
    return this.findClass(cName)
        .toType()
        .findMethodOrNull { method ->
            method.name == mName && (
                    parametersTypeNames == null
                            || parametersTypeNames.size == method.parameters.size
                            && parametersTypeNames.zip(method.parameters).all { (t, p) -> p.type.typeName == t }
                    )
        } ?: throw MethodNotFoundException("$mName not found")
}
