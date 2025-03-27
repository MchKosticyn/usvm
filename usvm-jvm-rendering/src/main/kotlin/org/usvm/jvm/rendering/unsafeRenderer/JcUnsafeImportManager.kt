package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.SimpleName
import org.usvm.jvm.rendering.baseRenderer.JcImportManager

object ReflectionUtilName {
    const val SPRING = "org.springframework.test.util.ReflectionTestUtils"
    const val USVM = "org.usvm.jvm.rendering.ReflectionUtils"
    const val USVM_SIMPLE = "ReflectionUtils"

    fun isValid(name: String) = name in listOf(SPRING, USVM)
}

class JcUnsafeImportManager(
    reflectionUtilsFullName: String,
    cu: CompilationUnit? = null,
    private val shouldInlineUsvmUtils: Boolean = false
) : JcImportManager(cu) {

    init {
        check(ReflectionUtilName.isValid(reflectionUtilsFullName))
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

    fun useUsvmMethod(name: String) {
        usvmUtilMethodCollector.add(name)
    }

    fun extractUsedUsvmUtilMethods(): Set<String> {
        val usedMethodsTransitive = usvmUtilMethodCollector.flatMap { method ->
            usvmUtilRequiredMethodsMapping[method]!! + method
        }

        return usedMethodsTransitive.toSet()
    }
}
