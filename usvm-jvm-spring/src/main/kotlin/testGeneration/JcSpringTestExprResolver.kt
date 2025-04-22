package testGeneration

import machine.state.pinnedValues.JcPinnedValue
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedMethod
import org.usvm.api.util.JcTestStateResolver
import org.usvm.machine.JcContext
import org.usvm.memory.UReadOnlyMemory
import org.usvm.model.UModelBase
import org.usvm.test.api.JcTestExecutorDecoderApi
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestExpression
import utils.JcConcreteStateResolver

class JcSpringTestExprResolver(
    ctx: JcContext,
    model: UModelBase<JcType>,
    finalStateMemory: UReadOnlyMemory<JcType>,
    method: JcTypedMethod,
) : JcConcreteStateResolver<UTestExpression>(ctx, model, finalStateMemory, method) {

    override val decoderApi = JcTestExecutorDecoderApi(ctx.cp)

    override var resolveMode: ResolveMode = ResolveMode.CURRENT

    override fun <R> withMode(resolveMode: ResolveMode, body: JcTestStateResolver<UTestExpression>.() -> R): R {
        check(resolveMode == ResolveMode.CURRENT && this.resolveMode == ResolveMode.CURRENT)
        return body()
    }

    override fun allocateClassInstance(type: JcClassType) = UTestAllocateMemoryCall(type.jcClass)
    override fun allocateString(value: UTestExpression) = value
    fun resolvePinnedValue(value: JcPinnedValue) = resolveExpr(value.getExpr(), value.getType())
    fun getInstructions() = decoderApi.initializerInstructions()
}
