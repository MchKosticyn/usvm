package org.usvm.machine.state.concreteMemory

import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.impl.features.classpaths.JcUnknownType
import org.usvm.api.util.JcConcreteMemoryClassLoader
import org.usvm.api.util.Reflection.allocateInstance
import org.usvm.machine.JcContext
import java.lang.reflect.Field
import kotlin.math.min

internal interface ThreadLocalHelper {
    fun getThreadLocalValue(threadLocal: Any): Any?
    fun setThreadLocalValue(threadLocal: Any, value: Any?)
    fun checkIsPresent(threadLocal: Any): Boolean
}

private class JcConcreteSnapshot(
    private val ctx: JcContext,
    val threadLocalHelper: ThreadLocalHelper,
) {
    private val objects: HashMap<PhysicalAddress, PhysicalAddress> = hashMapOf()
    private val addedRec: HashSet<PhysicalAddress> = hashSetOf()
    private val statics: HashMap<Field, PhysicalAddress> = hashMapOf()
    private var staticsCache = hashSetOf<Class<*>>()

    constructor(
        ctx: JcContext,
        threadLocalHelper: ThreadLocalHelper,
        other: JcConcreteSnapshot
    ) : this(ctx, threadLocalHelper) {
        for ((phys, _) in other.objects) {
            addObjectToSnapshot(phys)
        }

        for ((field, phys) in other.statics) {
            addStaticFieldToSnapshot(field, phys)
        }
    }

    fun getObjects(): Map<PhysicalAddress, PhysicalAddress> = objects

    fun getStatics(): Map<Field, PhysicalAddress> = statics

    fun isEmpty(): Boolean = objects.isEmpty() && statics.isEmpty()

    private fun cloneObject(obj: Any): Any? {
        val type = obj.javaClass
        try {
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
                        val value = field.getFieldValue(obj)
                        field.setFieldValue(newObj, value)
                    }
                    newObj
                }
                else -> null
            }
        } catch (e: Throwable) {
            println("[WARNING] cloneObject failed on class ${type.name}")
            return null
        }
    }

    fun addObjectToSnapshot(oldPhys: PhysicalAddress) {
        if (objects.containsKey(oldPhys))
            return

        val obj = oldPhys.obj!!
        val type = obj.javaClass
        if (!type.isThreadLocal && (type.isImmutable || type.allInstanceFieldsAreFinal)) {
            return
        }

        val clonedObj = if (type.isThreadLocal) {
            if (!threadLocalHelper.checkIsPresent(obj))
                return

            threadLocalHelper.getThreadLocalValue(obj)
        } else {
            cloneObject(obj) ?: return
        }
        val clonedPhys = PhysicalAddress(clonedObj)
        objects[oldPhys] = clonedPhys
    }

    private inner class EffectTraversal: ObjectTraversal(threadLocalHelper, false) {
        override fun skip(phys: PhysicalAddress, type: Class<*>): Boolean {
            return type.notTracked || addedRec.contains(phys)
        }

        override fun skipField(field: Field): Boolean {
            return field.type.notTrackedWithSubtypes
        }

        override fun skipArrayIndices(elementType: Class<*>): Boolean {
            return elementType.notTrackedWithSubtypes
        }

        override fun handleArray(phys: PhysicalAddress, type: Class<*>) {
            addObjectToSnapshot(phys)
            addedRec.add(phys)
        }

        override fun handleClass(phys: PhysicalAddress, type: Class<*>) {
            addObjectToSnapshot(phys)
            addedRec.add(phys)
        }

        override fun handleThreadLocal(threadLocalPhys: PhysicalAddress, valuePhys: PhysicalAddress) {
            objects[threadLocalPhys] = valuePhys
            addedRec.add(threadLocalPhys)
        }

        override fun handleArrayIndex(arrayPhys: PhysicalAddress, index: Int, valuePhys: PhysicalAddress) {
        }

        override fun handleClassField(parentPhys: PhysicalAddress, field: Field, valuePhys: PhysicalAddress) {
        }

    }

    fun addObjectToSnapshotRec(obj: Any?) {
        EffectTraversal().traverse(obj)
    }

    fun addStaticFieldToSnapshot(field: Field, phys: PhysicalAddress) {
        if (!field.isFinal)
            statics[field] = phys
    }

    fun addStaticFieldToSnapshot(field: Field, value: Any?) {
        check(value !is PhysicalAddress)
        addStaticFieldToSnapshot(field, PhysicalAddress(value))
    }

    fun addStaticFields(type: Class<*>) {
        // TODO: add custom filters
        // TODO: discard all runtime statics! #CM
        if (type.isImmutable || staticsCache.contains(type))
            return

        for (field in type.staticFields) {
            val value = field.getStaticFieldValue()
            addStaticFieldToSnapshot(field, value)
            addObjectToSnapshotRec(value)
        }
        staticsCache.add(type)
    }

    fun ensureStatics() {
        val currentStatics = JcConcreteMemoryClassLoader.initializedStatics()
        val needToAdd = currentStatics - staticsCache

        for (type in needToAdd) {
            addStaticFields(type)
        }
    }
}

private class JcConcreteSnapshotSequence(
    snapshots: List<JcConcreteSnapshot>
) {
    private val objects: Map<PhysicalAddress, PhysicalAddress>
    private val statics: Map<Field, PhysicalAddress>
    private val threadLocalHelper: ThreadLocalHelper

    init {
        check(snapshots.isNotEmpty())
        if (snapshots.size == 1) {
            val snapshot = snapshots[0]
            objects = snapshot.getObjects()
            statics = snapshot.getStatics()
            threadLocalHelper = snapshot.threadLocalHelper
        } else {
            threadLocalHelper = snapshots[0].threadLocalHelper
            val resultObjects = hashMapOf<PhysicalAddress, PhysicalAddress>()
            val resultStatics = hashMapOf<Field, PhysicalAddress>()
            for (snapshot in snapshots) {
                check(snapshot.threadLocalHelper === threadLocalHelper)
                resultObjects.putAll(snapshot.getObjects())
                resultStatics.putAll(snapshot.getStatics())
            }
            objects = resultObjects
            statics = resultStatics
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
            check(!type.notTracked)
            when {
                type.isThreadLocal -> threadLocalHelper.setThreadLocalValue(oldObj, obj)
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
                            field.setFieldValue(oldObj, value)
                        } catch (e: Throwable) {
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
    private val threadLocalHelper: ThreadLocalHelper,
    val before: JcConcreteSnapshot = JcConcreteSnapshot(ctx, threadLocalHelper)
) {
    var after: JcConcreteSnapshot? = null

    private var isActiveVar = false
    private var isAliveVar = true

    val isAlive: Boolean get() = isAliveVar

    val isActive: Boolean get() = isActiveVar

    val afterIsEmpty: Boolean get() = after == null

    fun kill() {
        isAliveVar = false
    }

    fun createAfterIfNeeded() {
        if (after != null || !isActive || !isAlive)
            return

        this.after = JcConcreteSnapshot(ctx, threadLocalHelper, before)
    }

    fun addObject(obj: Any?) {
        check(obj !is PhysicalAddress)
        check(isAlive)
        check(afterIsEmpty)
        isActiveVar = true
        before.addObjectToSnapshot(PhysicalAddress(obj))
    }

    fun addObjectRec(obj: Any?) {
        check(obj !is PhysicalAddress)
        check(isAlive)
        check(afterIsEmpty)
        isActiveVar = true
        before.addObjectToSnapshotRec(obj)
    }

    fun ensureStatics() {
        check(isAlive)
        check(afterIsEmpty)
        isActiveVar = true
        before.ensureStatics()
    }

    fun addStaticFields(type: Class<*>) {
        check(isAlive)
        check(afterIsEmpty)
        isActiveVar = true
        before.addStaticFields(type)
    }
}

private class JcConcreteEffectSequence private constructor(
    var seq: ArrayDeque<JcConcreteEffect>
) {
    constructor() : this(ArrayDeque())

    private fun startNewEffect(
        ctx: JcContext,
        threadLocalHelper: ThreadLocalHelper
    ) {
        // TODO: if previous effect is empty maybe take it? #CM
        if (seq.isEmpty()) {
            seq.addLast(JcConcreteEffect(ctx, threadLocalHelper))
            return
        }

        val last = seq.last()
        val lastAfter = last.after
        if (lastAfter != null) {
            seq.addLast(JcConcreteEffect(ctx, threadLocalHelper, lastAfter))
            return
        }

        val newBefore = JcConcreteSnapshot(ctx, threadLocalHelper, last.before)
        val newEffect = JcConcreteEffect(ctx, threadLocalHelper, newBefore)
        last.after = newEffect.before
        seq.addLast(newEffect)
    }

    fun head(): JcConcreteEffect? {
        return seq.lastOrNull()
    }

    private fun findCommonPartIndex(other: JcConcreteEffectSequence): Int {
        val otherSeq = other.seq
        var index = min(seq.size, otherSeq.size) - 1
        while (index >= 0 && seq[index] !== otherSeq[index])
            index--

        return index
    }

    fun resetTo(other: JcConcreteEffectSequence) {
        check(other !== this)

        if (seq === other.seq)
            return

        seq.lastOrNull()?.createAfterIfNeeded()

        val commonPartEnd = findCommonPartIndex(other) + 1
        val snapshots = mutableListOf<JcConcreteSnapshot>()
        for (i in seq.lastIndex downTo commonPartEnd) {
            val effect = seq[i]
            if (effect.isActive) {
                snapshots.add(effect.before)
            }
        }

        val otherSeq = other.seq
        for (i in commonPartEnd until otherSeq.size) {
            val effect = otherSeq[i]
            if (effect.isActive) {
                snapshots.add(effect.after!!)
            }
        }

        if (snapshots.isNotEmpty()) {
            val snapshotSeq = JcConcreteSnapshotSequence(snapshots)
            snapshotSeq.resetObjects()
            snapshotSeq.resetStatics()
        }

        seq = other.seq
    }

    fun copy(ctx: JcContext, threadLocalHelper: ThreadLocalHelper): JcConcreteEffectSequence {
        val copied = JcConcreteEffectSequence(ArrayDeque(seq))
        startNewEffect(ctx, threadLocalHelper)
        copied.startNewEffect(ctx, threadLocalHelper)
        return copied
    }
}

// TODO: do not store effects of new addresses! #CM
//  Optimize: check if address is allocated during current effect: maybe instrumentation of Object<init>?
internal class JcConcreteEffectStorage private constructor(
    private val ctx: JcContext,
    private val threadLocalHelper: ThreadLocalHelper,
    private val own: JcConcreteEffectSequence,
    private val current: JcConcreteEffectSequence
) {
    constructor(
        ctx: JcContext,
        threadLocalHelper: ThreadLocalHelper,
    ) : this(ctx, threadLocalHelper, JcConcreteEffectSequence(), JcConcreteEffectSequence())

    private val isCurrent: Boolean
        get() = own.seq === current.seq

    fun addObjectToEffect(obj: Any) {
        check(isCurrent)
        own.head()!!.addObject(obj)
    }

    fun addObjectToEffectRec(obj: Any?) {
        check(isCurrent)
        own.head()!!.addObjectRec(obj)
    }

    fun ensureStatics() {
        check(isCurrent)
        own.head()!!.ensureStatics()
    }

    fun addStatics(type: Class<*>) {
        check(isCurrent)
        own.head()?.addStaticFields(type)
    }

    fun reset() {
        JcConcreteMemoryClassLoader.setEffectStorage(this)
        current.resetTo(own)
    }

    fun kill() {
        check(isCurrent)
        own.head()?.kill()
    }

    fun copy(): JcConcreteEffectStorage {
        val wasCurrent = isCurrent
        val copied = JcConcreteEffectStorage(ctx, threadLocalHelper, own.copy(ctx, threadLocalHelper), current)
        check(!wasCurrent || isCurrent && !copied.isCurrent)
        return copied
    }
}
