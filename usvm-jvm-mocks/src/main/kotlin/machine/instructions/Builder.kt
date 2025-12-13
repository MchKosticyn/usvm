package machine.instructions

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
        return withMode(ResolveMode.CURRENT) {
        val list = mutableListOf<Pair<UTestInst, String>>()
        val argsList = mutableListOf<UTestExpression>()
        val parameters = resolveParameters()
        for (key in mockedMethodsValues.keys) {
            val m = mockedMethodsValues[key] as UExpr<out USort>
            val resolved = resolveExpr(m, key.type)
            list.add(Pair(UTestMethodCall(resolved, method.method, parameters), key.mockedMethod.method))
        }
        UTestMockConfigInfo(list)}
    }
    override fun resolveReference(heapRef: UHeapRef, type: JcRefType): UTestExpression {
        return super.resolveReference(heapRef, type)
    }
//        val ref = evaluateInModel(heapRef) as UConcreteHeapRef
//        if (ref.address == NULL_ADDRESS) {
//            return decoderApi.createNullConst(type)
//        }
//
//        val obj = if (resolveMode == ResolveMode.CURRENT) {
//            tryCreateObjectInstance(heapRef)
//        } else null
//
//        if (obj != null) {
//            saveResolvedRef(ref.address, obj)
//            return obj
//        }
//
//        // to find a type, we need to understand the source of the object
////        val typeStream = if (ref.address <= INITIAL_INPUT_ADDRESS) {
////            // input object
////            model.typeStreamOf(ref)
////        } else {
////            // allocated object
////            memory.typeStreamOf(ref)
////        }
////            .filterBySupertype(type)
//
//        // We filter allocated object type stream, because it could be stored in the input array,
//        // which resolved to a wrong type, since we do not build connections between element types
//        // and array types right now.
//        // In such cases, we need to resolve this element to null.
//
//        val evaluatedType = type.jcClass
//            ?: return decoderApi.createNullConst(type)
//
//        // We check for the type stream emptiness first and only then for the resolved cache,
//        // because even if the object is already resolved, it could be incompatible with the [type], if it
//        // is an element of an array of the wrong type.
//
//        return resolveRef(ref.address) {
//            when (type) {
//                is JcArrayType -> resolveArray(ref, heapRef, type)
//                is JcClassType -> resolveObject(ref, heapRef, type)
//                else -> error("Unexpected type: $type")
//            }
//        }
//    }

}