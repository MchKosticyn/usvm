package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.SimpleName
import org.usvm.jvm.rendering.baseRenderer.JcImportManager

class JcUnsafeImportManager(
    reflectionUtilsFullName: String,
    cu: CompilationUnit? = null
): JcImportManager(cu) {

    init {
        check(reflectionUtilsFullName.endsWith("ReflectionUtils"))
    }

    private var reflectionUtilsImported = false

    val reflectionUtilsName: SimpleName by lazy {
        reflectionUtilsImported = true
        if (add(reflectionUtilsFullName))
            SimpleName("ReflectionUtils")
        else SimpleName(reflectionUtilsFullName)
    }

    val needReflectionUtils: Boolean
        get() = reflectionUtilsImported
}
