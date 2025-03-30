package testGeneration

import machine.state.pinnedValues.JcSpringPinnedValue
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
import org.usvm.test.api.UTestStatement

class   JcSpringTestExprResolver(
    ctx: JcContext,
    private val model: UModelBase<JcType>,
    finalStateMemory: UReadOnlyMemory<JcType>,
    method: JcTypedMethod
) : JcConcreteStateResolver<UTestExpression>(ctx, model, finalStateMemory, method) {

    private val appendedStatements: MutableList<UTestStatement> = mutableListOf()
    override val decoderApi = JcTestExecutorDecoderApi(ctx.cp)
    override fun allocateClassInstance(type: JcClassType) = UTestAllocateMemoryCall(type.jcClass)
    override fun allocateString(value: UTestExpression) = value
    fun resolvePinnedValue(value: JcSpringPinnedValue) = resolveExpr(model.eval(value.getExpr()), value.getType())
    fun getInstructions() = decoderApi.initializerInstructions() + appendedStatements
    fun appendStatement(statements: UTestStatement) = appendedStatements.add(statements)
}
