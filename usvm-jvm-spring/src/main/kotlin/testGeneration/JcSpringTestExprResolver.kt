package testGeneration

import machine.state.JcSpringState
import machine.state.pinnedValues.JcPinnedValue
import org.jacodb.api.jvm.JcClassType
import org.usvm.test.api.JcTestExecutorDecoderApi
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestStatement
import utils.JcConcreteStateResolver

class JcSpringTestExprResolver(
    state: JcSpringState
) : JcConcreteStateResolver<UTestExpression>(state) {
    private val model = state.models[0]

    private val appendedStatements: MutableList<UTestStatement> = mutableListOf()
    override val decoderApi = JcTestExecutorDecoderApi(ctx.cp)
    override fun allocateClassInstance(type: JcClassType) = UTestAllocateMemoryCall(type.jcClass)
    override fun allocateString(value: UTestExpression) = value
    fun resolvePinnedValue(value: JcPinnedValue) = resolveExpr(model.eval(value.getExpr()), value.getType())
    fun getInstructions() = decoderApi.initializerInstructions() + appendedStatements
    fun appendStatement(statements: UTestStatement) = appendedStatements.add(statements)
}
