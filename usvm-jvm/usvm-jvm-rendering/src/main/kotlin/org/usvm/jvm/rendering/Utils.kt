package org.usvm.jvm.rendering

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.ReferenceType
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType

object Utils {
    fun parseClassOrInterface(name: String): ClassOrInterfaceType = StaticJavaParser.parseClassOrInterfaceType(name)
    fun qualifiedName(type: JcClassType): String = qualifiedName(type.jcClass)
    fun qualifiedName(clazz: JcClassOrInterface): String = clazz.name.replace("$", ".")
    fun MethodDeclaration.tryAddThrownException(exceptionType: ReferenceType) {
        if (!thrownExceptions.contains(exceptionType)) addThrownException(exceptionType)
    }
}