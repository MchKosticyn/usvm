package org.usvm

data class GuardedExpr<out T>(
    val expr: T,
    val guard: UBoolExpr,
)

infix fun <T> T.with(guard: UBoolExpr) = GuardedExpr(this, guard)

infix fun <T> GuardedExpr<T>.withAlso(guard: UBoolExpr) = GuardedExpr(expr, guard.ctx.mkAnd(this.guard, guard))

/**
 * @param concreteHeapRefs a list of split concrete heap refs with their guards.
 * @param symbolicHeapRef an ite made of all [USymbolicHeapRef]s with its guard, the single [USymbolicHeapRef] if it's
 * the single [USymbolicHeapRef] in the base expression or `null` if there are no [USymbolicHeapRef]s at all.
 */
internal data class SplitHeapRefs(
    val concreteHeapRefs: List<GuardedExpr<UConcreteHeapRef>>,
    val symbolicHeapRef: GuardedExpr<UHeapRef>?,
)

/**
 * Traverses the [ref] non-recursively and collects [UConcreteHeapRef]s and [USymbolicHeapRef] as well as
 * guards for them. The result [SplitHeapRefs.symbolicHeapRef] will be `null` if there are no [USymbolicHeapRef]s as
 * leafs in the [ref] ite. Otherwise, it will contain an ite with the guard protecting from bubbled up concrete refs.
 *
 * @param initialGuard an initial value for the accumulated guard.
 */
internal fun splitUHeapRef(ref: UHeapRef, initialGuard: UBoolExpr = ref.ctx.trueExpr): SplitHeapRefs {
    val concreteHeapRefs = mutableListOf<GuardedExpr<UConcreteHeapRef>>()

    val symbolicHeapRef = filter(ref, initialGuard) { guarded ->
        if (guarded.expr is USymbolicHeapRef) {
            true
        } else {
            @Suppress("UNCHECKED_CAST")
            concreteHeapRefs += (guarded as GuardedExpr<UConcreteHeapRef>)
            false
        }
    }

    return SplitHeapRefs(
        concreteHeapRefs,
        symbolicHeapRef,
    )
}

/**
 * Traverses the [ref], accumulating guards and applying the [blockOnConcrete] on [UConcreteHeapRef]s and
 * [blockOnSymbolic] on [USymbolicHeapRef]. An argument for the [blockOnSymbolic] is obtained by removing all concrete
 * heap refs from the [ref] if it's ite.
 *
 * @param initialGuard the initial value fot the guard to be passed to [blockOnConcrete] and [blockOnSymbolic].
 */
internal inline fun withHeapRef(
    ref: UHeapRef,
    initialGuard: UBoolExpr,
    crossinline blockOnConcrete: (GuardedExpr<UConcreteHeapRef>) -> Unit,
    crossinline blockOnSymbolic: (GuardedExpr<UHeapRef>) -> Unit,
) {
    if (initialGuard.isFalse) {
        return
    }

    when (ref) {
        is UConcreteHeapRef -> blockOnConcrete(ref with initialGuard)
        is USymbolicHeapRef -> blockOnSymbolic(ref with initialGuard)
        is UIteExpr<UAddressSort> -> {
            val (concreteHeapRefs, symbolicHeapRef) = splitUHeapRef(ref, initialGuard)

            symbolicHeapRef?.let { (ref, guard) -> blockOnSymbolic(ref with guard) }
            concreteHeapRefs.onEach { (ref, guard) -> blockOnConcrete(ref with guard) }
        }

        else -> error("Unexpected ref: $ref")
    }
}

private const val LEFT_CHILD = 0
private const val RIGHT_CHILD = 1
private const val DONE = 2


/**
 * Reassembles [this] non-recursively with applying [concreteMapper] on [UConcreteHeapRef] and
 * [symbolicMapper] on [USymbolicHeapRef]. Respects [UIteExpr], so the structure of the result expression will be
 * the same as [this] is, but implicit simplifications may occur.
 */
internal inline fun <Sort : USort> UHeapRef.map(
    crossinline concreteMapper: (UConcreteHeapRef) -> UExpr<Sort>,
    crossinline symbolicMapper: (USymbolicHeapRef) -> UExpr<Sort>,
): UExpr<Sort> = when (this) {
    is UConcreteHeapRef -> concreteMapper(this)
    is USymbolicHeapRef -> symbolicMapper(this)
    is UIteExpr<UAddressSort> -> {
        /**
         * This code simulates DFS on a binary tree without an explicit recursion. Pair.second represents the first
         * unprocessed child of the pair.first (`0` means the left child, `1` means the right child).
         */
        val nodeToChild = mutableListOf<Pair<UHeapRef, Int>>()
        val completelyMapped = mutableListOf<UExpr<Sort>>()

        nodeToChild.add(this to LEFT_CHILD)

        while (nodeToChild.isNotEmpty()) {
            val (ref, state) = nodeToChild.removeLast()
            when (ref) {
                is UConcreteHeapRef -> completelyMapped += concreteMapper(ref)
                is USymbolicHeapRef -> completelyMapped += symbolicMapper(ref)
                is UIteExpr<UAddressSort> -> {
                    when (state) {
                        LEFT_CHILD -> {
                            nodeToChild += ref to RIGHT_CHILD
                            nodeToChild += ref.trueBranch to LEFT_CHILD
                        }
                        RIGHT_CHILD -> {
                            nodeToChild += ref to DONE
                            nodeToChild += ref.falseBranch to LEFT_CHILD
                        }
                        DONE -> {
                            // we firstly process the left child of [cur], so it will be under the top of the stack
                            // the top of the stack will be the right child
                            val rhs = completelyMapped.removeLast()
                            val lhs = completelyMapped.removeLast()
                            completelyMapped += ctx.mkIte(ref.condition, lhs, rhs)
                        }
                    }
                }
            }
        }

        completelyMapped.single()
    }

    else -> error("Unexpected ref: $this")
}

/**
 * Filters [ref] non-recursively with [predicate] and returns the result. A guard in the argument of the
 * [predicate] consists of a predicate from the root to the passed leaf.
 *
 * It's guaranteed that [predicate] will be called exactly once on each leaf.
 *
 * @return A guarded expression with the guard indicating that any leaf on which [predicate] returns `false`
 * is inaccessible. `Null` is returned when all leafs match [predicate].
 */
internal inline fun filter(
    ref: UHeapRef,
    initialGuard: UBoolExpr,
    crossinline predicate: (GuardedExpr<UHeapRef>) -> Boolean,
): GuardedExpr<UHeapRef>? = with(ref.ctx) {
    when (ref) {
        is USymbolicHeapRef,
        is UConcreteHeapRef,
        -> (ref with initialGuard).takeIf(predicate)
        is UIteExpr<UAddressSort> -> {
            /**
             * This code simulates DFS on a binary tree without an explicit recursion. Pair.second represents the first
             * unprocessed child of the pair.first, or all childs processed if pair.second equals [DONE].
             */
            val nodeToChild = mutableListOf<Pair<GuardedExpr<UHeapRef>, Int>>()
            val completelyMapped = mutableListOf<GuardedExpr<UHeapRef>?>()

            nodeToChild.add((ref with initialGuard) to LEFT_CHILD)

            while (nodeToChild.isNotEmpty()) {
                val (guarded, state) = nodeToChild.removeLast()
                val (cur, guardFromTop) = guarded
                when (cur) {
                    is USymbolicHeapRef,
                    is UConcreteHeapRef,
                    -> completelyMapped += (cur with trueExpr).takeIf { predicate(cur with guardFromTop) }
                    is UIteExpr<UAddressSort> -> when (state) {
                        LEFT_CHILD -> {
                            nodeToChild += guarded to RIGHT_CHILD
                            val leftGuard = mkAndNoFlat(guardFromTop, cur.condition)
                            nodeToChild += (cur.trueBranch with leftGuard) to LEFT_CHILD
                        }
                        RIGHT_CHILD -> {
                            nodeToChild += guarded to DONE
                            val guardRhs = mkAndNoFlat(guardFromTop, !cur.condition)
                            nodeToChild += (cur.falseBranch with guardRhs) to LEFT_CHILD
                        }
                        DONE -> {
                            // we firstly process the left child of [cur], so it will be under the top of the stack
                            // the top of the stack will be the right child
                            val rhs = completelyMapped.removeLast()
                            val lhs = completelyMapped.removeLast()
                            val next = when {
                                lhs != null && rhs != null -> {
                                    val leftPart = mkOrNoFlat(!cur.condition, lhs.guard)

                                    val rightPart = mkOrNoFlat(cur.condition, rhs.guard)

                                    /**
                                     *```
                                     *           cur.condition | guard = ( cur.condition -> lhs.guard) &&
                                                   /        \            (!cur.condition -> rhs.guard)
                                     *            /          \
                                     *           /            \
                                     *          /              \
                                     *         /                \
                                     * lhs.expr | lhs.guard   rhs.expr | rhs.guard
                                     *```
                                     */
                                    val guard = mkAndNoFlat(leftPart, rightPart)
                                    mkIte(cur.condition, lhs.expr, rhs.expr) with guard
                                }
                                lhs != null -> {
                                    val guard = mkAndNoFlat(listOf(cur.condition, lhs.guard))
                                    lhs.expr with guard
                                }
                                rhs != null -> {
                                    val guard = mkAndNoFlat(listOf(!cur.condition, rhs.guard))
                                    rhs.expr with guard
                                }
                                else -> null
                            }
                            completelyMapped += next
                        }
                    }
                }

            }

            completelyMapped.single()?.withAlso(initialGuard)
        }

        else -> error("Unexpected ref: $ref")
    }
}