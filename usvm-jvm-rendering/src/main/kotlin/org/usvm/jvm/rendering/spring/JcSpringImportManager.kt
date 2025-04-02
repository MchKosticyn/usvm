package org.usvm.jvm.rendering.spring

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.SimpleName
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeImportManager
import org.usvm.jvm.rendering.unsafeRenderer.ReflectionUtilName

class JcSpringImportManager(
    cu: CompilationUnit? = null,
    inlineUsvmUtils: Boolean = true
) : JcUnsafeImportManager(cu, inlineUsvmUtils) {

    var springUtilsImported = false
        get private set

    val springUtilsName: SimpleName by lazy {
        springUtilsImported = true
        if (add(ReflectionUtilName.USVM))
            SimpleName(ReflectionUtilName.USVM_SIMPLE)
        else SimpleName(ReflectionUtilName.USVM)
    }
}
