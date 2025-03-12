package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.type.ReferenceType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.testRenderer.JcTestBlockRenderer
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestExpression
import java.util.IdentityHashMap

open class JcUnsafeTestBlockRenderer private constructor(
    override val importManager: JcUnsafeImportManager,
    identifiersManager: JcIdentifiersManager,
    shouldDeclareVar: Set<UTestExpression>,
    exprCache: IdentityHashMap<UTestExpression, Expression>,
    thrownExceptions: HashSet<ReferenceType>
) : JcTestBlockRenderer(importManager, identifiersManager, shouldDeclareVar, exprCache, thrownExceptions) {

    constructor(
        importManager: JcUnsafeImportManager,
        identifiersManager: JcIdentifiersManager,
        shouldDeclareVar: Set<UTestExpression>
    ) : this(importManager, identifiersManager, shouldDeclareVar, IdentityHashMap(), HashSet())

    override fun newInnerBlock(): JcUnsafeTestBlockRenderer {
        return JcUnsafeTestBlockRenderer(
            importManager,
            JcIdentifiersManager(identifiersManager),
            shouldDeclareVar,
            IdentityHashMap(exprCache),
            thrownExceptions
        )
    }

    private val utilsName: NameExpr by lazy {
        NameExpr(importManager.reflectionUtilsName)
    }

    override fun renderPrivateCtorCall(ctor: JcMethod, type: JcClassType, args: List<Expression>): Expression {
        TODO()
    }

    override fun renderPrivateMethodCall(method: JcMethod, instance: Expression, args: List<Expression>): Expression {
        TODO()
    }

    override fun renderPrivateStaticMethodCall(method: JcMethod, args: List<Expression>): Expression {
        TODO()
    }

    override fun renderGetPrivateStaticField(field: JcField): Expression {
        return MethodCallExpr(
            utilsName,
            "getStaticFieldValue",
            NodeList(renderClassExpression(field.enclosingClass), StringLiteralExpr(field.name)),
        )
    }

    override fun renderGetPrivateField(instance: Expression, field: JcField): Expression {
        return MethodCallExpr(
            utilsName,
            "getFieldValue",
            NodeList(instance, StringLiteralExpr(field.name)),
        )
    }

    override fun renderSetPrivateStaticField(field: JcField, value: Expression): Expression {
        return MethodCallExpr(
            utilsName,
            "setStaticFieldValue",
            NodeList(renderClassExpression(field.enclosingClass), StringLiteralExpr(field.name), value),
        )
    }

    override fun renderSetPrivateField(instance: Expression, field: JcField, value: Expression): Expression {
        return MethodCallExpr(
            utilsName,
            "setFieldValue",
            NodeList(instance, StringLiteralExpr(field.name), value),
        )
    }

    override fun renderAllocateMemoryCall(expr: UTestAllocateMemoryCall): Expression {
        thrownExceptions.add(StaticJavaParser.parseClassOrInterfaceType("InstantiationException"))
        return MethodCallExpr(
            utilsName,
            "allocateInstance",
            NodeList(renderClassExpression(expr.clazz)),
        )
    }
}
