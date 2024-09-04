package org.usvm.machine.state.concreteMemory.concreteMemoryRegions

import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.objectType
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.ref.UAllocatedRefSetWithInputElements
import org.usvm.collection.set.ref.UInputRefSetWithInputElements
import org.usvm.collection.set.ref.URefSetEntries
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.collection.set.ref.URefSetRegion
import org.usvm.collection.set.ref.URefSetRegionId
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.isTrue
import org.usvm.machine.JcContext
import org.usvm.machine.state.concreteMemory.JcConcreteMemoryBindings
import org.usvm.machine.state.concreteMemory.Marshall
import org.usvm.memory.UMemoryRegion
import org.usvm.util.jcTypeOf

@Suppress("UNUSED")
internal class JcConcreteRefSetRegion(
    private val regionId: URefSetRegionId<JcType>,
    private val ctx: JcContext,
    private val bindings: JcConcreteMemoryBindings,
    private var baseRegion: URefSetRegion<JcType>,
    private val marshall: Marshall,
    private val ownership: MutabilityOwnership
) : URefSetRegion<JcType>, JcConcreteRegion {

    private val setType by lazy { regionId.setType }
    private val sort by lazy { regionId.sort }

    private fun writeToBase(key: URefSetEntryLValue<JcType>, value: UExpr<UBoolSort>, guard: UBoolExpr) {
        baseRegion = baseRegion.write(key, value, guard, ownership) as URefSetRegion<JcType>
    }

    override fun allocatedSetWithInputElements(setRef: UConcreteHeapAddress): UAllocatedRefSetWithInputElements<JcType> {
        // TODO: elems with input addresses (statics and symbolics)
        if (bindings.contains(setRef)) {
            marshall.unmarshallSet(setRef) // TODO: make efficient: create symbolic collection from set #CM
        }

        return baseRegion.allocatedSetWithInputElements(setRef)
    }

    override fun inputSetWithInputElements(): UInputRefSetWithInputElements<JcType> {
        return baseRegion.inputSetWithInputElements()
    }

    override fun union(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership
    ): URefSetRegion<JcType> {
        check(this.ownership == ownership)
        if (srcRef is UConcreteHeapRef &&
            bindings.contains(srcRef.address) &&
            dstRef is UConcreteHeapRef &&
            bindings.contains(dstRef.address)
        ) {
            val isConcreteCopy = operationGuard.isTrue
            if (isConcreteCopy && bindings.setUnion(srcRef.address, dstRef.address)) {
                return this
            }
        }

        if (srcRef is UConcreteHeapRef)
            marshall.unmarshallSet(srcRef.address)

        if (dstRef is UConcreteHeapRef)
            marshall.unmarshallSet(dstRef.address)

        baseRegion = baseRegion.union(srcRef, dstRef, operationGuard, ownership)

        return this
    }

    override fun setEntries(ref: UHeapRef): URefSetEntries<JcType> {
        if (ref is UConcreteHeapRef && bindings.contains(ref.address)) {
            marshall.unmarshallSet(ref.address) // TODO: make efficient: create set of entries
            // TODO: set of pairs (allocatedRef, element)
        }

        return baseRegion.setEntries(ref)
    }

    override fun write(
        key: URefSetEntryLValue<JcType>,
        value: UExpr<UBoolSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership
    ): UMemoryRegion<URefSetEntryLValue<JcType>, UBoolSort> {
        check(this.ownership == ownership)
        val ref = key.setRef
        if (ref is UConcreteHeapRef && bindings.contains(ref.address)) {
            val address = ref.address
            val objType = ctx.cp.objectType
            val keyObj = marshall.tryExprToObj(key.setElement, objType)
            val valueObj = marshall.tryExprToObj(value, ctx.cp.boolean)
            val isConcreteWrite = valueObj.hasValue && keyObj.hasValue && guard.isTrue
            if (isConcreteWrite && bindings.changeSetContainsElement(address, keyObj.value, valueObj.value as Boolean)) {
                return this
            }

            marshall.unmarshallSet(address)
        }

        writeToBase(key, value, guard)

        return this
    }

    override fun read(key: URefSetEntryLValue<JcType>): UExpr<UBoolSort> {
        val ref = key.setRef
        if (ref is UConcreteHeapRef && bindings.contains(ref.address)) {
            val address = ref.address
            val objType = ctx.cp.objectType
            val elem = marshall.tryExprToObj(key.setElement, objType)
            if (elem.hasValue) {
                val contains = bindings.checkSetContains(address, elem.value)
                return marshall.objToExpr(contains, ctx.cp.boolean)
            }
            marshall.unmarshallSet(address)
        }

        return baseRegion.read(key)
    }

    @Suppress("UNCHECKED_CAST")
    private fun unmarshallElement(ref: UConcreteHeapRef, element: Any?) {
        val elemType = if (element == null) ctx.cp.objectType else ctx.jcTypeOf(element)
        val elemExpr = marshall.objToExpr<USort>(element, elemType) as UHeapRef
        val lvalue = URefSetEntryLValue(ref, elemExpr, setType)
        writeToBase(lvalue, ctx.trueExpr, ctx.trueExpr)
    }

    fun unmarshallContents(ref: UConcreteHeapRef, obj: Set<*>) {
        for (elem in obj) {
            unmarshallElement(ref, elem)
        }
    }

    fun copy(
        bindings: JcConcreteMemoryBindings,
        marshall: Marshall,
        ownership: MutabilityOwnership
    ): JcConcreteRefSetRegion {
        return JcConcreteRefSetRegion(
            regionId,
            ctx,
            bindings,
            baseRegion,
            marshall,
            ownership
        )
    }
}
