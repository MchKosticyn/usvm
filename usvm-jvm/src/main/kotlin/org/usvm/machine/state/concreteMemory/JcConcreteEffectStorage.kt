package org.usvm.machine.state.concreteMemory

import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.impl.features.classpaths.JcUnknownType
import org.usvm.api.util.JcConcreteMemoryClassLoader
import org.usvm.api.util.Reflection.allocateInstance
import org.usvm.instrumentation.util.getFieldValue
import org.usvm.machine.JcContext
import java.lang.reflect.Field
import java.util.LinkedList
import java.util.Queue
import kotlin.math.min
import org.usvm.instrumentation.util.getFieldValue as getFieldValueUnsafe
import org.usvm.instrumentation.util.setFieldValue as setFieldValueUnsafe

private class JcConcreteSnapshot(
    private val ctx: JcContext,
    private val getThreadLocalValue: (threadLocal: Any) -> Any?,
    private val setThreadLocalValue: (threadLocal: Any, value: Any?) -> Unit
) {
    val objects: MutableMap<PhysicalAddress, PhysicalAddress> = mutableMapOf()
    val statics: MutableMap<Field, PhysicalAddress> = mutableMapOf()

    constructor(
        ctx: JcContext,
        getThreadLocalValue: (threadLocal: Any) -> Any?,
        setThreadLocalValue: (threadLocal: Any, value: Any?) -> Unit,
        other: JcConcreteSnapshot
    ) : this(ctx, getThreadLocalValue, setThreadLocalValue) {
        for ((phys, _) in other.objects) {
            addObjectToSnapshot(phys)
        }

        for ((field, phys) in other.statics) {
            addStaticFieldToSnapshot(field, phys)
        }
    }

    fun isEmpty(): Boolean = objects.isEmpty() && statics.isEmpty()

    private fun cloneObject(obj: Any): Any? {
        try {
            val type = obj.javaClass
            val jcType = type.toJcType(ctx) ?: return null
            return when {
                jcType is JcUnknownType -> null
                type.isImmutable -> null
                type.isProxy || type.isLambda -> null
                type.isByteBuffer -> null
                jcType is JcArrayType -> {
                    return when (obj) {
                        is IntArray -> obj.clone()
                        is ByteArray -> obj.clone()
                        is CharArray -> obj.clone()
                        is LongArray -> obj.clone()
                        is FloatArray -> obj.clone()
                        is ShortArray -> obj.clone()
                        is DoubleArray -> obj.clone()
                        is BooleanArray -> obj.clone()
                        is Array<*> -> obj.clone()
                        else -> error("cloneObject: unexpected array $obj")
                    }
                }
                type.allInstanceFields.isEmpty() -> null
                jcType is JcClassType -> {
                    val newObj = jcType.allocateInstance(JcConcreteMemoryClassLoader)
                    for (field in type.allInstanceFields) {
                        val value = field.getFieldValueUnsafe(obj)
                        field.setFieldValueUnsafe(newObj, value)
                    }
                    newObj
                }
                else -> null
            }
        } catch (e: Exception) {
//            println("cloneObject failed on class ${type.name}")
            return null
        }
    }

    fun addObjectToSnapshot(oldPhys: PhysicalAddress) {
        if (objects.containsKey(oldPhys))
            return

        val obj = oldPhys.obj!!
        val type = obj.javaClass
        if (type.isImmutable)
            return

        val clonedObj = if (type.isThreadLocal) {
            getThreadLocalValue(obj)
        } else {
            cloneObject(obj) ?: return
        }
        val clonedPhys = PhysicalAddress(clonedObj)
        objects[oldPhys] = clonedPhys
    }

    fun addObjectToSnapshotRec(obj: Any?) {
        obj ?: return
        val handledObjects: MutableSet<PhysicalAddress> = mutableSetOf()
        val queue: Queue<Any?> = LinkedList()
        queue.add(obj)
        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: continue
            val currentPhys = PhysicalAddress(current)
            if (!handledObjects.add(currentPhys) || objects.containsKey(currentPhys))
                continue

            val type = current.javaClass
            if (type.isThreadLocal) {
                val value = getThreadLocalValue(current)
                queue.add(value)
                objects[currentPhys] = PhysicalAddress(value)
                continue
            }

            addObjectToSnapshot(currentPhys)

            when {
                type.isImmutable -> continue
                current is Array<*> -> queue.addAll(current)
                type.isArray -> continue
                else -> {
                    for (field in type.allInstanceFields) {
                        try {
                            queue.add(field.getFieldValue(current))
                        } catch (e: Throwable) {
//                            println("addObjectToBacktrackRec failed on class ${type.name}")
                            continue
                        }
                    }
                }
            }
        }
    }

    fun addStaticFieldToSnapshot(field: Field, phys: PhysicalAddress) {
        if (!field.isFinal)
            statics[field] = phys
    }

    fun addStaticFieldToSnapshot(field: Field, value: Any?) {
        check(value !is PhysicalAddress)
        addStaticFieldToSnapshot(field, PhysicalAddress(value))
    }

    fun addStaticFields(type: JcClassOrInterface) {
        val fields = type.staticFields.mapNotNull { it.toJavaField }
        for (field in fields) {
            val value = field.getStaticFieldValue()
            addObjectToSnapshotRec(value)
            addStaticFieldToSnapshot(field, value)
        }
    }

    fun resetStatics() {
        for ((field, phys) in statics) {
            val value = phys.obj
            field.setStaticFieldValue(value)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun resetObjects() {
        for ((oldPhys, clonedPhys) in objects) {
            val oldObj = oldPhys.obj ?: continue
            val obj = clonedPhys.obj ?: continue
            val type = oldObj.javaClass
            check(type == obj.javaClass || type.isThreadLocal)
            when {
                type.isImmutable -> continue
                type.isThreadLocal -> setThreadLocalValue(oldObj, obj)
                type.isArray -> {
                    when {
                        obj is IntArray && oldObj is IntArray -> {
                            obj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        obj is ByteArray && oldObj is ByteArray -> {
                            obj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        obj is CharArray && oldObj is CharArray -> {
                            obj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        obj is LongArray && oldObj is LongArray -> {
                            obj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        obj is FloatArray && oldObj is FloatArray -> {
                            obj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        obj is ShortArray && oldObj is ShortArray -> {
                            obj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        obj is DoubleArray && oldObj is DoubleArray -> {
                            obj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        obj is BooleanArray && oldObj is BooleanArray -> {
                            obj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        obj is Array<*> && oldObj is Array<*> -> {
                            oldObj as Array<Any?>
                            obj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        else -> error("applyBacktrack: unexpected array $obj")
                    }
                }

                else -> {
                    for (field in type.allInstanceFields) {
                        try {
                            val value = field.getFieldValue(obj)
                            field.setFieldValueUnsafe(oldObj, value)
                        } catch (e: Exception) {
                            error("applyBacktrack class ${type.name} failed on field ${field.name}, cause: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}

private class JcConcreteEffect(
    private val ctx: JcContext,
    private val getThreadLocalValue: (threadLocal: Any) -> Any?,
    private val setThreadLocalValue: (threadLocal: Any, value: Any?) -> Unit,
    myState: JcConcreteMemoryState
) {
    val before: JcConcreteSnapshot = JcConcreteSnapshot(ctx, getThreadLocalValue, setThreadLocalValue)
    val staticsCache: MutableSet<JcClassOrInterface> = mutableSetOf()
    var after: JcConcreteSnapshot? = null
    val forks: MutableList<JcConcreteMemoryState> = mutableListOf(myState)

    private fun createAfter() {
        if (after != null)
            return

        this.after = JcConcreteSnapshot(ctx, getThreadLocalValue, setThreadLocalValue, before)
    }

    fun addStaticFields(types: List<JcClassOrInterface>) {
        for (type in types) {
            if (!staticsCache.add(type))
                continue

            before.addStaticFields(type)
        }
    }

    fun resetToBefore(): Boolean {
        val isDead = forks.all { it.isDead() }
        if (!isDead)
            createAfter()
        before.resetObjects()
        before.resetStatics()
        return isDead
    }

    fun resetToAfter() {
        check(after != null || before.isEmpty())
        val after = after ?: return
        after.resetObjects()
        after.resetStatics()
    }
}

private class JcConcreteEffectSequence private constructor(
    private var seq: ArrayDeque<JcConcreteEffect>
) {
    constructor() : this(ArrayDeque())

    fun startNewEffect(
        ctx: JcContext,
        getThreadLocalValue: (threadLocal: Any) -> Any?,
        setThreadLocalValue: (threadLocal: Any, value: Any?) -> Unit,
        myState: JcConcreteMemoryState
    ) {
        seq.addLast(JcConcreteEffect(ctx, getThreadLocalValue, setThreadLocalValue, myState))
    }

    fun head(): JcConcreteEffect {
        return seq.last()
    }

    private fun findCommonPartIndex(other: JcConcreteEffectSequence): Int {
        val otherSeq = other.seq
        var index = min(seq.size, otherSeq.size) - 1
        while (index >= 0 && seq[index] != otherSeq[index])
            index--

        return index
    }

    fun resetTo(other: JcConcreteEffectSequence) {
        check(other != this)

        val commonPartEnd = findCommonPartIndex(other) + 1
        // TODO: optimize intersection (if object contains in older, do not apply others) #CM
        for (i in seq.size - 1 downTo commonPartEnd) {
            if (seq[i].resetToBefore())
                seq.removeAt(i)
        }

        val otherSeq = other.seq
        // TODO: optimize intersection (if object contains in newer, do not apply others) #CM
        for (i in commonPartEnd until otherSeq.size) {
            otherSeq[i].resetToAfter()
        }

        seq = other.seq
    }

    fun copy(newState: JcConcreteMemoryState): JcConcreteEffectSequence {
        for (effect in seq) {
            effect.forks.add(newState)
        }

        return JcConcreteEffectSequence(ArrayDeque(seq))
    }
}

internal class JcConcreteEffectStorage private constructor(
    private val ctx: JcContext,
    private val getThreadLocalValue: (threadLocal: Any) -> Any?,
    private val setThreadLocalValue: (threadLocal: Any, value: Any?) -> Unit,
    private val own: JcConcreteEffectSequence,
    private val current: JcConcreteEffectSequence
) {
    constructor(
        ctx: JcContext,
        getThreadLocalValue: (threadLocal: Any) -> Any?,
        setThreadLocalValue: (threadLocal: Any, value: Any?) -> Unit
    ) : this(ctx, getThreadLocalValue, setThreadLocalValue, JcConcreteEffectSequence(), JcConcreteEffectSequence())

    fun startNewEffect(myState: JcConcreteMemoryState) {
        own.startNewEffect(ctx, getThreadLocalValue, setThreadLocalValue, myState)
    }

    fun addObjectToEffect(obj: Any) {
        own.head().before.addObjectToSnapshot(PhysicalAddress(obj))
    }

    fun addObjectToEffectRec(obj: Any?) {
        own.head().before.addObjectToSnapshotRec(obj)
    }

    fun addStaticsToEffect(types: List<JcClassOrInterface>) {
        own.head().addStaticFields(types)
    }

    fun reset() {
        current.resetTo(own)
    }

    fun copy(newState: JcConcreteMemoryState): JcConcreteEffectStorage {
        return JcConcreteEffectStorage(ctx, getThreadLocalValue, setThreadLocalValue, own.copy(newState), current)
    }
}
