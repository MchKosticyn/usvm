package utils

import machine.concreteMemory.JcConcreteMemory
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.ext.findType
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.api.util.JcTestStateResolver
import org.usvm.machine.JcContext
import org.usvm.memory.UReadOnlyMemory
import org.usvm.model.UModelBase

abstract class JcConcreteStateResolver<T>(
    ctx: JcContext,
    model: UModelBase<JcType>,
    finalStateMemory: UReadOnlyMemory<JcType>,
    method: JcTypedMethod,
) : JcTestStateResolver<T>(ctx, model, finalStateMemory, method) {

    val concreteMemory = finalStateMemory as JcConcreteMemory

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
