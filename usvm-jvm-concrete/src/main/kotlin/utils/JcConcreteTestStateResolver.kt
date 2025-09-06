package utils

import machine.state.concreteMemory.JcConcreteMemory
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedField
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.findTypeOrNull
import org.jacodb.api.jvm.ext.isAssignable
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.api.util.JcTestStateResolver
import org.usvm.jvm.util.isThrowable
import org.usvm.machine.JcContext
import org.usvm.memory.UReadOnlyMemory
import org.usvm.model.UModelBase

abstract class JcConcreteTestStateResolver<T>(
    ctx: JcContext,
    model: UModelBase<JcType>,
    finalStateMemory: UReadOnlyMemory<JcType>,
    method: JcTypedMethod,
) : JcTestStateResolver<T>(ctx, model, finalStateMemory, method) {

    protected val concreteMemory = finalStateMemory as JcConcreteMemory

    override fun resolveObject(ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcClassType): T {
        if (type == ctx.stringType) {
            val string = concreteMemory.tryHeapRefToObject(ref) as String?
                ?: return super.resolveObject(ref, heapRef, type)
            return decoderApi.createStringConst(string)
        }

        if (type == ctx.classType) {
            val clazz = concreteMemory.tryHeapRefToObject(ref) as Class<*>?
                ?: return super.resolveObject(ref, heapRef, type)
            return decoderApi.createClassConst(ctx.cp.findType(clazz.typeName))
        }

        return super.resolveObject(ref, heapRef, type)
    }

    override fun shouldIgnoreField(typedField: JcTypedField): Boolean {
        return super.shouldIgnoreField(typedField)
                || typedField.enclosingType.isThrowable && typedField.name == "stackTrace"
    }

    override fun shouldSkipInitialization(type: JcClassType): Boolean {
        return false
    }
}
