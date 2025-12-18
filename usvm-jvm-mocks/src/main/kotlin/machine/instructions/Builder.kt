package machine.instructions

import machine.memory.JcMockedMethodsValue
import machine.mockedMethodsValues
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedMethod
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.NULL_ADDRESS
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.typeStreamOf
import org.usvm.api.util.JcTestStateResolver
import org.usvm.machine.JcContext
import org.usvm.machine.state.JcState
import org.usvm.memory.UReadOnlyMemory
import org.usvm.model.UModelBase
import org.usvm.test.api.JcTestExecutorDecoderApi
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestMethodCall

fun createUTestInstructions(
    method: JcTypedMethod,
    state: JcState
): UTestMockConfigInfo {
    val model = state.models.first()
    val ctx = state.ctx
    val memoryScope = MemoryScope(ctx, model, state.memory, method)

    return memoryScope.createUTestInstructions()
}

private class MemoryScope(
    ctx: JcContext,
    model: UModelBase<JcType>,
    finalStateMemory: UReadOnlyMemory<JcType>,
    method: JcTypedMethod,
) : JcTestStateResolver<UTestExpression>(ctx, model, finalStateMemory, method) {
    override val decoderApi = JcTestExecutorDecoderApi(ctx.cp)
    override fun allocateClassInstance(type: JcClassType): UTestExpression =
        UTestAllocateMemoryCall(type.jcClass) // ?
    fun createUTestInstructions(): UTestMockConfigInfo {
        val newMap : Map<JcMockedMethodsValue<USort>, UExpr<USort>> = mockedMethodsValues.toMap()
        return withMode(ResolveMode.CURRENT) {
        val list = mutableListOf<Pair<UTestInst, String>>()
        val parameters = resolveParameters()
        for (key in newMap.keys) {
            val m = newMap[key]
            val resolved = resolveExpr(m as UExpr<out USort>, key.type)
            list.add(Pair(UTestMethodCall(resolved, key.method, parameters), key.mockedMethod.method))
        }
        val initStmts = this@MemoryScope.decoderApi.initializerInstructions()
        for (initStmt in initStmts) {
            list.add(Pair(initStmt, ""))
        }
        UTestMockConfigInfo(list)}
    }
}