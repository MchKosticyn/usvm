package org.usvm.jvm.rendering.unsafeRenderer

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

open class JcUnsafeUtilsRenderer(
    protected val blockRenderer: JcUnsafeTestBlockRenderer
) {

    protected val importManager: JcUnsafeImportManager get() = blockRenderer.importManager

    private val utilsName: NameExpr by lazy {
        NameExpr(
            if (importManager.add(ReflectionUtilName.USVM))
                ReflectionUtilName.USVM_SIMPLE
            else
                ReflectionUtilName.USVM
        )
    }

    open fun renderCtorCall(
        ctor: JcMethod,
        type: JcClassType,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        blockRenderer.addThrownException("java.lang.Throwable")
        importManager.useUsvmReflectionMethod("callConstructor")
        val allArgs = listOf(blockRenderer.renderClassExpression(type), StringLiteralExpr(ctor.jcdbSignature)) + args
        return MethodCallExpr(
            utilsName,
            NodeList(blockRenderer.renderClass(type)),
            "callConstructor",
            NodeList(allArgs),
        )
    }

    open fun renderInstanceMethodCall(
        method: JcMethod,
        instance: Expression,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        blockRenderer.addThrownException("java.lang.Throwable")
        importManager.useUsvmReflectionMethod("callMethod")
        val allArgs = listOf(instance, StringLiteralExpr(method.jcdbSignature)) + args
        return MethodCallExpr(
            utilsName,
            listTypeArgsFor(method),
            "callMethod",
            NodeList(allArgs),
        )
    }

    open fun renderStaticMethodCall(
        method: JcMethod,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        blockRenderer.addThrownException("java.lang.Throwable")
        importManager.useUsvmReflectionMethod("callStaticMethod")
        val enclosingClass = method.enclosingClass
        val allArgs =
            listOf(blockRenderer.renderClassExpression(enclosingClass), StringLiteralExpr(method.jcdbSignature)) + args
        return MethodCallExpr(
            utilsName,
            listTypeArgsFor(method),
            "callStaticMethod",
            NodeList(allArgs),
        )
    }

    open fun renderGetInstanceField(instance: Expression, field: JcField): Expression {
        importManager.useUsvmReflectionMethod("getStaticFieldValue")
        return MethodCallExpr(
            utilsName,
            listTypeArgsFor(field),
            "getStaticFieldValue",
            NodeList(blockRenderer.renderClassExpression(field.enclosingClass), StringLiteralExpr(field.name)),
        )
    }

    open fun renderGetStaticField(field: JcField): Expression {
        importManager.useUsvmReflectionMethod("getStaticFieldValue")
        return MethodCallExpr(
            utilsName,
            listTypeArgsFor(field),
            "getStaticFieldValue",
            NodeList(blockRenderer.renderClassExpression(field.enclosingClass), StringLiteralExpr(field.name)),
        )
    }

    open fun renderSetInstanceField(instance: Expression, field: JcField, value: Expression): Expression {
        importManager.useUsvmReflectionMethod("setFieldValue")
        return MethodCallExpr(
            utilsName,
            "setFieldValue",
            NodeList(instance, StringLiteralExpr(field.name), value),
        )
    }

    open fun renderSetStaticField(field: JcField, value: Expression): Expression {
        importManager.useUsvmReflectionMethod("setStaticFieldValue")
        return MethodCallExpr(
            utilsName,
            "setStaticFieldValue",
            NodeList(blockRenderer.renderClassExpression(field.enclosingClass), StringLiteralExpr(field.name), value),
        )
    }

    open fun renderAllocateInstance(clazz: JcClassOrInterface): Expression {
        blockRenderer.addThrownException("java.lang.InstantiationException")
        importManager.useUsvmReflectionMethod("allocateInstance")
        return MethodCallExpr(
            utilsName,
            NodeList(blockRenderer.renderClass(clazz)),
            "allocateInstance",
            NodeList(blockRenderer.renderClassExpression(clazz)),
        )
    }

    private fun listTypeArgsFor(type: JcType): NodeList<Type>? {
        val cp = type.classpath
        return when (type) {
            is JcRefType -> NodeList(blockRenderer.renderType(type))
            cp.void, cp.nullType -> null
            else -> NodeList(blockRenderer.renderType(type.autoboxIfNeeded()))
        }
    }

    protected fun listTypeArgsFor(method: JcMethod): NodeList<Type>? {
        val cp = method.enclosingClass.classpath
        val resultTypeName = method.returnType.typeName
        val resultType = cp.findType(resultTypeName)
        return listTypeArgsFor(resultType)
    }

    protected fun listTypeArgsFor(field: JcField): NodeList<Type>? {
        return listTypeArgsFor(fieldType(field))
    }

    protected fun fieldType(field: JcField): JcType {
        val cp = field.enclosingClass.classpath
        val fieldTypeName = field.type.typeName
        return cp.findType(fieldTypeName)
    }
}
