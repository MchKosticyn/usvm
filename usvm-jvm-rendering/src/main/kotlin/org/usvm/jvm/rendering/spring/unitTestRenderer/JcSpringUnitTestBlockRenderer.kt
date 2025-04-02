package org.usvm.jvm.rendering.spring.unitTestRenderer

import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.CastExpr
import com.github.javaparser.ast.expr.ClassExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.expr.TypeExpr
import com.github.javaparser.ast.type.ReferenceType
import java.util.*
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeImportManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestBlockRenderer
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestExpression

open class JcSpringUnitTestBlockRenderer protected constructor(
    override val methodRenderer: JcSpringUnitTestRenderer,
    override val importManager: JcUnsafeImportManager,
    identifiersManager: JcIdentifiersManager,
    cp: JcClasspath,
    shouldDeclareVar: Set<UTestExpression>,
    exprCache: IdentityHashMap<UTestExpression, Expression>,
    thrownExceptions: HashSet<ReferenceType>
) : JcUnsafeTestBlockRenderer(
    methodRenderer,
    importManager,
    identifiersManager,
    cp,
    shouldDeclareVar,
    exprCache,
    thrownExceptions
) {

    constructor(
        methodRenderer: JcSpringUnitTestRenderer,
        importManager: JcUnsafeImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath,
        shouldDeclareVar: Set<UTestExpression>
    ) : this(methodRenderer, importManager, identifiersManager, cp, shouldDeclareVar, IdentityHashMap(), HashSet())

    override fun newInnerBlock(): JcSpringUnitTestBlockRenderer {
        return JcSpringUnitTestBlockRenderer(
            methodRenderer,
            importManager,
            JcIdentifiersManager(identifiersManager),
            cp,
            shouldDeclareVar,
            IdentityHashMap(exprCache),
            thrownExceptions
        )
    }

    //region Private Methods

    private fun JcMethod.parametersTypes() : List<JcType> {
        val cp = enclosingClass.classpath
        return parameters.map {
            val paramType = cp.findTypeOrNull(it.type.typeName)
            check(paramType != null) { "parameter type not found in classpath" }
            paramType
        }
    }

    override fun renderPrivateCtorCall(ctor: JcMethod, type: JcClassType, args: List<Expression>): Expression {
        val cp = type.classpath
        addThrownException("java.lang.reflect.InvocationTargetException", cp)
        addThrownException("java.lang.NoSuchMethodException", cp)
        addThrownException("java.lang.InstantiationException", cp)
        addThrownException("java.lang.IllegalAccessException", cp)

        val internalReflectionUtils = renderClass("org.springframework.util.ReflectionUtils")
        val ctorParametersTypes = ctor.parametersTypes()
        val instanceType = renderClass(type, includeGenericArgs = false)
        val accessibleCtorArgs = listOf(ClassExpr(instanceType)) + ctorParametersTypes.map {
            ClassExpr(renderType(it, false))
        }

        val accessibleCtor = MethodCallExpr(
            TypeExpr(internalReflectionUtils),
            NodeList(instanceType),
            "accessibleConstructor",
            NodeList(accessibleCtorArgs),
        )
        val newInstCall = MethodCallExpr(accessibleCtor, "newInstance", NodeList(args))
        return newInstCall
    }

    override fun renderPrivateMethodCall(method: JcMethod, instance: Expression, args: List<Expression>): Expression {
        addThrownException("java.lang.Throwable", method.enclosingClass.classpath)
        val allArgs = listOf(instance, StringLiteralExpr(method.name)) + args
        return MethodCallExpr(
            utilsName,
            typeArgsForPrivateCall(method),
            "invokeMethod",
            NodeList(allArgs),
        )
    }

    override fun renderPrivateStaticMethodCall(method: JcMethod, args: List<Expression>): Expression {
        addThrownException("java.lang.Throwable", method.enclosingClass.classpath)
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
        return MethodCallExpr(
            utilsName,
            "TEST_ALLOCATE_MEMORY",
            NodeList(StringLiteralExpr(expr.clazz.name)),
        )
    }

    //endregion
}
