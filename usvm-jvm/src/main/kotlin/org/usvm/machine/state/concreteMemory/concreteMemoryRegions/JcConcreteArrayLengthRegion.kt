package org.usvm.machine.state.concreteMemory.concreteMemoryRegions

import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.int
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.array.length.UArrayLengthsRegion
import org.usvm.collection.array.length.UArrayLengthsRegionId
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.isTrue
import org.usvm.machine.JcContext
import org.usvm.machine.USizeSort
import org.usvm.machine.state.concreteMemory.JcConcreteMemoryBindings
import org.usvm.machine.state.concreteMemory.Marshall
import org.usvm.memory.UMemoryRegion

internal class JcConcreteArrayLengthRegion(
    private val regionId: UArrayLengthsRegionId<JcType, USizeSort>,
    private val ctx: JcContext,
    private val bindings: JcConcreteMemoryBindings,
    private var baseRegion: UArrayLengthsRegion<JcType, USizeSort>,
    private val marshall: Marshall,
    private val ownership: MutabilityOwnership
) : UArrayLengthsRegion<JcType, USizeSort>, JcConcreteRegion {

    private val lengthType by lazy { ctx.cp.int }

    override fun read(key: UArrayLengthLValue<JcType, USizeSort>): UExpr<USizeSort> {
        val ref = key.ref
        if (ref is UConcreteHeapRef && bindings.contains(ref.address)) {
            val lengthObj = bindings.readArrayLength(ref.address)
            return marshall.objToExpr(lengthObj, lengthType)
        } else {
            return baseRegion.read(key)
        }
    }

    override fun write(
        key: UArrayLengthLValue<JcType, USizeSort>,
        value: UExpr<USizeSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership
    ): UMemoryRegion<UArrayLengthLValue<JcType, USizeSort>, USizeSort> {
        check(this.ownership == ownership)
        val ref = key.ref
        if (ref is UConcreteHeapRef && bindings.contains(ref.address)) {
            val address = ref.address
            val lengthObj = marshall.tryExprToObj(value, lengthType)
            val isConcreteWrite = lengthObj.hasValue && guard.isTrue
            if (isConcreteWrite) {
                bindings.writeArrayLength(address, lengthObj.value as Int)
                return this
            }

            marshall.unmarshallArray(address)
        }

        baseRegion = baseRegion.write(key, value, guard, ownership) as UArrayLengthsRegion<JcType, USizeSort>

        return this
    }

    private fun unmarshallLengthCommon(ref: UConcreteHeapRef, size: Int) {
        val key = UArrayLengthLValue(ref, regionId.arrayType, regionId.sort)
        val length = marshall.objToExpr<USizeSort>(size, lengthType)
        baseRegion = baseRegion.write(key, length, ctx.trueExpr, ownership) as UArrayLengthsRegion<JcType, USizeSort>
    }

    fun unmarshallLength(ref: UConcreteHeapRef, obj: Array<*>) {
        unmarshallLengthCommon(ref, obj.size)
    }

    fun unmarshallLength(ref: UConcreteHeapRef, obj: ByteArray) {
        unmarshallLengthCommon(ref, obj.size)
    }

    fun unmarshallLength(ref: UConcreteHeapRef, obj: ShortArray) {
        unmarshallLengthCommon(ref, obj.size)
    }

    fun unmarshallLength(ref: UConcreteHeapRef, obj: CharArray) {
        unmarshallLengthCommon(ref, obj.size)
    }

    fun unmarshallLength(ref: UConcreteHeapRef, obj: IntArray) {
        unmarshallLengthCommon(ref, obj.size)
    }

    fun unmarshallLength(ref: UConcreteHeapRef, obj: LongArray) {
        unmarshallLengthCommon(ref, obj.size)
    }

    fun unmarshallLength(ref: UConcreteHeapRef, obj: FloatArray) {
        unmarshallLengthCommon(ref, obj.size)
    }

    fun unmarshallLength(ref: UConcreteHeapRef, obj: DoubleArray) {
        unmarshallLengthCommon(ref, obj.size)
    }

    fun unmarshallLength(ref: UConcreteHeapRef, obj: BooleanArray) {
        unmarshallLengthCommon(ref, obj.size)
    }

    fun copy(
        bindings: JcConcreteMemoryBindings,
        marshall: Marshall,
        ownership: MutabilityOwnership
    ): JcConcreteArrayLengthRegion {
        return JcConcreteArrayLengthRegion(
            regionId,
            ctx,
            bindings,
            baseRegion,
            marshall,
            ownership
        )
    }
}
