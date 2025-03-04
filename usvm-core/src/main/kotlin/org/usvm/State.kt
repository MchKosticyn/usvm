package org.usvm

import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemory
import org.usvm.merging.UMergeable
import org.usvm.model.UModelBase
import org.usvm.targets.UTarget
import org.usvm.targets.UTargetsSet

typealias StateId = UInt

abstract class UState<Type, Method, Statement, Context, Target, State>(
    // TODO: add interpreter-specific information
    val ctx: Context,
    initOwnership: MutabilityOwnership,
    callStack: UCallStack<Method, Statement>,
    pathConstraints: UPathConstraints<Type>,
    memory: UMemory<Type, Method>,
    /**
     * A list of [UModelBase]s that satisfy the [pathConstraints].
     * Could be empty (for example, if forking without a solver).
     */
    var models: List<UModelBase<Type>>,
    var pathNode: PathNode<Statement>,
    var forkPoints: PathNode<PathNode<Statement>>,
    targets: UTargetsSet<Target, Statement>
) : UMergeable<State, Unit>, Cloneable
    where Context : UContext<*>,
          Target : UTarget<Statement, Target>,
          State : UState<Type, Method, Statement, Context, Target, State> {

    var callStack: UCallStack<Method, Statement> = callStack
        private set
    var pathConstraints: UPathConstraints<Type> = pathConstraints
        private set
    var memory: UMemory<Type, Method> = memory
        private set
    var targets: UTargetsSet<Target, Statement> = targets
        private set

    /**
     * Deterministic state id.
     * TODO: Can be replaced with overridden hashCode
     */
    var id: StateId = ctx.getNextStateId()
        private set

    var ownership: MutabilityOwnership = initOwnership
        protected set

    /**
     * Creates new state structurally identical to this.
     * If [newConstraints] is null, clones [pathConstraints]. Otherwise, uses [newConstraints] in cloned state.
     */
    @Suppress("UNCHECKED_CAST")
    open fun clone(newConstraints: UPathConstraints<Type>? = null): State {
        val clonedState = super.clone() as State
        val newThisOwnership = MutabilityOwnership()
        val cloneOwnership = MutabilityOwnership()
        val clonedConstraints = newConstraints?.also {
            this.pathConstraints.changeOwnership(newThisOwnership)
            it.changeOwnership(cloneOwnership)
        } ?: pathConstraints.clone(newThisOwnership, cloneOwnership)
        this.ownership = newThisOwnership
        clonedState.ownership = cloneOwnership
        clonedState.callStack = callStack.clone()
        clonedState.pathConstraints = clonedConstraints
        clonedState.memory = memory.clone(clonedConstraints.typeConstraints, newThisOwnership, cloneOwnership)
        clonedState.targets = targets.clone()
        clonedState.id = ctx.getNextStateId()

        return clonedState
    }

    override fun mergeWith(other: State, by: Unit): State? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UState<*, *, *, *, *, *>

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    abstract val entrypoint: Method

    val lastEnteredMethod: Method
        get() = callStack.lastMethod()

    val currentStatement: Statement
        get() = pathNode.statement

    /**
     * A property containing information about whether the state is exceptional or not.
     */
    abstract val isExceptional: Boolean
}
