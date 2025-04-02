package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.type.ReferenceType
import com.github.javaparser.ast.type.Type
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
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.testRenderer.JcTestBlockRenderer
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestExpression
import java.util.IdentityHashMap
import org.jacodb.api.jvm.JcClasspath

open class JcUnsafeTestBlockRenderer protected constructor(
    override val methodRenderer: JcUnsafeTestRenderer,
    override val importManager: JcUnsafeImportManager,
    identifiersManager: JcIdentifiersManager,
    cp: JcClasspath,
    shouldDeclareVar: Set<UTestExpression>,
    exprCache: IdentityHashMap<UTestExpression, Expression>,
    thrownExceptions: HashSet<ReferenceType>
) : JcTestBlockRenderer(
    methodRenderer,
    importManager,
    identifiersManager,
    cp,
    shouldDeclareVar,
    exprCache,
    thrownExceptions
) {

    constructor(
        methodRenderer: JcUnsafeTestRenderer,
        importManager: JcUnsafeImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath,
        shouldDeclareVar: Set<UTestExpression>
    ) : this(methodRenderer, importManager, identifiersManager, cp, shouldDeclareVar, IdentityHashMap(), HashSet())

    override fun newInnerBlock(): JcUnsafeTestBlockRenderer {
        return JcUnsafeTestBlockRenderer(
            methodRenderer,
            importManager,
            JcIdentifiersManager(identifiersManager),
            cp,
            shouldDeclareVar,
            IdentityHashMap(exprCache),
            thrownExceptions
        )
    }

    private val utilsName: NameExpr by lazy {
        NameExpr(importManager.usvmUtilsName)
    }

    private fun typeArgsForType(type: JcType): NodeList<Type>? {
        val cp = type.classpath
        return when (type) {
            is JcRefType -> NodeList(renderType(type))
            cp.void, cp.nullType -> null
            else -> NodeList(renderType(type.autoboxIfNeeded()))
        }
    }

    //region Private Methods

    override fun renderPrivateCtorCall(ctor: JcMethod, type: JcClassType, args: List<Expression>): Expression {
        addThrownException("java.lang.Throwable", ctor.enclosingClass.classpath)
        importManager.useUsvmReflectionMethod("callConstructor")
        val allArgs = listOf(renderClassExpression(type), StringLiteralExpr(ctor.jcdbSignature)) + args
        return MethodCallExpr(
            utilsName,
            NodeList(renderClass(type)),
            "callConstructor",
            NodeList(allArgs),
        )
    }

    private val JcMethod.resultType: JcType
        get() = enclosingClass.classpath.findType(returnType.typeName)

    protected fun typeArgsForPrivateCall(method: JcMethod): NodeList<Type>? {
        return typeArgsForType(method.resultType)
    }

    override fun renderPrivateMethodCall(method: JcMethod, instance: Expression, args: List<Expression>): Expression {
        addThrownException("java.lang.Throwable", method.enclosingClass.classpath)
        importManager.useUsvmReflectionMethod("callMethod")
        val allArgs = listOf(instance, StringLiteralExpr(method.jcdbSignature)) + args
        return MethodCallExpr(
            utilsName,
            typeArgsForPrivateCall(method),
            "callMethod",
            NodeList(allArgs),
        )
    }

    override fun renderPrivateStaticMethodCall(method: JcMethod, args: List<Expression>): Expression {
        addThrownException("java.lang.Throwable", method.enclosingClass.classpath)
        importManager.useUsvmReflectionMethod("callStaticMethod")
        val enclosingClass = method.enclosingClass
        val allArgs = listOf(renderClassExpression(enclosingClass), StringLiteralExpr(method.jcdbSignature)) + args
        return MethodCallExpr(
            utilsName,
            typeArgsForPrivateCall(method),
            "callStaticMethod",
            NodeList(allArgs),
        )
    }

    //endregion

    //region Private Fields

    protected val JcField.fieldType: JcType
        get() = enclosingClass.classpath.findType(type.typeName)

    protected fun typeArgsForPrivateFieldGet(field: JcField): NodeList<Type>? {
        return typeArgsForType(field.fieldType)
    }

    override fun renderGetPrivateStaticField(field: JcField): Expression {
        importManager.useUsvmReflectionMethod("getStaticFieldValue")
        return MethodCallExpr(
            utilsName,
            typeArgsForPrivateFieldGet(field),
            "getStaticFieldValue",
            NodeList(renderClassExpression(field.enclosingClass), StringLiteralExpr(field.name)),
        )
    }

    override fun renderGetPrivateField(instance: Expression, field: JcField): Expression {
        importManager.useUsvmReflectionMethod("getFieldValue")
        return MethodCallExpr(
            utilsName,
            typeArgsForPrivateFieldGet(field),
            "getFieldValue",
            NodeList(instance, StringLiteralExpr(field.name)),
        )
    }

    override fun renderSetPrivateStaticField(field: JcField, value: Expression): Expression {
        importManager.useUsvmReflectionMethod("setStaticFieldValue")
        return MethodCallExpr(
            utilsName,
            "setStaticFieldValue",
            NodeList(renderClassExpression(field.enclosingClass), StringLiteralExpr(field.name), value),
        )
    }

    override fun renderSetPrivateField(instance: Expression, field: JcField, value: Expression): Expression {
        importManager.useUsvmReflectionMethod("setFieldValue")
        return MethodCallExpr(
            utilsName,
            "setFieldValue",
            NodeList(instance, StringLiteralExpr(field.name), value),
        )
    }

    //endregion

    //region Allocation

    override fun renderAllocateMemoryCall(expr: UTestAllocateMemoryCall): Expression {
        addThrownException("java.lang.InstantiationException", expr.clazz.classpath)
        importManager.useUsvmReflectionMethod("allocateInstance")
        return MethodCallExpr(
            utilsName,
            NodeList(renderClass(expr.clazz)),
            "allocateInstance",
            NodeList(renderClassExpression(expr.clazz)),
        )
    }

    //endregion
}
