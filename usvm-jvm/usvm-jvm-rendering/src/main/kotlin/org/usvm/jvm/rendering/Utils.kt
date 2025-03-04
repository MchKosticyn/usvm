package org.usvm.jvm.rendering

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.type.ClassOrInterfaceType

object Utils {
    fun parseClassOrInterface(name: String): ClassOrInterfaceType = StaticJavaParser.parseClassOrInterfaceType(name)
}