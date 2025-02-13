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
import org.usvm.memory.mapWithStaticAsConcrete
import org.usvm.util.jcTypeOf
import org.usvm.util.onSome

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

        val handledSrcRef = handleRefForWrite(srcRef, operationGuard) {
            marshall.unmarshallMap(it, mapType)
        }

        val handledDstRef = handleRefForWrite(dstRef, operationGuard) {
            marshall.unmarshallMap(it, mapType)
        }

        if (handledSrcRef == null || handledDstRef == null) {
            baseRegion = baseRegion.merge(srcRef, dstRef, mapType, sort, keySet, operationGuard, ownership)
            return this
        }

        if (handledSrcRef is UConcreteHeapRef &&
            bindings.contains(handledSrcRef.address) &&
            handledDstRef is UConcreteHeapRef &&
            bindings.contains(handledDstRef.address)
        ) {
            val isConcreteCopy = operationGuard.isTrue
            if (isConcreteCopy) {
                bindings.mapMerge(handledSrcRef.address, handledDstRef.address)
                return this
            }
        }

        if (handledSrcRef is UConcreteHeapRef)
            marshall.unmarshallMap(handledSrcRef.address, mapType)

        if (handledDstRef is UConcreteHeapRef)
            marshall.unmarshallMap(handledDstRef.address, mapType)

        baseRegion = baseRegion.merge(handledSrcRef, handledDstRef, mapType, sort, keySet, operationGuard, ownership)

        return this
    }

    private fun readConcrete(ref: UConcreteHeapRef, key: URefMapEntryLValue<JcType, ValueSort>): UExpr<ValueSort> {
        if (bindings.contains(ref.address)) {
            val address = ref.address
            val objType = ctx.cp.objectType
            val keyObj = marshall.tryExprToObj(key.mapKey, objType)
            keyObj.onSome {
                val valueObj = bindings.readMapValue(address, it)
                return marshall.objToExpr(valueObj, objType)
            }

            marshall.unmarshallMap(address, mapType)
        }

        return baseRegion.read(key)
    }

    override fun read(key: URefMapEntryLValue<JcType, ValueSort>): UExpr<ValueSort> {
        check(key.mapType == mapType)
        return key.mapRef.mapWithStaticAsConcrete(
            concreteMapper = { readConcrete(it, key) },
            symbolicMapper = { baseRegion.read(key) },
            ignoreNullRefs = true
        )
    }

    override fun write(
        key: URefMapEntryLValue<JcType, ValueSort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership
    ): UMemoryRegion<URefMapEntryLValue<JcType, ValueSort>, ValueSort> {
        check(this.ownership == ownership)
        check(key.mapType == mapType)
        val ref = handleRefForWrite(key.mapRef, guard) {
            marshall.unmarshallMap(it, mapType)
        }

        if (ref == null) {
            writeToBase(key, value, guard)
            return this
        }

        if (ref is UConcreteHeapRef && bindings.contains(ref.address)) {
            val address = ref.address
            val valueObj = marshall.tryExprToObj(value, ctx.cp.objectType)
            val keyObj = marshall.tryExprToObj(key.mapKey, ctx.cp.objectType)
            val isConcreteWrite = valueObj.isSome && keyObj.isSome && guard.isTrue
            if (isConcreteWrite) {
                bindings.writeMapValue(address, keyObj.getOrThrow(), valueObj.getOrThrow())
                return this
            }

            marshall.unmarshallMap(address, mapType)
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
