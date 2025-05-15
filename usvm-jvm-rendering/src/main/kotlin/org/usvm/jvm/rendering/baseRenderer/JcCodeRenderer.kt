package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.ArrayAccessExpr
import com.github.javaparser.ast.expr.ArrayInitializerExpr
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.BooleanLiteralExpr
import com.github.javaparser.ast.expr.CastExpr
import com.github.javaparser.ast.expr.CharLiteralExpr
import com.github.javaparser.ast.expr.ClassExpr
import com.github.javaparser.ast.expr.DoubleLiteralExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.IntegerLiteralExpr
import com.github.javaparser.ast.expr.LambdaExpr
import com.github.javaparser.ast.expr.LongLiteralExpr
import com.github.javaparser.ast.expr.MarkerAnnotationExpr
import com.github.javaparser.ast.expr.MemberValuePair
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.Name
import com.github.javaparser.ast.expr.NormalAnnotationExpr
import com.github.javaparser.ast.expr.NullLiteralExpr
import com.github.javaparser.ast.expr.ObjectCreationExpr
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.expr.TypeExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.type.ArrayType
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.PrimitiveType
import com.github.javaparser.ast.type.PrimitiveType.Primitive
import com.github.javaparser.ast.type.ReferenceType
import com.github.javaparser.ast.type.Type
import com.github.javaparser.ast.type.VoidType
import com.github.javaparser.ast.type.WildcardType
import kotlin.math.max
import org.jacodb.api.jvm.JcAccessible
import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcBoundedWildcard
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypeVariable
import org.jacodb.api.jvm.JcUnboundWildcard
import org.jacodb.api.jvm.ext.packageName
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.features.classpaths.virtual.JcVirtualField
import org.jacodb.impl.types.JcTypeVariableImpl
import org.jacodb.impl.types.TypeNameImpl
import org.usvm.jvm.rendering.baseRenderer.JcTypeVariableExt.isRecursive
import org.usvm.jvm.rendering.isVararg
import org.usvm.jvm.util.toTypedMethod

abstract class JcCodeRenderer<T: Node>(
    open val importManager: JcImportManager,
    internal val identifiersManager: JcIdentifiersManager,
    protected val cp: JcClasspath,
    private val packagePrivateAsPublic: Boolean = true
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

    val objectType by lazy { renderClass("java.lang.Object") }

    //region Types

    protected fun qualifiedName(typeName: String): String = typeName.replace("$", ".")

    fun renderType(type: JcType, includeGenericArgs: Boolean = true): Type = when (type) {
        is JcPrimitiveType -> PrimitiveType(Primitive.byTypeName(type.typeName).get())
        is JcArrayType -> ArrayType(renderType(type.elementType, includeGenericArgs))
        is JcClassType -> renderClass(type, includeGenericArgs)
        is JcTypeVariable -> renderClass(type.jcClass, (type as? JcTypeVariableImpl)?.isRecursive == false)
        is JcBoundedWildcard -> renderBoundedWildcardType(type)
        is JcUnboundWildcard -> WildcardType()
        else -> error("unexpected type ${type.typeName}")
    }

    private fun renderBoundedWildcardType(type: JcBoundedWildcard): Type {
        var wc = WildcardType()

        val ub = type.upperBounds.singleOrNull()
        if (ub != null) wc = wc.setExtendedType(renderType(ub) as ReferenceType)

        val lb = type.lowerBounds.singleOrNull()
        if (lb != null) wc = wc.setSuperType(renderType(lb) as ReferenceType)

        return wc
    }

    fun renderClass(typeName: String, includeGenericArgs: Boolean = true): ClassOrInterfaceType {
        check(!typeName.contains('<') && !typeName.contains('>')) {
            "hardcoded generics not supported"
        }
        val type = cp.findTypeOrNull(typeName) as? JcClassType
        if (type != null)
            return renderClass(type, includeGenericArgs)
        var classOrInterface = StaticJavaParser.parseClassOrInterfaceType(typeName)
        if (importManager.add(classOrInterface.nameWithScope))
            classOrInterface = classOrInterface.removeScope()
        return classOrInterface
    }

    fun shouldRenderClassAsPrivate(type: JcClassType): Boolean {
        return !(type.isPublic || packagePrivateAsPublic && type.isPackagePrivate)
    }

    fun renderClass(type: JcClassType, includeGenericArgs: Boolean = true): ClassOrInterfaceType {
        check(!shouldRenderClassAsPrivate(type)) { "Rendering private classes is not supported" }
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
        if (!includeGenericArgs || type.typeArguments.any { it is JcTypeVariableImpl && it.isRecursive })
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

    //region Test framework methods

    val assertionsClass: ClassOrInterfaceType get() = renderClass(JcTestFrameworkProvider.assertionsClassName)

    fun assertThrowsCall(exceptionClassExpr: Expression, observedLambda: Expression): MethodCallExpr  {
        check(JcTestFrameworkProvider.assertionsClassName != JcRendererTestFramework.JUNIT_4.assertionsClassName) {
            "not yet supported"
        }

        return MethodCallExpr(
            TypeExpr(assertionsClass),
            "assertThrows",
            NodeList(exceptionClassExpr, observedLambda)
        )
    }

    //endregion

    //region Mockito methods

    val mockitoClass: ClassOrInterfaceType by lazy { renderClass("org.mockito.Mockito") }

    protected val JcField.isSpy: Boolean
        get() = this is JcVirtualField &&
                name == "\$isSpyGenerated239" &&
                type == TypeNameImpl.fromTypeName("java.lang.Object")

    fun mockitoMockMethodCall(classToMock: JcClassType): MethodCallExpr {
        return MethodCallExpr(
            TypeExpr(mockitoClass),
            "mock",
            NodeList(renderClassExpression(classToMock))
        )
    }

    fun mockitoSpyInstanceMethodCall(instanceToSpy: Expression): MethodCallExpr {
        return MethodCallExpr(
            TypeExpr(mockitoClass),
            "spy",
            NodeList(instanceToSpy)
        )
    }

    fun mockitoSpyClassMethodCall(classToSpy: JcClassType): MethodCallExpr {
        return MethodCallExpr(
            TypeExpr(mockitoClass),
            "spy",
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

    fun mockitoWhenMethodCall(receiver: Expression, methodCall: Expression): MethodCallExpr {
        return MethodCallExpr(
            receiver,
            "when",
            NodeList(methodCall)
        )
    }

    fun mockitoThenThrowMethodCall(methodMock: Expression, exception: Expression): MethodCallExpr {
        return MethodCallExpr(
            methodMock,
            "thenThrow",
            NodeList(exception)
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

    fun mockitoAnyMethodCall(type: JcType): MethodCallExpr {
        return MethodCallExpr(
            TypeExpr(mockitoClass),
            NodeList(renderType(type)),
            "any",
            NodeList()
        )
    }

    //endregion

    fun shouldRenderMethodCallAsPrivate(method: JcMethod): Boolean {
        return !method.isPublic
    }

    open fun renderPrivateCtorCall(ctor: JcMethod, type: JcClassType, args: List<Expression>, inlinesVarargs: Boolean): Expression {
        error("Rendering private methods is not supported")
    }

    open fun renderConstructorCall(ctor: JcMethod, type: JcClassType, args: List<Expression>, inlinesVarargs: Boolean): Expression {
        check(ctor.isConstructor) {
            "not a constructor in renderConstructorCall"
        }
        if (shouldRenderMethodCallAsPrivate(ctor))
            return renderPrivateCtorCall(ctor, type, args, inlinesVarargs)

        val castedArgs = callArgsWithGenericsCasted(ctor, args, inlinesVarargs)

        return when {
            type.outerType == null || type.isStatic -> {
                ObjectCreationExpr(null, renderClass(type), NodeList(castedArgs))
            }

            else -> {
                val ctorTypeName = qualifiedName(type.jcClass.name).split(".").last()
                val ctorType = StaticJavaParser.parseClassOrInterfaceType(ctorTypeName)
                    .setTypeArgsIfNeeded(true, type)
                ObjectCreationExpr(castedArgs.first(), ctorType, NodeList(castedArgs.drop(1)))
            }
        }
    }

    open fun renderPrivateMethodCall(method: JcMethod, instance: Expression, args: List<Expression>, inlinesVarargs: Boolean): Expression {
        error("Rendering private methods is not supported")
    }

    open fun renderMethodCall(method: JcMethod, instance: Expression, args: List<Expression>, inlinesVarargs: Boolean): Expression {
        check(!method.isStatic) {
            "cannot render static methods in renderMethodCall"
        }

        if (shouldRenderMethodCallAsPrivate(method))
            return renderPrivateMethodCall(method, instance, args, inlinesVarargs)

        val castedArgs = callArgsWithGenericsCasted(method, args, inlinesVarargs)

        return MethodCallExpr(
            instance,
            method.name,
            NodeList(castedArgs)
        )
    }

    open fun renderPrivateStaticMethodCall(method: JcMethod, args: List<Expression>, inlinesVarargs: Boolean): Expression {
        error("Rendering private methods is not supported")
    }

    open fun renderStaticMethodCall(method: JcMethod, args: List<Expression>, inlinesVarargs: Boolean): Expression {
        check(method.isStatic) {
            "cannot render instance method in renderStaticMethodCall"
        }

        if (shouldRenderMethodCallAsPrivate(method))
            return renderPrivateStaticMethodCall(method, args, inlinesVarargs)

        val castedArgs = callArgsWithGenericsCasted(method, args, inlinesVarargs)

        return MethodCallExpr(
            renderStaticMethodCallScope(method, false),
            method.name,
            NodeList(castedArgs)
        )
    }

    protected fun callArgsWithGenericsCasted(method: JcMethod, args: List<Expression>, hasInlinedVarArgs: Boolean): List<Expression> {
        val typedParams = method.toTypedMethod.parameters.map { parameter -> parameter.type }.toMutableList()

        if (hasInlinedVarArgs) {
            check(method.isVararg) {
                "cannot inline non-vararg args"
            }

            val varargParamType = typedParams.removeLast()
            check(varargParamType is JcArrayType) {
                "vararg param expected to be of array type"
            }

            val extraArgType = varargParamType.elementType
            val extraParamCount = max(args.size - typedParams.size, 0)
            typedParams.addAll(List(extraParamCount) { extraArgType })
        }

        return args.zip(typedParams).map { (arg, paramType) ->
            exprWithGenericsCasted(paramType, arg)
        }
    }

    protected fun exprWithGenericsCasted(type: JcType, expr: Expression): Expression {
        if (type !is JcClassType || type.typeArguments.isEmpty()) return expr
        val asObj = CastExpr(objectType, expr)
        val asTargetType = CastExpr(renderType(type), asObj)
        return asTargetType
    }

    @Suppress("SameParameterValue")
    private fun renderStaticMethodCallScope(method: JcMethod, allowStaticImport: Boolean): TypeExpr? {
        val callType = method.enclosingClass.toType()
        val useClassName = !allowStaticImport || !importManager.addStatic(callType.jcClass.name, method.name)
        return if (useClassName) TypeExpr(renderClass(callType, includeGenericArgs = false)) else null
    }

    protected open fun renderLambdaExpression(params: List<Parameter>, body: BlockStmt): Expression {
        return LambdaExpr(NodeList(params), body)
    }

    protected fun renderMethodParameter(type: JcType, name: String? = null): Parameter {
        return renderMethodParameter(type.typeName, name)
    }

    protected fun renderMethodParameter(clazz: JcClassOrInterface, name: String? = null): Parameter {
        return renderMethodParameter(clazz.name, name)
    }

    protected fun renderMethodParameter(typeName: String, name: String? = null): Parameter {
        val paramName = identifiersManager.generateIdentifier(name ?: "")
        val renderedClass = renderClass(typeName)
        return Parameter(renderedClass, paramName)
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
        check(field.isStatic) {
            "cannot render instance field in renderGetStaticField"
        }

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
        check(!field.isStatic) {
            "cannot render static field in renderGetField"
        }

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
        check(field.isStatic) {
            "cannot render instance field in renderSetStaticField"
        }

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
        check(!field.isStatic) {
            "cannot render static field in renderSetField"
        }

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

    //region Modifiers

    fun getModifiers(accessible: JcAccessible): List<Modifier> {
        val modifiers = mutableListOf<Modifier>()
        if (accessible.isPublic)
            modifiers.add(Modifier.publicModifier())
        if (accessible.isStatic)
            modifiers.add(Modifier.staticModifier())
        if (accessible.isFinal)
            modifiers.add(Modifier.finalModifier())
        if (accessible.isPrivate)
            modifiers.add(Modifier.privateModifier())
        if (accessible.isAbstract)
            modifiers.add(Modifier.abstractModifier())
        if (accessible.isProtected)
            modifiers.add(Modifier.protectedModifier())

        return modifiers
    }

    //endregion

    //region Primitives

    fun renderBooleanPrimitive(value: Boolean): Expression = BooleanLiteralExpr(value)

    fun renderCharPrimitive(value: Char): Expression = CharLiteralExpr(value)

    fun renderStringPrimitive(value: String): Expression = StringLiteralExpr(value)

    fun renderBytePrimitive(value: Byte): Expression =
        CastExpr(PrimitiveType.byteType(), IntegerLiteralExpr(value.toString()))

    fun renderShortPrimitive(value: Short): Expression =
        CastExpr(PrimitiveType.shortType(), IntegerLiteralExpr(value.toString()))

    fun renderIntPrimitive(value: Int): Expression = IntegerLiteralExpr(value.toString())

    fun renderLongPrimitive(value: Long): Expression = LongLiteralExpr(value.toString() + "L")

    fun renderFloatPrimitive(value: Float): Expression = DoubleLiteralExpr(value.toString() + "f")

    fun renderDoublePrimitive(value: Double): Expression = DoubleLiteralExpr(value.toString())

    //endregion

    //region Annotations

    fun renderAnnotation(annotation: JcAnnotation): AnnotationExpr {
        val annotationClass = annotation.jcClass

        check(annotationClass != null) {
            "annotation class is null"
        }

        val annotationName = Name(renderClass(annotationClass).nameWithScope)

        if (annotation.values.isEmpty()) {
            return MarkerAnnotationExpr(annotationName)
        }

        val annotationValues = renderAnnotationValues(annotation.values)
        return NormalAnnotationExpr(annotationName, annotationValues)
    }

    private fun renderAnnotationValues(rawValues: Map<String, Any?>): NodeList<MemberValuePair> {
        val result = rawValues.map { (name, value) ->
            val renderedValue = renderSingleAnnotationValue(value)
            MemberValuePair(name, renderedValue)
        }
        return NodeList(result)
    }

    private fun renderSingleAnnotationValue(value: Any?): Expression {
        return when (value) {
            null -> NullLiteralExpr()

            is String -> renderStringPrimitive(value)

            is Boolean -> renderBooleanPrimitive(value)

            is Char -> renderCharPrimitive(value)

            is Byte -> renderBytePrimitive(value)

            is Short-> renderShortPrimitive(value)

            is Int -> renderIntPrimitive(value)

            is Long -> renderLongPrimitive(value)

            is Float -> renderFloatPrimitive(value)

            is Double -> renderDoublePrimitive(value)

            is JcClassOrInterface -> {
                renderClassExpression(value)
            }

            is JcField -> {
                check(value.isStatic) {
                    "enum value should be a static field"
                }

                renderGetStaticField(value)
            }

            is JcAnnotation -> {
                renderAnnotation(value)
            }

            is List<*> -> {
                val renderedValues = NodeList(value.map { renderSingleAnnotationValue(it) })
                ArrayInitializerExpr(renderedValues)
            }

            else -> error("unsupported annotation value kind $value")
        }
    }

    //endregion
}
