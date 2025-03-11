package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.ClassExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.MarkerAnnotationExpr
import com.github.javaparser.ast.expr.MethodCallExpr
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
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.ext.packageName
import org.jacodb.api.jvm.ext.toType

abstract class JcCodeRenderer<T: Node>(
    val importManager: JcImportManager
) {

    private var rendered: T? = null

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

    // TODO: handle private types?
    fun renderClass(type: JcClassType, includeGenericArgs: Boolean = true): ClassOrInterfaceType {
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
        ClassExpr(renderType(type, false))

    val testAnnotationJUnit: AnnotationExpr by lazy {
        importManager.add("org.junit.Test")
        MarkerAnnotationExpr("Test")
    }

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
}
