package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.ast.CompilationUnit
import org.usvm.jvm.rendering.ReflectionUtilsInlineStrategy
import org.usvm.jvm.rendering.baseRenderer.JcImportManager

open class JcUnsafeImportManager(
    cu: CompilationUnit? = null,
    val reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy = ReflectionUtilsInlineStrategy.NoInline()
) : JcImportManager(cu) {
    private val usvmUtilMethodCollector: MutableSet<String> = mutableSetOf()

    private val usvmUtilRequiredMethodsMapping = mapOf(
        "callConstructor" to listOf("getConstructor", "methodSignature", "parameterTypesSignature"),
        "callMethod" to listOf("getMethod", "getInstanceMethods", "methodSignature", "parameterTypesSignature"),
        "callStaticMethod" to listOf(
            "callMethod",
            "getMethod",
            "getStaticMethod",
            "getStaticMethods",
            "getInstanceMethods",
            "methodSignature",
            "parameterTypesSignature"
        ),
        "getStaticFieldValue" to listOf(
            "getStaticField",
            "getFieldValue",
            "getOffsetOf",
            "isStatic",
            "getStaticFields"
        ),
        "getFieldValue" to listOf("getOffsetOf", "isStatic"),
        "setStaticFieldValue" to listOf(
            "getStaticField",
            "getStaticFields",
            "setFieldValue",
            "getOffsetOf",
            "isStatic"
        ),
        "setFieldValue" to listOf("getField", "getInstanceFields", "getOffsetOf", "isStatic"),
        "allocateInstance" to listOf()
    )

    fun useUsvmReflectionMethod(name: String) {
        usvmUtilMethodCollector.add(name)
    }

    fun extractUsedUsvmUtilMethods(): Set<String> {
        val usedMethodsTransitive = usvmUtilMethodCollector.flatMap { method ->
            usvmUtilRequiredMethodsMapping[method]!! + method
        }

        return usedMethodsTransitive.toSet()
    }
}
