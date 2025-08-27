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
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestBlockRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeUtilsRenderer
import org.usvm.jvm.rendering.unsafeRenderer.ReflectionUtilName

class JcSpringReflectionUtilsRenderer(
    springBlockRenderer: JcSpringUnitTestBlockRenderer,
) : JcUnsafeUtilsRenderer(springBlockRenderer) {

    private val isAccessibleFromTestClass: (JcClassOrInterface) -> Boolean = importManager.reflectionUtilsInlineStrategy.isOpenForReflection

    val springTestUtilsName: Expression by lazy {
        NameExpr(
            if (importManager.add(ReflectionUtilName.SPRING_TEST))
                ReflectionUtilName.SPRING_TEST_SIMPLE
            else
                ReflectionUtilName.SPRING_TEST
        )
    }

    override fun renderCtorCall(
        ctor: JcMethod,
        type: JcClassType,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        return if (isAccessibleFromTestClass(type.jcClass))
            springCtorCall(ctor, type, args, inlinesVarargs)
        else
            super.renderCtorCall(ctor, type, args, inlinesVarargs)
    }

    @Suppress("unused")
    private fun springCtorCall(
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

        val springInternalUtilsName = NameExpr(
            if (importManager.add(ReflectionUtilName.SPRING_INTERNAL))
                ReflectionUtilName.SPRING_INTERNAL_SIMPLE
            else
                ReflectionUtilName.SPRING_INTERNAL
        )

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
        method: JcMethod,
        instance: Expression,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        return if (isAccessibleFromTestClass(method.enclosingClass))
            springInstanceMethodCall(method, instance, args, inlinesVarargs)
        else
            super.renderInstanceMethodCall(method, instance, args, inlinesVarargs)
    }

    @Suppress("unused")
    private fun springInstanceMethodCall(
        method: JcMethod,
        instance: Expression,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        val allArgs = listOf(instance, StringLiteralExpr(method.name)) + args
        return MethodCallExpr(
            springTestUtilsName,
            listTypeArgsFor(method),
            "invokeMethod",
            NodeList(allArgs),
        )
    }

    override fun renderStaticMethodCall(method: JcMethod, args: List<Expression>, inlinesVarargs: Boolean): Expression {
        return if (isAccessibleFromTestClass(method.enclosingClass))
            springStaticMethodCall(method, args, inlinesVarargs)
        else
            super.renderStaticMethodCall(method, args, inlinesVarargs)
    }

    @Suppress("unused")
    private fun springStaticMethodCall(method: JcMethod, args: List<Expression>, inlinesVarargs: Boolean): Expression {
        blockRenderer.addThrownException("java.lang.Throwable")
        val enclosingClass = method.enclosingClass
        val invokeMethodArgs = listOf(
            blockRenderer.renderClassExpression(enclosingClass),
            StringLiteralExpr(method.name)
        ) + args

        return MethodCallExpr(
            springTestUtilsName,
            listTypeArgsFor(method),
            "invokeMethod",
            NodeList(invokeMethodArgs),
        )
    }

    override fun renderGetInstanceField(instance: Expression, field: JcField): Expression {
        return if (isAccessibleFromTestClass(field.enclosingClass))
            springGetInstanceField(instance, field)
        else
            super.renderGetInstanceField(instance, field)
    }

    private fun springGetInstanceField(instance: Expression, field: JcField): Expression {
        val call = MethodCallExpr(
            springTestUtilsName,
            "getField",
            NodeList(instance, StringLiteralExpr(field.name))
        )
        return CastExpr(blockRenderer.renderType(fieldType(field)), call)
    }

    override fun renderGetStaticField(field: JcField): Expression {
        return if (isAccessibleFromTestClass(field.enclosingClass))
            springGetStaticField(field)
        else
            super.renderGetStaticField(field)
    }

    private fun springGetStaticField(field: JcField): Expression {
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

    override fun renderSetInstanceField(instance: Expression, field: JcField, value: Expression): Expression {
        return if (isAccessibleFromTestClass(field.enclosingClass) && !field.enclosingClass.isRecord)
            springSetInstanceField(instance, field, value)
        else
            super.renderSetInstanceField(instance, field, value)
    }

    private val JcClassOrInterface.isRecord: Boolean get() = (access and Opcodes.ACC_RECORD) != 0

    private fun springSetInstanceField(instance: Expression, field: JcField, value: Expression): Expression {
        return MethodCallExpr(
            springTestUtilsName,
            "setField",
            NodeList(instance, StringLiteralExpr(field.name), value),
        )
    }

    override fun renderSetStaticField(field: JcField, value: Expression): Expression {
        return if (isAccessibleFromTestClass(field.enclosingClass) && !field.isFinal)
            springSetStaticField(field, value)
        else
            super.renderSetStaticField(field, value)
    }

    private fun springSetStaticField(field: JcField, value: Expression): Expression {
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
