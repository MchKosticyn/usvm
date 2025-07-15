package machine.state.concreteMemory

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import machine.JcConcreteMemoryClassLoader
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.impl.features.classpaths.JcUnknownType
import org.usvm.jvm.util.allInstanceFields
import org.usvm.jvm.util.allocateInstance
import org.usvm.jvm.util.staticFields
import org.usvm.machine.JcContext
import utils.allInstanceFieldsAreFinal
import utils.getFieldValue
import utils.getStaticFieldValue
import utils.isByteBuffer
import utils.isFinal
import utils.isImmutable
import utils.isLambda
import utils.isProxy
import utils.isThreadLocal
import utils.notTracked
import utils.notTrackedWithSubtypes
import utils.setFieldValue
import utils.setStaticFieldValue
import utils.toJcType
import java.lang.reflect.Field
import java.util.IdentityHashMap
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
    private val objects: IdentityHashMap<Any, Any?> = IdentityHashMap()
    private val addedRec: IdentityHashMap<Any, Unit> = IdentityHashMap()
    private val newObjects: IdentityHashMap<Any, Unit> = IdentityHashMap()
    private val statics: Object2ObjectOpenHashMap<Field, Any?> = Object2ObjectOpenHashMap()
    private var staticsCache: HashSet<Class<*>> = hashSetOf()

    constructor(
        ctx: JcContext,
        threadLocalHelper: ThreadLocalHelper,
        other: JcConcreteSnapshot
    ) : this(ctx, threadLocalHelper) {
        for ((obj, _) in other.objects) {
            addObjectToSnapshot(obj)
        }

        for ((field, obj) in other.statics) {
            addStaticFieldToSnapshot(field, obj)
        }
    }

    fun getObjects(): IdentityHashMap<Any, Any?> = objects

    fun getStatics(): Object2ObjectOpenHashMap<Field, Any?> = statics

    private fun cloneObject(obj: Any): Any? {
        val type = obj.javaClass
        try {
            val jcType = type.toJcType(ctx.cp) ?: return null
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
            println("[WARNING] cloneObject failed on class ${type.typeName}")
            return null
        }
    }

    fun addObjectToSnapshot(oldObj: Any) {
        if (objects.containsKey(oldObj) || newObjects.contains(oldObj))
            return

        val type = oldObj.javaClass
        if (!type.isThreadLocal && (type.isImmutable || !type.isArray && type.allInstanceFieldsAreFinal)) {
            return
        }

        val clonedObj = if (type.isThreadLocal) {
            if (!threadLocalHelper.checkIsPresent(oldObj))
                return

            threadLocalHelper.getThreadLocalValue(oldObj)
        } else {
            cloneObject(oldObj) ?: return
        }

        objects[oldObj] = clonedObj
    }

    private inner class EffectTraversal: ObjectTraversal(threadLocalHelper, false) {
        override fun skip(obj: Any, type: Class<*>): Boolean {
            return type.notTracked || addedRec.contains(obj)
        }

        override fun skipField(field: Field): Boolean {
            return field.type.notTrackedWithSubtypes
        }

        override fun skipArrayIndices(elementType: Class<*>): Boolean {
            return elementType.notTrackedWithSubtypes
        }

        override fun handleArray(array: Any, type: Class<*>) {
            addObjectToSnapshot(array)
            addedRec[array] = Unit
        }

        override fun handleClass(obj: Any, type: Class<*>) {
            addObjectToSnapshot(obj)
            addedRec[obj] = Unit
        }

        override fun handleThreadLocal(threadLocal: Any, value: Any?) {
            objects[threadLocal] = value
            addedRec[threadLocal] = Unit
        }
    }

    fun addObjectToSnapshotRec(obj: Any) {
        EffectTraversal().traverse(obj)
    }

    fun addStaticFieldToSnapshot(field: Field, value: Any?) {
        if (!field.isFinal)
            statics[field] = value
    }

    fun addStaticFields(type: Class<*>) {
        // TODO: add custom filters
        // TODO: discard all runtime statics! #CM
        if (type.isImmutable || staticsCache.contains(type))
            return

        for (field in type.staticFields) {
            val value = field.getStaticFieldValue()
            addStaticFieldToSnapshot(field, value)
            value ?: continue
            addObjectToSnapshotRec(value)
        }
        staticsCache.add(type)
    }

    fun addNewObject(obj: Any) {
        newObjects[obj] = Unit
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
    private val objects: IdentityHashMap<Any, Any?>
    private val statics: Object2ObjectOpenHashMap<Field, Any?>
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
            val resultObjects = IdentityHashMap<Any, Any?>()
            val resultStatics = Object2ObjectOpenHashMap<Field, Any?>()
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
        for ((field, value) in statics) {
            field.setStaticFieldValue(value)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun resetObjects() {
        for ((oldObj, clonedObj) in objects) {
            val type = oldObj.javaClass
            check(clonedObj != null && type == clonedObj.javaClass || type.isThreadLocal)
            check(!type.notTracked)
            when {
                type.isThreadLocal -> threadLocalHelper.setThreadLocalValue(oldObj, clonedObj)
                type.isArray -> {
                    when {
                        clonedObj is IntArray && oldObj is IntArray -> {
                            clonedObj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        clonedObj is ByteArray && oldObj is ByteArray -> {
                            clonedObj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        clonedObj is CharArray && oldObj is CharArray -> {
                            clonedObj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        clonedObj is LongArray && oldObj is LongArray -> {
                            clonedObj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        clonedObj is FloatArray && oldObj is FloatArray -> {
                            clonedObj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        clonedObj is ShortArray && oldObj is ShortArray -> {
                            clonedObj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        clonedObj is DoubleArray && oldObj is DoubleArray -> {
                            clonedObj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        clonedObj is BooleanArray && oldObj is BooleanArray -> {
                            clonedObj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        clonedObj is Array<*> && oldObj is Array<*> -> {
                            oldObj as Array<Any?>
                            clonedObj.forEachIndexed { i, v ->
                                oldObj[i] = v
                            }
                        }
                        else -> error("applyBacktrack: unexpected array $clonedObj")
                    }
                }

                else -> {
                    check(clonedObj != null)
                    for (field in type.allInstanceFields) {
                        try {
                            val value = field.getFieldValue(clonedObj)
                            field.setFieldValue(oldObj, value)
                        } catch (e: Throwable) {
                            error("applyBacktrack class ${type.typeName} failed on field ${field.name}, cause: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    fun weight(): Int {
        // TODO: add objects size
        return objects.size + statics.size
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
        check(isAlive)
        check(afterIsEmpty)
        isActiveVar = true
        obj ?: return
        before.addObjectToSnapshot(obj)
    }

    fun addObjectRec(obj: Any?) {
        check(isAlive)
        check(afterIsEmpty)
        isActiveVar = true
        obj ?: return
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

    fun addNewObject(obj: Any) {
        check(isAlive)
        check(afterIsEmpty)
        isActiveVar = true
        before.addNewObject(obj)
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

    private fun createResetPath(other: JcConcreteEffectSequence): List<JcConcreteSnapshot> {
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

        return snapshots
    }

    fun resetTo(other: JcConcreteEffectSequence) {
        check(other !== this)

        if (seq === other.seq)
            return

        seq.lastOrNull()?.createAfterIfNeeded()

        val snapshots = createResetPath(other)

        if (snapshots.isNotEmpty()) {
            val snapshotSeq = JcConcreteSnapshotSequence(snapshots)
            snapshotSeq.resetObjects()
            snapshotSeq.resetStatics()
        }

        seq = other.seq
    }

    fun resetWeight(other: JcConcreteEffectSequence): Int {
        check(other !== this)

        if (seq === other.seq)
            return 0

        val snapshots = createResetPath(other)

        if (snapshots.isEmpty())
            return 0

        val snapshotSeq = JcConcreteSnapshotSequence(snapshots)
        return snapshotSeq.weight()
    }

    fun copy(ctx: JcContext, threadLocalHelper: ThreadLocalHelper): JcConcreteEffectSequence {
        val copied = JcConcreteEffectSequence(ArrayDeque(seq))
        startNewEffect(ctx, threadLocalHelper)
        copied.startNewEffect(ctx, threadLocalHelper)
        return copied
    }
}

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

    val isEmpty: Boolean
        get() = own.seq.isEmpty()

    fun addObjectToEffect(obj: Any) {
        check(isCurrent) {
            "addObjectToEffect: effect storage is not current"
        }
        own.head()!!.addObject(obj)
    }

    fun addObjectToEffectRec(obj: Any?) {
        check(isCurrent) {
            "addObjectToEffectRec: effect storage is not current"
        }
        own.head()!!.addObjectRec(obj)
    }

    fun ensureStatics() {
        check(isCurrent) {
            "ensureStatics: effect storage is not current"
        }
        own.head()!!.ensureStatics()
    }

    fun addStatics(type: Class<*>) {
        check(isCurrent) {
            "addStatics: effect storage is not current"
        }
        own.head()?.addStaticFields(type)
    }

    fun addNewObject(obj: Any) {
        check(isCurrent) {
            "addNewObject: effect storage is not current"
        }
        own.head()?.addNewObject(obj)
    }

    fun reset() {
        // TODO: #hack #threads
        //  disabling effect storage, because other running threads may create objects, but effect storage is not ready
        JcConcreteMemoryClassLoader.disableEffectStorage()
        current.resetTo(own)
        JcConcreteMemoryClassLoader.setEffectStorage(this)
    }

    fun resetWeight(): Int {
        return current.resetWeight(own)
    }

    internal fun kill(force: Boolean) {
        check(isCurrent || force)
        own.head()?.kill()
        JcConcreteMemoryClassLoader.disableEffectStorage()
    }

    fun copy(): JcConcreteEffectStorage {
        val wasCurrent = isCurrent
        val copied = JcConcreteEffectStorage(ctx, threadLocalHelper, own.copy(ctx, threadLocalHelper), current)
        check(!wasCurrent || isCurrent && !copied.isCurrent)
        return copied
    }
}
