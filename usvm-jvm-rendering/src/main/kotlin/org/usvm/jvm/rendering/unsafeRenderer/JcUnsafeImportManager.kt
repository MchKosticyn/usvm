package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.SimpleName
import org.usvm.jvm.rendering.baseRenderer.JcImportManager

enum class ReflectionUtilNames(val fullName: String) {
    SPRING("org.springframework.test.util.ReflectionTestUtils"),
    USVM("org.usvm.jvm.rendering.ReflectionUtils");

    companion object {
        fun isValidUtilName(name: String) =
            ReflectionUtilNames.entries.any {
                it.fullName == name
            }

    }
}

class JcUnsafeImportManager(
    reflectionUtilsFullName: String,
    cu: CompilationUnit? = null
): JcImportManager(cu) {

    init {
        check(ReflectionUtilNames.isValidUtilName(reflectionUtilsFullName))
    }

    private var reflectionUtilsImported = false

    val reflectionUtilsName: SimpleName by lazy {
        reflectionUtilsImported = true
        if (add(reflectionUtilsFullName))
            SimpleName(reflectionUtilsFullName.split(".").last())
        else SimpleName(reflectionUtilsFullName)
    }

    val needReflectionUtils: Boolean
        get() = reflectionUtilsImported
}
