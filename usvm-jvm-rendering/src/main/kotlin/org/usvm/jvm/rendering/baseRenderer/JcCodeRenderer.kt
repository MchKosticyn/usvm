package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.ArrayAccessExpr
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.ClassExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.FieldAccessExpr
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
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypeVariable
import org.jacodb.api.jvm.ext.packageName
import org.jacodb.api.jvm.ext.toType

abstract class JcCodeRenderer<T: Node>(
    open val importManager: JcImportManager,
    internal val identifiersManager: JcIdentifiersManager
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

    protected fun qualifiedName(typeName: String): String = typeName.replace("$", ".")

    fun renderType(type: JcType, includeGenericArgs: Boolean = true): Type = when (type) {
        is JcPrimitiveType -> PrimitiveType(Primitive.byTypeName(type.typeName).get())
        is JcArrayType -> ArrayType(renderType(type.elementType, includeGenericArgs))
        is JcClassType -> renderClass(type, includeGenericArgs)
        is JcTypeVariable -> renderClass(type.jcClass, includeGenericArgs)
        else -> error("unexpected type ${type.typeName}")
    }

    fun renderClass(typeName: String, cp: JcClasspath? = null, includeGenericArgs: Boolean = true): ClassOrInterfaceType {
        val type = (cp?.findTypeOrNull(typeName) as? JcClassType)
        if (type != null)
            return renderClass(type, includeGenericArgs)
        var classOrInterface = StaticJavaParser.parseClassOrInterfaceType(typeName)
        if (importManager.add(classOrInterface.nameWithScope))
            classOrInterface = classOrInterface.removeScope()
        return classOrInterface
    }

    fun renderClass(type: JcClassType, includeGenericArgs: Boolean = true): ClassOrInterfaceType {
        check(type.isPublic) { "Rendering private classes is not supported" }
        check(!type.jcClass.isAnonymous) { "Rendering anonymous classes is not supported" }

        return when {
            type.outerType == null -> renderClassOuter(type, includeGenericArgs)
            type.isStatic -> renderClassInnerStatic(type, includeGenericArgs)
            else -> renderClassInner(type, includeGenericArgs)
        }
    }

    private fun renderClassInnerStatic(type: JcClassType, includeGenericArgs: Boolean): ClassOrInterfaceType {
        val simpleNameParts = qualifiedName(type.jcClass.simpleName).split(".")
        val renderName = qualifiedName(type.jcClass.name)
        val importPackage = type.jcClass.packageName + "." + simpleNameParts.dropLast(1).joinToString(".")
        val importName = simpleNameParts.last()
        if (importManager.addStatic(importPackage, importName)) {
            return StaticJavaParser.parseClassOrInterfaceType(simpleNameParts.last())
                .setTypeArgsIfNeeded(includeGenericArgs, type)
        }
        return ClassOrInterfaceType(renderClass(type.outerType!!, false), renderName)
            .setTypeArgsIfNeeded(includeGenericArgs, type)
    }

    private fun ClassOrInterfaceType.setTypeArgsIfNeeded(
        includeGenericArgs: Boolean,
        type: JcClassType
    ): ClassOrInterfaceType {
        if (includeGenericArgs && type.typeArguments.isNotEmpty())
            return setTypeArguments(NodeList(type.typeArguments.map { renderType(it) }))

        return this
    }

    private fun renderClassInner(type: JcClassType, includeGenericArgs: Boolean): ClassOrInterfaceType {
        return ClassOrInterfaceType(
            renderClass(type.outerType!!, includeGenericArgs),
            qualifiedName(type.jcClass.simpleName).split(".").last()
        ).setTypeArgsIfNeeded(includeGenericArgs, type)
    }

    private fun renderClassOuter(type: JcClassType, includeGenericArgs: Boolean): ClassOrInterfaceType {
        val renderName = when {
            importManager.add(type.jcClass.packageName, type.jcClass.simpleName) -> qualifiedName(type.jcClass.simpleName)
            else -> qualifiedName(type.jcClass.name)
        }

        val renderedType = StaticJavaParser.parseClassOrInterfaceType(qualifiedName(renderName))
        if (!includeGenericArgs)
            return renderedType.removeTypeArguments()

        val typeArguments = type.typeArguments
        if (typeArguments.isEmpty())
            return renderedType

        val renderedTypeArguments = typeArguments.map { renderType(it) }
        return renderedType.setTypeArguments(NodeList(renderedTypeArguments))
    }

    fun renderClass(jcClass: JcClassOrInterface, includeGenericArgs: Boolean = true): ClassOrInterfaceType {
        return renderClass(jcClass.toType(), includeGenericArgs)
    }

    fun renderClassExpression(type: JcClassOrInterface): Expression =
        ClassExpr(renderClass(type, false))

    fun renderClassExpression(type: JcClassType): Expression =
        ClassExpr(renderClass(type, false))

    //endregion

    //region Methods

    //region Mockito methods

    val mockitoClass: ClassOrInterfaceType by lazy { renderClass("org.mockito.Mockito") }

    fun mockitoMockMethodCall(classToSpy: JcClassType): MethodCallExpr {
        return MethodCallExpr(
            TypeExpr(mockitoClass),
            "mock",
            NodeList(renderClassExpression(classToSpy))
        )
    }

    fun mockitoMockStaticMethodCall(mockedClass: JcClassOrInterface): MethodCallExpr {
        return MethodCallExpr(
            TypeExpr(mockitoClass),
            "mockStatic",
            NodeList(renderClassExpression(mockedClass))
        )
    }

    fun mockitoWhenMethodCall(methodCall: Expression, mockStaticReceiver: Expression? = null): MethodCallExpr {
        return MethodCallExpr(
            mockStaticReceiver ?: TypeExpr(mockitoClass),
            "when",
            NodeList(methodCall)
        )
    }

    fun mockitoThenReturnMethodCall(methodMock: Expression, methodValue: Expression): MethodCallExpr {
        return MethodCallExpr(
            methodMock,
            "thenReturn",
            NodeList(methodValue)
        )
    }

    fun mockitoAnyBooleanMethodCall(): MethodCallExpr {
        return MethodCallExpr(
            TypeExpr(mockitoClass),
            "anyBoolean",
            NodeList()
        )
    }

    fun mockitoAnyByteMethodCall(): MethodCallExpr {
        return MethodCallExpr(
            TypeExpr(mockitoClass),
            "anyByte",
            NodeList()
        )
    }

    fun mockitoAnyCharMethodCall(): MethodCallExpr {
        return MethodCallExpr(
            TypeExpr(mockitoClass),
            "anyChar",
            NodeList()
        )
    }

    fun mockitoAnyIntMethodCall(): MethodCallExpr {
        return MethodCallExpr(
            TypeExpr(mockitoClass),
            "anyInt",
            NodeList()
        )
    }

    fun mockitoAnyLongMethodCall(): MethodCallExpr {
        return MethodCallExpr(
            TypeExpr(mockitoClass),
            "anyLong",
            NodeList()
        )
    }

    fun mockitoAnyFloatMethodCall(): MethodCallExpr {
        return MethodCallExpr(
            TypeExpr(mockitoClass),
            "anyFloat",
            NodeList()
        )
    }

    fun mockitoAnyDoubleMethodCall(): MethodCallExpr {
        return MethodCallExpr(
            TypeExpr(mockitoClass),
            "anyDouble",
            NodeList()
        )
    }

    fun mockitoAnyShortMethodCall(): MethodCallExpr {
        return MethodCallExpr(
            TypeExpr(mockitoClass),
            "anyShort",
            NodeList()
        )
    }

    fun mockitoAnyMethodCall(): MethodCallExpr {
        return MethodCallExpr(
            TypeExpr(mockitoClass),
            "any",
            NodeList()
        )
    }

    //endregion

    fun shouldRenderMethodCallAsPrivate(method: JcMethod): Boolean {
        return !method.isPublic
    }

    open fun renderPrivateCtorCall(ctor: JcMethod, type: JcClassType, args: List<Expression>): Expression {
        error("Rendering private methods is not supported")
    }

    open fun renderConstructorCall(ctor: JcMethod, type: JcClassType, args: List<Expression>): Expression {
        check(ctor.isConstructor)
        if (shouldRenderMethodCallAsPrivate(ctor))
            return renderPrivateCtorCall(ctor, type, args)

        return when {
            type.outerType == null || type.isStatic -> {
                ObjectCreationExpr(null, renderClass(type), NodeList(args))
            }

            else -> {
                val ctorTypeName = qualifiedName(type.jcClass.name).split(".").last()
                val ctorType = StaticJavaParser.parseClassOrInterfaceType(ctorTypeName)
                    .setTypeArgsIfNeeded(true, type)
                ObjectCreationExpr(args.first(), ctorType, NodeList(args.drop(1)))
            }
        }
    }

    open fun renderPrivateMethodCall(method: JcMethod, instance: Expression, args: List<Expression>): Expression {
        error("Rendering private methods is not supported")
    }

    open fun renderMethodCall(method: JcMethod, instance: Expression, args: List<Expression>): Expression {
        check(!method.isStatic)

        if (shouldRenderMethodCallAsPrivate(method))
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

        if (shouldRenderMethodCallAsPrivate(method))
            return renderPrivateStaticMethodCall(method, args)

        return MethodCallExpr(
            renderStaticMethodCallScope(method, false),
            method.name,
            NodeList(args)
        )
    }

    private fun renderStaticMethodCallScope(method: JcMethod, allowStaticImport: Boolean): TypeExpr? {
        val callType = method.enclosingClass.toType()
        val useClassName = !allowStaticImport || !importManager.addStatic(callType.jcClass.name, method.name)
        return if (useClassName) TypeExpr(renderClass(callType, includeGenericArgs = false)) else null
    }


    //endregion

    //region Fields

    protected open fun shouldRenderGetFieldAsPrivate(field: JcField): Boolean {
        return !field.isPublic
    }

    protected open fun shouldRenderSetFieldAsPrivate(field: JcField): Boolean {
        return !field.isPublic || field.isFinal
    }

    open fun renderGetPrivateStaticField(field: JcField): Expression {
        error("Rendering private fields is not supported")
    }

    open fun renderGetStaticField(field: JcField): Expression {
        check(field.isStatic)

        if (shouldRenderGetFieldAsPrivate(field))
            return renderGetPrivateStaticField(field)

        return FieldAccessExpr(
            TypeExpr(renderClass(field.enclosingClass)),
            field.name
        )
    }

    open fun renderGetPrivateField(instance: Expression, field: JcField): Expression {
        error("Rendering private fields is not supported")
    }

    open fun renderGetField(instance: Expression, field: JcField): Expression {
        check(!field.isStatic)

        if (shouldRenderGetFieldAsPrivate(field))
            return renderGetPrivateField(instance, field)

        return FieldAccessExpr(
            instance,
            field.name
        )
    }

    open fun renderSetPrivateStaticField(field: JcField, value: Expression): Expression {
        error("Rendering private fields is not supported")
    }

    fun renderAssign(lhv: Expression, rhv: Expression): Expression {
        return AssignExpr(lhv, rhv, AssignExpr.Operator.ASSIGN)
    }

    fun renderSetStaticField(field: JcField, value: Expression): Expression {
        check(field.isStatic)

        if (shouldRenderSetFieldAsPrivate(field))
            return renderSetPrivateStaticField(field, value)

        return renderAssign(
            FieldAccessExpr(TypeExpr(renderClass(field.enclosingClass)), field.name),
            value
        )
    }

    open fun renderSetPrivateField(instance: Expression, field: JcField, value: Expression): Expression {
        error("Rendering private fields is not supported")
    }

    fun renderSetField(instance: Expression, field: JcField, value: Expression): Expression {
        check(!field.isStatic)

        if (shouldRenderSetFieldAsPrivate(field))
            return renderSetPrivateField(instance, field, value)

        return renderAssign(
            FieldAccessExpr(instance, field.name),
            value
        )
    }

    //endregion

    //region Arrays

    fun renderArraySet(array: Expression, index: Expression, value: Expression): Expression {
        return renderAssign(
            ArrayAccessExpr(array, index),
            value
        )
    }

    //endregion
}
