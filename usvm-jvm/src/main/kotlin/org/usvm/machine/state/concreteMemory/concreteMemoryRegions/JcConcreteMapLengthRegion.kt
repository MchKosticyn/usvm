package org.usvm.machine.state.concreteMemory.concreteMemoryRegions

import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.int
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.api.SymbolicIdentityMap
import org.usvm.api.SymbolicMap
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.length.UMapLengthRegion
import org.usvm.collection.map.length.UMapLengthRegionId
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.isTrue
import org.usvm.machine.JcContext
import org.usvm.machine.USizeSort
import org.usvm.machine.state.concreteMemory.JcConcreteMemoryBindings
import org.usvm.machine.state.concreteMemory.Marshall
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.mapWithStaticAsConcrete

internal class JcConcreteMapLengthRegion(
    private val regionId: UMapLengthRegionId<JcType, USizeSort>,
    private val ctx: JcContext,
    private val bindings: JcConcreteMemoryBindings,
    private var baseRegion: UMapLengthRegion<JcType, USizeSort>,
    private val marshall: Marshall,
    private val ownership: MutabilityOwnership
) : UMapLengthRegion<JcType, USizeSort>, JcConcreteRegion {

    private val lengthType by lazy { ctx.cp.int }

    private fun writeToBase(
        key: UMapLengthLValue<JcType, USizeSort>,
        value: UExpr<USizeSort>,
        guard: UBoolExpr
    ) {
        baseRegion = baseRegion.write(key, value, guard, ownership) as UMapLengthRegion<JcType, USizeSort>
    }

    private fun readConcrete(ref: UConcreteHeapRef, key: UMapLengthLValue<JcType, USizeSort>): UExpr<USizeSort> {
        if (bindings.contains(ref.address)) {
            val lengthObj = bindings.readMapLength(ref.address)
            return marshall.objToExpr(lengthObj, lengthType)
        }

        return baseRegion.read(key)
    }

    override fun read(key: UMapLengthLValue<JcType, USizeSort>): UExpr<USizeSort> {
        return key.ref.mapWithStaticAsConcrete(
            concreteMapper = { readConcrete(it, key) },
            symbolicMapper = { baseRegion.read(key) },
            ignoreNullRefs = true
        )
    }

    override fun write(
        key: UMapLengthLValue<JcType, USizeSort>,
        value: UExpr<USizeSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership
    ): UMemoryRegion<UMapLengthLValue<JcType, USizeSort>, USizeSort> {
        check(this.ownership == ownership)
        val ref = handleRefForWrite(key.ref, guard) {
            marshall.unmarshallMap(it, key.mapType)
        }

        if (ref == null) {
            writeToBase(key, value, guard)
            return this
        }

        if (ref is UConcreteHeapRef && bindings.contains(ref.address)) {
            val address = ref.address
            val lengthObj = marshall.tryExprToObj(value, lengthType)
            val isConcreteWrite = lengthObj.isSome && guard.isTrue
            if (isConcreteWrite) {
                bindings.writeMapLength(address, lengthObj.getOrThrow() as Int)
                return this
            }

            marshall.unmarshallMap(address, key.mapType)
        }

        writeToBase(key, value, guard)

        return this
    }

    private fun unmarshallLength(ref: UConcreteHeapRef, size: Int) {
        val key = UMapLengthLValue(ref, regionId.mapType, regionId.sort)
        val length = marshall.objToExpr<USizeSort>(size, lengthType)
        writeToBase(key, length, ctx.trueExpr)
    }

    fun unmarshallLength(ref: UConcreteHeapRef, obj: Map<*, *>) {
        unmarshallLength(ref, obj.size)
    }

    fun unmarshallLength(ref: UConcreteHeapRef, obj: SymbolicMap<*, *>) {
        unmarshallLength(ref, obj.size())
    }

    fun unmarshallLength(ref: UConcreteHeapRef, obj: SymbolicIdentityMap<*, *>) {
        unmarshallLength(ref, obj.size())
    }

    fun copy(
        bindings: JcConcreteMemoryBindings,
        marshall: Marshall,
        ownership: MutabilityOwnership
    ): JcConcreteMapLengthRegion {
        return JcConcreteMapLengthRegion(
            regionId,
            ctx,
            bindings,
            baseRegion,
            marshall,
            ownership
        )
    }
}
