package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.type.ReferenceType
import java.util.IdentityHashMap
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.jvm.rendering.testRenderer.JcTestBlockRenderer
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestExpression

open class JcUnsafeTestBlockRenderer protected constructor(
    override val methodRenderer: JcUnsafeTestRenderer,
    override val importManager: JcImportManager,
    identifiersManager: JcIdentifiersManager,
    cp: JcClasspath,
    shouldDeclareVar: Set<UTestExpression>,
    exprCache: IdentityHashMap<UTestExpression, Expression>,
    thrownExceptions: HashSet<ReferenceType>,
    protected open val unsafeUtilsRenderer: JcUnsafeUtilsRenderer
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
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath,
        shouldDeclareVar: Set<UTestExpression>,
        unsafeUtilsRenderer: JcUnsafeUtilsRenderer
    ) : this(
        methodRenderer,
        importManager,
        identifiersManager,
        cp,
        shouldDeclareVar,
        IdentityHashMap(),
        HashSet(),
        unsafeUtilsRenderer
    )

    override fun newInnerBlock(): JcUnsafeTestBlockRenderer {
        return JcUnsafeTestBlockRenderer(
            methodRenderer,
            importManager,
            JcIdentifiersManager(identifiersManager),
            cp,
            shouldDeclareVar,
            IdentityHashMap(exprCache),
            thrownExceptions,
            unsafeUtilsRenderer
        )
    }

    //region Private Methods

    override fun renderPrivateCtorCall(
        ctor: JcMethod,
        type: JcClassType,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        return unsafeUtilsRenderer.renderCtorCall(this, ctor, type, args, inlinesVarargs)
    }

    override fun renderPrivateMethodCall(
        method: JcMethod,
        instance: Expression,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        return unsafeUtilsRenderer.renderInstanceMethodCall(this, method, instance, args, inlinesVarargs)
    }

    override fun renderPrivateStaticMethodCall(
        method: JcMethod,
        args: List<Expression>,
        inlinesVarargs: Boolean
    ): Expression {
        return unsafeUtilsRenderer.renderStaticMethodCall(this, method, args, inlinesVarargs)
    }

    //endregion

    //region Private Fields

    override fun renderGetPrivateStaticField(field: JcField): Expression {
        return unsafeUtilsRenderer.renderGetStaticField(field)
    }

    override fun renderGetPrivateField(instance: Expression, field: JcField): Expression {
        return unsafeUtilsRenderer.renderGetInstanceField(instance, field)
    }

    override fun renderSetPrivateStaticField(field: JcField, value: Expression): Expression {
        return unsafeUtilsRenderer.renderSetStaticField(field, value)
    }

    override fun renderSetPrivateField(instance: Expression, field: JcField, value: Expression): Expression {
        return unsafeUtilsRenderer.renderSetInstanceField(instance, field, value)
    }

    //endregion

    //region Allocation

    override fun renderAllocateMemoryCall(expr: UTestAllocateMemoryCall): Expression {
        return unsafeUtilsRenderer.renderAllocateInstance(this, expr.clazz)
    }

    //endregion
}
