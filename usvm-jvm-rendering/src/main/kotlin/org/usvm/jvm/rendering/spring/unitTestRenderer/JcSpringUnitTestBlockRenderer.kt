package org.usvm.jvm.rendering.spring.unitTestRenderer

import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.CastExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.type.ReferenceType
import java.util.*
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.jcdbSignature
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeImportManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestBlockRenderer
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestExpression

open class JcSpringUnitTestBlockRenderer protected constructor(
    override val importManager: JcUnsafeImportManager,
    identifiersManager: JcIdentifiersManager,
    shouldDeclareVar: Set<UTestExpression>,
    exprCache: IdentityHashMap<UTestExpression, Expression>,
    thrownExceptions: HashSet<ReferenceType>
) : JcUnsafeTestBlockRenderer(importManager, identifiersManager, shouldDeclareVar, exprCache, thrownExceptions) {

    constructor(
        importManager: JcUnsafeImportManager,
        identifiersManager: JcIdentifiersManager,
        shouldDeclareVar: Set<UTestExpression>
    ) : this(importManager, identifiersManager, shouldDeclareVar, IdentityHashMap(), HashSet())

    override fun newInnerBlock(): JcSpringUnitTestBlockRenderer {
        return JcSpringUnitTestBlockRenderer(
            importManager,
            JcIdentifiersManager(identifiersManager),
            shouldDeclareVar,
            IdentityHashMap(exprCache),
            thrownExceptions
        )
    }

    //region Private Methods

    override fun renderPrivateCtorCall(ctor: JcMethod, type: JcClassType, args: List<Expression>): Expression {
        addThrownException("Throwable")
        val allArgs = listOf(renderClassExpression(type), StringLiteralExpr(ctor.jcdbSignature)) + args
        return MethodCallExpr(
            utilsName,
            NodeList(renderClass(type)),
            "callConstructor",
            NodeList(allArgs),
        )
    }

    override fun renderPrivateMethodCall(method: JcMethod, instance: Expression, args: List<Expression>): Expression {
        addThrownException("Throwable")
        val allArgs = listOf(instance, StringLiteralExpr(method.name)) + args
        return MethodCallExpr(
            utilsName,
            typeArgsForPrivateCall(method),
            "invokeMethod",
            NodeList(allArgs),
        )
    }

    override fun renderPrivateStaticMethodCall(method: JcMethod, args: List<Expression>): Expression {
        addThrownException("Throwable")
        val enclosingClass = method.enclosingClass
        val allArgs = listOf(renderClassExpression(enclosingClass), StringLiteralExpr(method.name)) + args
        return MethodCallExpr(
            utilsName,
            typeArgsForPrivateCall(method),
            "invokeMethod",
            NodeList(allArgs),
        )
    }

    //endregion

    //region Private Fields

    override fun renderGetPrivateStaticField(field: JcField): Expression {
        val call = MethodCallExpr(
            utilsName,
            "getField",
            NodeList(renderClassExpression(field.enclosingClass), StringLiteralExpr(field.name)),
        )
        return CastExpr(renderType(field.fieldType), call)
    }

    override fun renderGetPrivateField(instance: Expression, field: JcField): Expression {
        val call = MethodCallExpr(
            utilsName,
            "getField",
            NodeList(instance, StringLiteralExpr(field.name))
        )
        return CastExpr(renderType (field.fieldType), call)
    }

    override fun renderSetPrivateStaticField(field: JcField, value: Expression): Expression {
        return MethodCallExpr(
            utilsName,
            "setField",
            NodeList(renderClassExpression(field.enclosingClass), StringLiteralExpr(field.name), value),
        )
    }

    override fun renderSetPrivateField(instance: Expression, field: JcField, value: Expression): Expression {
        return MethodCallExpr(
            utilsName,
            "setField",
            NodeList(instance, StringLiteralExpr(field.name), value),
        )
    }

    //endregion
    
    //region Allocate Call

    override fun renderAllocateMemoryCall(expr: UTestAllocateMemoryCall): Expression {
        error("allocateInstance not allowed in spring tests")
    }
    
    //endregion
}
