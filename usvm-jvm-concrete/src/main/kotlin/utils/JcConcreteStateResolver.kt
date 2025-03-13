package utils

import machine.concreteMemory.JcConcreteMemory
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.ext.findType
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.api.util.JcTestStateResolver
import org.usvm.jvm.util.toTypedMethod
import org.usvm.machine.state.JcState

abstract class JcConcreteStateResolver<T>(
    state: JcState,
) : JcTestStateResolver<T>(state.ctx, state.models.first(), state.memory, state.entrypoint.toTypedMethod) {
    val concreteMemory = (state.memory as JcConcreteMemory)

    override fun resolveObject(ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcClassType): T {
        if (type == ctx.stringType) {
            val string = concreteMemory.tryHeapRefToObject(ref) as String?
                ?: return super.resolveObject(ref, heapRef, type)
            return decoderApi.createStringConst(string)
        }

        if (type == ctx.classType) {
            val clazz = concreteMemory.tryHeapRefToObject(ref) as Class<*>?
                ?: return super.resolveObject(ref, heapRef, type)
            return decoderApi.createClassConst(ctx.cp.findType(clazz.name))
        }

        return super.resolveObject(ref, heapRef, type)
    }
}
