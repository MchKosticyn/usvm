package machine.instructions

import machine.memory.JcMockedMethodsValue
import machine.mockedMethodsValues
import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcParameter
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.bytecode.JcMethodImpl
import org.jacodb.impl.types.AnnotationInfo
import org.jacodb.impl.types.MethodInfo
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.NULL_ADDRESS
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.typeStreamOf
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
import org.usvm.test.api.UTestInstList
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestMockObject

//class WrappedMethod(
//    private val delegate: JcMethod,
//    val methodName: String
//) : JcMethod by delegate


fun createUTestMockConfigInfo(list: List<List<Pair<UTestInst, String>>>): UTestMockConfigInfo {
    val instructions = list.flatten()
    return UTestMockConfigInfo(instructions)
}
fun createUTestMockConfigInfo2(list: List<List<Pair<UTestExpression, Pair<String, JcMethod>>>>): UTestMockConfigInfo2  {
    val resultByClass =
        list
            .flatten()
            .groupBy {
                (_, method) -> method.second.enclosingClass
            }
    val resultByMethod =
        resultByClass.mapValues { (_, pairs) ->
            pairs.groupBy(
                keySelector = { (_, method) -> method },
                valueTransform = { (expr, _) -> expr }
            )
        }

    val final = mutableListOf<UTestMockObject>()
    for ((type, classMethods) in resultByMethod) {
        val fields = HashMap<JcField, UTestExpression>()
        val methods = HashMap<JcMethod, List<UTestExpression>>()
        for ((mockedMethod, expr) in classMethods) {
            val m = mockedMethod.second.toTypedMethod
            val exprs = mutableListOf<UTestExpression>()
            val oldExprs = methods[mockedMethod.second]
            if (oldExprs != null) {
                exprs += oldExprs
            }
            exprs += expr
            methods[mockedMethod.second] = exprs
        }
        val mockObj = UTestMockObject(type.toType(), fields, methods)
        final.add(mockObj)
    }
    return UTestMockConfigInfo2(final)
}

fun createUTestInstructions(
    key: JcMockedMethodsValue<USort>,
//    method: JcTypedMethod,
    state: JcState
): List<Pair<UTestInst, String>> {
    val model = state.models.first()
    val ctx = state.ctx
    val memoryScope = MemoryScope(ctx, model, state.memory, key.method.toTypedMethod)

    return memoryScope.createUTestInstructions(key)
}
fun cc (key: JcMockedMethodsValue<USort>, state: JcState): List<Pair<UTestExpression, Pair<String, JcMethod>>> {
    val model = state.models.first()
    val ctx = state.ctx
    val memoryScope = MemoryScope(ctx, model, state.memory, key.method.toTypedMethod)

    return memoryScope.c(key)
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
    fun createUTestInstructions(key: JcMockedMethodsValue<USort>): List<Pair<UTestInst, String>> {
        val newMap : Map<JcMockedMethodsValue<USort>, UExpr<USort>> = mockedMethodsValues.toMap()
        return withMode(ResolveMode.CURRENT) {
        val list = mutableListOf<Pair<UTestInst, String>>()
        val parameters = resolveParameters()
//        for (key in newMap.keys) {
//            val m = newMap[key]
//            val resolved = resolveExpr(m as UExpr<out USort>, key.type)
//            list.add(Pair(UTestMethodCall(resolved, key.method, parameters), key.mockedMethod.method))
//        }
        val m = newMap[key]
        val resolved = resolveExpr(m as UExpr<out USort>, key.type)
      list.add(Pair(UTestMethodCall(resolved, method.method, parameters), key.mockedMethod.str))
//        list.add(Pair(UTestMethodCall(resolved, method.method, mutableListOf()), key.mockedMethod.method))
        val initStmts = this@MemoryScope.decoderApi.initializerInstructions()
        for (initStmt in initStmts) {
            list.add(Pair(initStmt, ""))
        }
        list}
    }
    fun c (key: JcMockedMethodsValue<USort>): List<Pair<UTestExpression, Pair<String, JcMethod>>> {
        val newMap : Map<JcMockedMethodsValue<USort>, UExpr<USort>> = mockedMethodsValues.toMap()
        return withMode(ResolveMode.CURRENT) {
            val list = mutableListOf<Pair<UTestExpression, Pair<String, JcMethod>>>()
            val m = newMap[key]
            val resolved = resolveExpr(m as UExpr<out USort>, key.type)
            list.add(Pair(resolved, Pair(key.mockedMethod.method, key.method)))
            val initStmts = this@MemoryScope.decoderApi.initializerInstructions()
            if (initStmts.isNotEmpty()) {
                list.add(Pair(UTestInstList(initStmts), Pair(key.mockedMethod.method, key.method)))
            }
            list}

    }
}