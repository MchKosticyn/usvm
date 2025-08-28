package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.type.Type
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.autoboxIfNeeded
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.jcdbSignature
import org.jacodb.api.jvm.ext.nullType
import org.jacodb.api.jvm.ext.void
import org.usvm.jvm.rendering.ReflectionUtilsInlineStrategy
import org.usvm.jvm.rendering.baseRenderer.JcImportManager

open class JcUnsafeUtilsRenderer(
    protected val importManager: JcImportManager,
    protected val reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy
) {

    companion object {
        private const val USVM = "org.usvm.jvm.rendering.ReflectionUtils"
        private const val USVM_SIMPLE = "ReflectionUtils"
    }

    private val utilsName: NameExpr by lazy {
        NameExpr(
            if (reflectionUtilsInlineStrategy.inTestClassFile || importManager.add(USVM))
                USVM_SIMPLE
            else
                USVM
        )
    }

    // TODO: rewrite properly after inline strategy redesign
    fun addReflectionUtils(importManager: JcImportManager, cu: CompilationUnit): CompilationUnit {
        return reflectionUtilsInlineStrategy.addReflectionUtils(importManager, cu)
    }

    open fun renderCtorCall(
        blockRenderer: JcUnsafeTestBlockRenderer,
        ctor: JcMethod,
        type: JcClassType,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        blockRenderer.addThrownException("java.lang.Throwable")
        reflectionUtilsInlineStrategy.useUsvmReflectionMethod("callConstructor")
        val allArgs = listOf(blockRenderer.renderClassExpression(type), StringLiteralExpr(ctor.jcdbSignature)) + args
        return MethodCallExpr(
            utilsName,
            NodeList(blockRenderer.renderClass(type)),
            "callConstructor",
            NodeList(allArgs),
        )
    }

    open fun renderInstanceMethodCall(
        blockRenderer: JcUnsafeTestBlockRenderer,
        method: JcMethod,
        instance: Expression,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        blockRenderer.addThrownException("java.lang.Throwable")
        reflectionUtilsInlineStrategy.useUsvmReflectionMethod("callMethod")
        val allArgs = listOf(instance, StringLiteralExpr(method.jcdbSignature)) + args
        return MethodCallExpr(
            utilsName,
            listTypeArgsFor(method, blockRenderer),
            "callMethod",
            NodeList(allArgs),
        )
    }

    open fun renderStaticMethodCall(
        blockRenderer: JcUnsafeTestBlockRenderer,
        method: JcMethod,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        blockRenderer.addThrownException("java.lang.Throwable")
        reflectionUtilsInlineStrategy.useUsvmReflectionMethod("callStaticMethod")
        val enclosingClass = method.enclosingClass
        val allArgs =
            listOf(blockRenderer.renderClassExpression(enclosingClass), StringLiteralExpr(method.jcdbSignature)) + args
        return MethodCallExpr(
            utilsName,
            listTypeArgsFor(method, blockRenderer),
            "callStaticMethod",
            NodeList(allArgs),
        )
    }

    open fun renderGetInstanceField(
        blockRenderer: JcUnsafeTestBlockRenderer,
        instance: Expression,
        field: JcField
    ): Expression {
        reflectionUtilsInlineStrategy.useUsvmReflectionMethod("getStaticFieldValue")
        return MethodCallExpr(
            utilsName,
            listTypeArgsFor(field, blockRenderer),
            "getStaticFieldValue",
            NodeList(blockRenderer.renderClassExpression(field.enclosingClass), StringLiteralExpr(field.name)),
        )
    }

    open fun renderGetStaticField(blockRenderer: JcUnsafeTestBlockRenderer, field: JcField): Expression {
        reflectionUtilsInlineStrategy.useUsvmReflectionMethod("getStaticFieldValue")
        return MethodCallExpr(
            utilsName,
            listTypeArgsFor(field, blockRenderer),
            "getStaticFieldValue",
            NodeList(blockRenderer.renderClassExpression(field.enclosingClass), StringLiteralExpr(field.name)),
        )
    }

    open fun renderSetInstanceField(
        blockRenderer: JcUnsafeTestBlockRenderer,
        instance: Expression,
        field: JcField,
        value: Expression
    ): Expression {
        reflectionUtilsInlineStrategy.useUsvmReflectionMethod("setFieldValue")
        return MethodCallExpr(
            utilsName,
            "setFieldValue",
            NodeList(instance, StringLiteralExpr(field.name), value),
        )
    }

    open fun renderSetStaticField(
        blockRenderer: JcUnsafeTestBlockRenderer,
        field: JcField,
        value: Expression
    ): Expression {
        reflectionUtilsInlineStrategy.useUsvmReflectionMethod("setStaticFieldValue")
        return MethodCallExpr(
            utilsName,
            "setStaticFieldValue",
            NodeList(blockRenderer.renderClassExpression(field.enclosingClass), StringLiteralExpr(field.name), value),
        )
    }

    open fun renderAllocateInstance(blockRenderer: JcUnsafeTestBlockRenderer, clazz: JcClassOrInterface): Expression {
        blockRenderer.addThrownException("java.lang.InstantiationException")
        reflectionUtilsInlineStrategy.useUsvmReflectionMethod("allocateInstance")
        return MethodCallExpr(
            utilsName,
            NodeList(blockRenderer.renderClass(clazz)),
            "allocateInstance",
            NodeList(blockRenderer.renderClassExpression(clazz)),
        )
    }

    private fun listTypeArgsFor(type: JcType, blockRenderer: JcUnsafeTestBlockRenderer): NodeList<Type>? {
        val cp = type.classpath
        return when (type) {
            is JcRefType -> NodeList(blockRenderer.renderType(type))
            cp.void, cp.nullType -> null
            else -> NodeList(blockRenderer.renderType(type.autoboxIfNeeded()))
        }
    }

    protected fun listTypeArgsFor(method: JcMethod, blockRenderer: JcUnsafeTestBlockRenderer,): NodeList<Type>? {
        val cp = method.enclosingClass.classpath
        val resultTypeName = method.returnType.typeName
        val resultType = cp.findType(resultTypeName)
        return listTypeArgsFor(resultType, blockRenderer)
    }

    protected fun listTypeArgsFor(field: JcField, blockRenderer: JcUnsafeTestBlockRenderer): NodeList<Type>? {
        return listTypeArgsFor(fieldType(field), blockRenderer)
    }

    protected fun fieldType(field: JcField): JcType {
        val cp = field.enclosingClass.classpath
        val fieldTypeName = field.type.typeName
        return cp.findType(fieldTypeName)
    }
}
