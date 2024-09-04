package org.usvm.machine.state.concreteMemory.concreteMemoryRegions

import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.objectType
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.map.ref.URefMapRegion
import org.usvm.collection.map.ref.URefMapRegionId
import org.usvm.collection.set.ref.URefSetRegion
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.isTrue
import org.usvm.machine.JcContext
import org.usvm.machine.state.concreteMemory.JcConcreteMemoryBindings
import org.usvm.machine.state.concreteMemory.Marshall
import org.usvm.memory.UMemoryRegion
import org.usvm.util.jcTypeOf

internal class JcConcreteRefMapRegion<ValueSort : USort>(
    private val regionId: URefMapRegionId<JcType, ValueSort>,
    private val ctx: JcContext,
    private val bindings: JcConcreteMemoryBindings,
    private var baseRegion: URefMapRegion<JcType, ValueSort>,
    private val marshall: Marshall,
    private val ownership: MutabilityOwnership
) : URefMapRegion<JcType, ValueSort>, JcConcreteRegion {

    private val mapType = regionId.mapType
    private val valueSort = regionId.sort

    private fun writeToBase(
        key: URefMapEntryLValue<JcType, ValueSort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ) {
        baseRegion = baseRegion.write(key, value, guard, ownership) as URefMapRegion<JcType, ValueSort>
    }

    override fun merge(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        mapType: JcType,
        sort: ValueSort,
        keySet: URefSetRegion<JcType>,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership
    ): URefMapRegion<JcType, ValueSort> {
        check(this.ownership == ownership)
        if (srcRef is UConcreteHeapRef &&
            bindings.contains(srcRef.address) &&
            dstRef is UConcreteHeapRef &&
            bindings.contains(dstRef.address)
        ) {
            val isConcreteCopy = operationGuard.isTrue
            if (isConcreteCopy && bindings.mapMerge(srcRef.address, dstRef.address)) {
                return this
            }
        }

        if (srcRef is UConcreteHeapRef)
            marshall.unmarshallMap(srcRef.address, mapType)

        if (dstRef is UConcreteHeapRef)
            marshall.unmarshallMap(dstRef.address, mapType)

        baseRegion = baseRegion.merge(srcRef, dstRef, mapType, sort, keySet, operationGuard, ownership)

        return this
    }

    override fun read(key: URefMapEntryLValue<JcType, ValueSort>): UExpr<ValueSort> {
        // TODO: use key.mapType ? #CM
        val ref = key.mapRef
        if (ref is UConcreteHeapRef && bindings.contains(ref.address)) {
            val address = ref.address
            val objType = ctx.cp.objectType
            val keyObj = marshall.tryExprToObj(key.mapKey, objType)
            if (keyObj.hasValue) {
                val valueObj = bindings.readMapValue(address, keyObj.value)
                return marshall.objToExpr(valueObj, objType)
            }

            marshall.unmarshallMap(address, mapType)
        }

        return baseRegion.read(key)
    }

    override fun write(
        key: URefMapEntryLValue<JcType, ValueSort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership
    ): UMemoryRegion<URefMapEntryLValue<JcType, ValueSort>, ValueSort> {
        check(this.ownership == ownership)
        val ref = key.mapRef
        if (ref is UConcreteHeapRef && bindings.contains(ref.address)) {
            val address = ref.address
            val valueObj = marshall.tryExprToObj(value, ctx.cp.objectType)
            val keyObj = marshall.tryExprToObj(key.mapKey, ctx.cp.objectType)
            val isConcreteWrite = valueObj.hasValue && keyObj.hasValue && guard.isTrue
            if (isConcreteWrite && bindings.writeMapValue(address, keyObj.value, valueObj.value)) {
                return this
            }

            marshall.unmarshallMap(address, key.mapType)
        }

        writeToBase(key, value, guard)

        return this
    }

    @Suppress("UNCHECKED_CAST")
    private fun unmarshallEntry(ref: UConcreteHeapRef, key: Any?, value: Any?) {
        // TODO: not efficient, implement via memset
        val keyType = if (key == null) ctx.cp.objectType else ctx.jcTypeOf(key)
        val valueType = if (value == null) ctx.cp.objectType else ctx.jcTypeOf(value)
        val keyExpr = marshall.objToExpr<USort>(key, keyType) as UHeapRef
        val lvalue = URefMapEntryLValue(valueSort, ref, keyExpr, mapType)
        val rvalue = marshall.objToExpr<USort>(value, valueType) as UExpr<ValueSort>
        writeToBase(lvalue, rvalue, ctx.trueExpr)
    }

    fun unmarshallContents(ref: UConcreteHeapRef, obj: Map<*, *>) {
        for ((key, value) in obj) {
            unmarshallEntry(ref, key, value)
        }
    }

    fun copy(
        bindings: JcConcreteMemoryBindings,
        marshall: Marshall,
        ownership: MutabilityOwnership
    ): JcConcreteRefMapRegion<ValueSort> {
        return JcConcreteRefMapRegion(
            regionId,
            ctx,
            bindings,
            baseRegion,
            marshall,
            ownership
        )
    }
}
