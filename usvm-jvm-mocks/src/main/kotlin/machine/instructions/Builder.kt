package machine.instructions

import machine.memory.JcMockedMethodsValue
import machine.mockedMethodsValues
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedMethod
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.util.JcTestStateResolver
import org.usvm.jvm.util.toTypedMethod
import org.usvm.machine.JcContext
import org.usvm.machine.state.JcState
import org.usvm.memory.UReadOnlyMemory
import org.usvm.model.UModelBase
import org.usvm.test.api.JcTestExecutorDecoderApi
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestMockInst

/**
 * createUTestMockConfigInfo() simply combines lists of test instructions into a single one by flattening it
 * and creates UTestMockConfigInfo for render.
 */
fun createUTestMockConfigInfo(list: List<List<Pair<UTestInst, String>>>): UTestMockConfigInfo {
    val instructions = list.flatten()
    return UTestMockConfigInfo(instructions)
}

/**
 * createUTestInstructions() takes key and state, passes information on to MemoryScope,
 * which handles resolving values and returns UTestMockInst with additional meta info.
 */
fun createUTestInstructions(
    key: JcMockedMethodsValue<USort>,
    state: JcState
): List<Pair<UTestInst, String>> {
    val model = state.models.first()
    val ctx = state.ctx
    val memoryScope = MemoryScope(ctx, model, state.memory, key.method.toTypedMethod)

    return memoryScope.createUTestInstructions(key)
}

private class MemoryScope(
    ctx: JcContext,
    model: UModelBase<JcType>,
    finalStateMemory: UReadOnlyMemory<JcType>,
    method: JcTypedMethod
) : JcTestStateResolver<UTestExpression>(ctx, model, finalStateMemory, method) {
    override val decoderApi = JcTestExecutorDecoderApi(ctx.cp)
    override fun allocateClassInstance(type: JcClassType): UTestExpression =
        UTestAllocateMemoryCall(type.jcClass)
    fun createUTestInstructions(key: JcMockedMethodsValue<USort>): List<Pair<UTestInst, String>> {
        val newMap: Map<JcMockedMethodsValue<USort>, UExpr<USort>> = mockedMethodsValues.toMap()
        return withMode(ResolveMode.CURRENT) {
            val list = mutableListOf<Pair<UTestInst, String>>()
            val parameters = resolveParameters()
            val m = newMap[key]
            val resolved = resolveExpr(m as UExpr<out USort>, key.type)
            val pairToAdd = (Pair(UTestMockInst(resolved, method.method, parameters), key.mockedMethod.str))
            val initStmts = this@MemoryScope.decoderApi.initializerInstructions()
            for (initStmt in initStmts) {
                list.add(Pair(initStmt, ""))
            }
            list.add(pairToAdd)
            list
        }
    }
}
