package org.usvm.machine.state.concreteMemory.concreteMemoryRegions

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.memory.foldHeapRef

@Suppress("UNCHECKED_CAST")
fun handleRefForWrite(ref: UHeapRef, guard: UBoolExpr, handleIteCase: (UConcreteHeapAddress) -> Unit): UHeapRef? {
    if (ref !is UIteExpr<*>)
        return ref

    if (ref.condition.isTrue)
        return ref.trueBranch as UHeapRef

    if (ref.condition.isFalse)
        return ref.falseBranch as UHeapRef

    foldHeapRef(
        ref,
        null,
        guard,
        ignoreNullRefs = true,
        collapseHeapRefs = true,
        staticIsConcrete = true,
        blockOnConcrete = { _, (concreteRef, guard) -> if (!guard.isFalse) handleIteCase(concreteRef.address); null },
        blockOnSymbolic = { _, _ -> null }
    )

    return null
}
