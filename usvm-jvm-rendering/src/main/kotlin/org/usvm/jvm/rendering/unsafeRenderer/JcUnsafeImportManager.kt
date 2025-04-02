package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.SimpleName
import org.usvm.jvm.rendering.baseRenderer.JcImportManager

open class JcUnsafeImportManager(
    cu: CompilationUnit? = null,
    private val shouldInlineUsvmUtils: Boolean = false
) : JcImportManager(cu) {

    init {
        check(ReflectionUtilName.isValidFullName(ReflectionUtilName.USVM))
    }

    var usvmUtilsImported = false
        get private set

    val usvmUtilsName: SimpleName by lazy {
        usvmUtilsImported = true
        if (add(ReflectionUtilName.USVM))
            SimpleName(ReflectionUtilName.USVM_SIMPLE)
        else SimpleName(ReflectionUtilName.USVM)
    }

    override fun add(
        packageName: String,
        simpleName: String,
        packages: MutableSet<String>,
        names: MutableSet<String>
    ): Boolean {
        val isUsvmUtil = "${packageName}.${simpleName}" == ReflectionUtilName.USVM

        if (shouldInlineUsvmUtils && isUsvmUtil)
            return true

        return super.add(packageName, simpleName, packages, names)
    }

    private val usvmUtilMethodCollector: MutableSet<String> = mutableSetOf()

    private val usvmUtilRequiredMethodsMapping = mapOf<String, List<String>>(
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
