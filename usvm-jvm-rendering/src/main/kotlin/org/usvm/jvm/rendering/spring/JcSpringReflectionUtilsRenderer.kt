package org.usvm.jvm.rendering.spring

import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.CastExpr
import com.github.javaparser.ast.expr.ClassExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.findType
import org.objectweb.asm.Opcodes
import org.usvm.jvm.rendering.ReflectionUtilsInlineStrategy
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestBlockRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeUtilsRenderer

class JcSpringReflectionUtilsRenderer(
    importManager: JcImportManager,
    utilsInlineStrategy: ReflectionUtilsInlineStrategy,
    private val isAccessibleFromTestClass: (JcClassOrInterface) -> Boolean,
) : JcUnsafeUtilsRenderer(importManager, utilsInlineStrategy) {

    companion object {
        private const val SPRING_TEST = "org.springframework.test.util.ReflectionTestUtils"
        private const val SPRING_TEST_SIMPLE = "ReflectionTestUtils"
        private const val SPRING_INTERNAL = "org.springframework.util.ReflectionUtils"
        private const val SPRING_INTERNAL_SIMPLE = "ReflectionUtils"
    }

    private val springTestUtilsName: Expression by lazy {
        NameExpr(if (importManager.add(SPRING_TEST)) SPRING_TEST_SIMPLE else SPRING_TEST)
    }

    private val springInternalUtilsName: Expression by lazy {
        NameExpr(if (importManager.add(SPRING_INTERNAL)) SPRING_INTERNAL_SIMPLE else SPRING_INTERNAL)
    }

    override fun renderCtorCall(
        blockRenderer: JcUnsafeTestBlockRenderer,
        ctor: JcMethod,
        type: JcClassType,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        return if (isAccessibleFromTestClass(type.jcClass))
            springCtorCall(blockRenderer, ctor, type, args, inlinesVarargs)
        else
            super.renderCtorCall(blockRenderer, ctor, type, args, inlinesVarargs)
    }

    @Suppress("unused")
    private fun springCtorCall(
        blockRenderer: JcUnsafeTestBlockRenderer,
        ctor: JcMethod,
        type: JcClassType,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        blockRenderer.addThrownExceptions(
            listOf(
                "java.lang.reflect.InvocationTargetException",
                "java.lang.NoSuchMethodException",
                "java.lang.InstantiationException",
                "java.lang.IllegalAccessException"
            )
        )
        val cp = ctor.enclosingClass.classpath
        val ctorParametersTypes = ctor.parameters.map { cp.findType(it.type.typeName) }
        val instanceType = blockRenderer.renderClass(type, includeGenericArgs = false)
        val accessibleCtorArgs = listOf(ClassExpr(instanceType)) + ctorParametersTypes.map {
            ClassExpr(blockRenderer.renderType(it, false))
        }

        val accessibleCtor = MethodCallExpr(
            springInternalUtilsName,
            NodeList(instanceType),
            "accessibleConstructor",
            NodeList(accessibleCtorArgs),
        )
        val newInstCall = MethodCallExpr(accessibleCtor, "newInstance", NodeList(args))
        return newInstCall
    }

    override fun renderInstanceMethodCall(
        blockRenderer: JcUnsafeTestBlockRenderer,
        method: JcMethod,
        instance: Expression,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        return if (isAccessibleFromTestClass(method.enclosingClass))
            springInstanceMethodCall(blockRenderer, method, instance, args, inlinesVarargs)
        else
            super.renderInstanceMethodCall(blockRenderer, method, instance, args, inlinesVarargs)
    }

    @Suppress("unused")
    private fun springInstanceMethodCall(
        blockRenderer: JcUnsafeTestBlockRenderer,
        method: JcMethod,
        instance: Expression,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        val allArgs = listOf(instance, StringLiteralExpr(method.name)) + args
        return MethodCallExpr(
            springTestUtilsName,
            listTypeArgsFor(method, blockRenderer),
            "invokeMethod",
            NodeList(allArgs),
        )
    }

    override fun renderStaticMethodCall(
        blockRenderer: JcUnsafeTestBlockRenderer,
        method: JcMethod,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        return if (isAccessibleFromTestClass(method.enclosingClass))
            springStaticMethodCall(blockRenderer, method, args, inlinesVarargs)
        else
            super.renderStaticMethodCall(blockRenderer, method, args, inlinesVarargs)
    }

    @Suppress("unused")
    private fun springStaticMethodCall(
        blockRenderer: JcUnsafeTestBlockRenderer,
        method: JcMethod,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        blockRenderer.addThrownException("java.lang.Throwable")
        val enclosingClass = method.enclosingClass
        val invokeMethodArgs = listOf(
            blockRenderer.renderClassExpression(enclosingClass),
            StringLiteralExpr(method.name)
        ) + args

        return MethodCallExpr(
            springTestUtilsName,
            listTypeArgsFor(method, blockRenderer),
            "invokeMethod",
            NodeList(invokeMethodArgs),
        )
    }

    override fun renderGetInstanceField(
        blockRenderer: JcUnsafeTestBlockRenderer,
        instance: Expression,
        field: JcField
    ): Expression {
        return if (isAccessibleFromTestClass(field.enclosingClass))
            springGetInstanceField(blockRenderer, instance, field)
        else
            super.renderGetInstanceField(blockRenderer, instance, field)
    }

    private fun springGetInstanceField(
        blockRenderer: JcUnsafeTestBlockRenderer,
        instance: Expression,
        field: JcField
    ): Expression {
        val call = MethodCallExpr(
            springTestUtilsName,
            "getField",
            NodeList(instance, StringLiteralExpr(field.name))
        )

        return CastExpr(blockRenderer.renderType(fieldType(field)), call)
    }

    override fun renderGetStaticField(blockRenderer: JcUnsafeTestBlockRenderer, field: JcField): Expression {
        return if (isAccessibleFromTestClass(field.enclosingClass))
            springGetStaticField(blockRenderer, field)
        else
            super.renderGetStaticField(blockRenderer, field)
    }

    private fun springGetStaticField(blockRenderer: JcUnsafeTestBlockRenderer, field: JcField): Expression {
        val call = MethodCallExpr(
            springTestUtilsName,
            "getField",
            NodeList(
                blockRenderer.renderClassExpression(field.enclosingClass),
                StringLiteralExpr(field.name)
            ),
        )
        return CastExpr(blockRenderer.renderType(fieldType(field)), call)
    }

    override fun renderSetInstanceField(
        blockRenderer: JcUnsafeTestBlockRenderer,
        instance: Expression,
        field: JcField,
        value: Expression
    ): Expression {
        return if (isAccessibleFromTestClass(field.enclosingClass) && !field.enclosingClass.isRecord)
            springSetInstanceField(blockRenderer, instance, field, value)
        else
            super.renderSetInstanceField(blockRenderer, instance, field, value)
    }

    private val JcClassOrInterface.isRecord: Boolean get() = (access and Opcodes.ACC_RECORD) != 0

    private fun springSetInstanceField(
        blockRenderer: JcUnsafeTestBlockRenderer,
        instance: Expression,
        field: JcField,
        value: Expression
    ): Expression {
        return MethodCallExpr(
            springTestUtilsName,
            "setField",
            NodeList(instance, StringLiteralExpr(field.name), value),
        )
    }

    override fun renderSetStaticField(
        blockRenderer: JcUnsafeTestBlockRenderer,
        field: JcField,
        value: Expression
    ): Expression {
        return if (isAccessibleFromTestClass(field.enclosingClass) && !field.isFinal)
            springSetStaticField(blockRenderer, field, value)
        else
            super.renderSetStaticField(blockRenderer, field, value)
    }

    private fun springSetStaticField(
        blockRenderer: JcUnsafeTestBlockRenderer,
        field: JcField,
        value: Expression
    ): Expression {
        return MethodCallExpr(
            springTestUtilsName,
            "setField",
            NodeList(
                blockRenderer.renderClassExpression(field.enclosingClass),
                StringLiteralExpr(field.name),
                value
            ),
        )
    }
}
