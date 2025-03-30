package utils

import machine.concreteMemory.JcConcreteMemory
import machine.concreteMemory.JcConcreteMemoryBindings
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.ext.findType
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.api.decoder.DecoderApi
import org.usvm.api.util.JcTestStateResolver
import org.usvm.machine.state.JcState

abstract class JcConcreteStateResolver<T>(
    state: JcState,
    override val decoderApi: DecoderApi<T>
) : JcTestStateResolver<T>(state.ctx, state.models.first(), state.memory, state.entrypoint.toTypedMethod) {
    val bindings = (state.memory as JcConcreteMemory)

    override fun resolveObject(ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcClassType): T {
        if (type == ctx.stringType && bindings.contains(ref.address)) {
            val string = bindings.virtToPhys(ref.address) as String
            return decoderApi.createStringConst(string)
        }

        if (type == ctx.classType && bindings.contains(ref.address)) {
            val clazz = bindings.virtToPhys(ref.address) as Class<*>
            return decoderApi.createClassConst(ctx.cp.findType(clazz.name))
        }

        return super.resolveObject(ref, heapRef, type)
    }
}