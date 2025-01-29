package org.usvm.machine.state.concreteMemory

import java.lang.reflect.Field

internal interface ChildKind

internal data class FieldChildKind(
    val field: Field
) : ChildKind

internal data class ArrayIndexChildKind(
    val index: Int,
) : ChildKind

internal class ThreadLocalValueChildKind : ChildKind
