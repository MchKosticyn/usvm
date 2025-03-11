package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.ArrayAccessExpr
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.ClassExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.MarkerAnnotationExpr
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.ObjectCreationExpr
import com.github.javaparser.ast.expr.TypeExpr
import com.github.javaparser.ast.type.ArrayType
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.PrimitiveType
import com.github.javaparser.ast.type.PrimitiveType.Primitive
import com.github.javaparser.ast.type.Type
import com.github.javaparser.ast.type.VoidType
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.ext.packageName
import org.jacodb.api.jvm.ext.toType

abstract class JcCodeRenderer<T: Node>(
    val importManager: JcImportManager
) {

    private var rendered: T? = null

    protected abstract fun renderInternal(): T

    fun render(): T {
        if (rendered != null)
            return rendered!!

        rendered = renderInternal()
        return rendered!!
    }

    companion object {
        val voidType by lazy { VoidType() }
    }

    //region Types

    fun qualifiedName(type: JcClassType): String = qualifiedName(type.jcClass)
    fun qualifiedName(clazz: JcClassOrInterface): String = clazz.name.replace("$", ".")
    fun qualifiedName(typeName: TypeName): String = qualifiedName(typeName.typeName)
    private fun qualifiedName(typeName: String): String = typeName.replace("$", ".")

    private fun referenceType(jcClass: JcClassOrInterface) {
        // TODO
        importManager.add(jcClass.packageName)
    }

    fun renderType(type: JcType, includeGenericArgs: Boolean = true): Type = when (type) {
        is JcPrimitiveType -> PrimitiveType(Primitive.byTypeName(type.typeName).get())
        is JcArrayType -> ArrayType(renderType(type.elementType, includeGenericArgs))
        is JcClassType -> renderClass(type, includeGenericArgs)
        else -> error("unexpected type ${type.typeName}")
    }

    fun renderClass(type: JcClassType, includeGenericArgs: Boolean = true): ClassOrInterfaceType {
        check(!type.isPrivate) { "Rendering private classes is not supported" }

        referenceType(type.jcClass)

        val renderedType = StaticJavaParser.parseClassOrInterfaceType(qualifiedName(type))
        if (!includeGenericArgs)
            return renderedType.removeTypeArguments()

        val typeArguments = type.typeArguments
        if (typeArguments.isEmpty())
            return renderedType

        val renderedTypeArguments = typeArguments.map { renderType(it) }
        return renderedType.setTypeArguments(NodeList(renderedTypeArguments))
    }

    fun renderClass(jcClass: JcClassOrInterface): ClassOrInterfaceType {
        return renderClass(jcClass.toType())
    }

    fun renderClassExpression(type: JcClassType): Expression =
        ClassExpr(renderClass(type, false))

    //endregion

    //region Annotations

    val testAnnotationJUnit: AnnotationExpr by lazy {
        importManager.add("org.junit.Test")
        MarkerAnnotationExpr("Test")
    }

    //endregion

    //region Methods

    val mockitoClass: ClassOrInterfaceType by lazy {
        importManager.add("org.mockito.Mockito")
        StaticJavaParser.parseClassOrInterfaceType("Mockito")
    }

    fun mockitoSpyMethodCall(classToSpy: JcClassType): MethodCallExpr {
        return MethodCallExpr(
            TypeExpr(mockitoClass),
            "spy",
            NodeList(renderClassExpression(classToSpy))
        )
    }

    open fun renderPrivateCtorCall(ctor: JcMethod, type: JcClassType, args: List<Expression>): Expression {
        error("Rendering private methods is not supported")
    }

    open fun renderConstructorCall(ctor: JcMethod, type: JcClassType, args: List<Expression>): Expression {
        check(ctor.isConstructor)
        if (ctor.isPrivate)
            return renderPrivateCtorCall(ctor, type, args)

        val renderedClass = renderClass(type)
        var ctorArgs: List<Expression> = args
        val scope: Expression?
        when {
            type.outerType == null -> scope = null
            type.isStatic -> scope = TypeExpr(renderClass(type.outerType!!))
            else -> {
                scope = args.first()
                ctorArgs = args.drop(1)
            }
        }

        return ObjectCreationExpr(scope, renderedClass, NodeList(ctorArgs))
    }

    open fun renderPrivateMethodCall(method: JcMethod, instance: Expression, args: List<Expression>): Expression {
        error("Rendering private methods is not supported")
    }

    open fun renderMethodCall(method: JcMethod, instance: Expression, args: List<Expression>): Expression {
        check(!method.isStatic)

        if (method.isPrivate)
            return renderPrivateMethodCall(method, instance, args)

        return MethodCallExpr(
            instance,
            method.name,
            NodeList(args)
        )
    }

    open fun renderPrivateStaticMethodCall(method: JcMethod, args: List<Expression>): Expression {
        error("Rendering private methods is not supported")
    }

    open fun renderStaticMethodCall(method: JcMethod, args: List<Expression>): Expression {
        check(method.isStatic)

        if (method.isPrivate)
            return renderPrivateStaticMethodCall(method, args)

        return MethodCallExpr(
            TypeExpr(renderClass(method.enclosingClass)),
            method.name,
            NodeList(args)
        )
    }

    //endregion

    //region Fields

    open fun renderSetPrivateStaticField(field: JcField, value: Expression): Expression {
        error("Rendering private fields is not supported")
    }

    fun renderSetStaticField(field: JcField, value: Expression): Expression {
        check(field.isStatic)

        if (field.isPrivate)
            return renderSetPrivateStaticField(field, value)

        return AssignExpr(
            FieldAccessExpr(TypeExpr(renderClass(field.enclosingClass)), field.name),
            value,
            AssignExpr.Operator.ASSIGN
        )
    }

    open fun renderSetPrivateField(instance: Expression, field: JcField, value: Expression): Expression {
        error("Rendering private fields is not supported")
    }

    fun renderSetField(instance: Expression, field: JcField, value: Expression): Expression {
        check(!field.isStatic)

        if (field.isPrivate)
            renderSetPrivateField(instance, field, value)

        return AssignExpr(
            FieldAccessExpr(instance, field.name),
            value,
            AssignExpr.Operator.ASSIGN
        )
    }

    //endregion

    //region Arrays

    fun renderArraySet(array: Expression, index: Expression, value: Expression): Expression {
        return AssignExpr(
            ArrayAccessExpr(array, index),
            value,
            AssignExpr.Operator.ASSIGN
        )
    }

    //endregion
}
