package org.usvm.machine.state.concreteMemory

internal data class PhysicalAddress(
    val obj: Any?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PhysicalAddress

        return obj === other.obj
    }

    override fun hashCode(): Int {
        return System.identityHashCode(obj)
    }

    val isNull: Boolean by lazy {
        obj == null
    }
}
