package org.usvm.test.internal

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.MethodNotFoundException
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findMethodOrNull
import org.jacodb.api.jvm.ext.toType

fun JcClasspath.findJcMethod(cName: String, mName: String): JcTypedMethod =
    this.findClass(cName).toType().findMethodOrNull { it.name == mName }
        ?: throw MethodNotFoundException("$mName not found")
fun JcMethod.toTyped(): JcTypedMethod =
    this.enclosingClass.toType().declaredMethods.first { typed -> typed.method == this }