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
    private val staticsCache: HashSet<Class<*>> = hashSetOf()

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

    private inner class SnapshotTraversal: ObjectTraversal(threadLocalHelper, false) {
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
        SnapshotTraversal().traverse(obj)
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

    val isEmpty: Boolean get() = statics.isEmpty() && objects.isEmpty()

    fun merge(other: JcConcreteSnapshot) {
        objects.putAll(other.objects)
        addedRec.putAll(other.addedRec)
        newObjects.putAll(other.newObjects)
        statics.putAll(other.statics)
        staticsCache.addAll(other.staticsCache)
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

private sealed interface EffectNode

private data object RootNode : EffectNode

private var effectId = 0

private class JcConcreteEffect(
    private val ctx: JcContext,
    private val threadLocalHelper: ThreadLocalHelper,
    parent: EffectNode
) : EffectNode {

    val id = effectId++

    var before: JcConcreteSnapshot? = null
        private set

    var after: JcConcreteSnapshot? = null
        private set

    var parent = parent
        private set

    private val children = hashSetOf<JcConcreteEffect>()

    private val childrenCount: Int get() = children.size

    var isAlive = true
        private set

    //region Aliveness Operations

    fun kill() {
        check(children.isEmpty())
        isAlive = false
    }

    //endregion

    //region Children Operations

    fun addChild(child: JcConcreteEffect) {
        children.add(child)
    }

    fun deleteChild(child: JcConcreteEffect) {
        children.remove(child)
    }

    val hasChildren: Boolean get() = childrenCount > 0

    val hasSingleChild: Boolean get() = childrenCount == 1

    //endregion

    //region Snapshots Operations

    val beforeIsEmpty: Boolean get() = before?.isEmpty != false
    val afterIsEmpty: Boolean get() = after?.isEmpty != false

    val isEmpty: Boolean get() = beforeIsEmpty && afterIsEmpty

    private fun getOrCreateBefore(): JcConcreteSnapshot {
        if (before != null)
            return before!!

        before = JcConcreteSnapshot(ctx, threadLocalHelper)
        return before!!
    }

    fun createAfterIfNeeded() {
        if (!afterIsEmpty || beforeIsEmpty || !isAlive)
            return

        this.after = JcConcreteSnapshot(ctx, threadLocalHelper, before!!)
    }

    fun addObject(obj: Any?) {
        check(after == null)
        obj ?: return
        getOrCreateBefore().addObjectToSnapshot(obj)
    }

    fun addObjectRec(obj: Any?) {
        check(after == null)
        obj ?: return
        getOrCreateBefore().addObjectToSnapshotRec(obj)
    }

    fun ensureStatics() {
        check(after == null)
        getOrCreateBefore().ensureStatics()
    }

    fun addStaticFields(type: Class<*>) {
        check(after == null)
        getOrCreateBefore().addStaticFields(type)
    }

    fun addNewObject(obj: Any) {
        check(after == null)
        getOrCreateBefore().addNewObject(obj)
    }

    //endregion

    //region Merging

    fun mergeWithParent() {
        check(parent !== RootNode)
        val prev = requireNotNull(parent as? JcConcreteEffect)
        check(!beforeIsEmpty && !afterIsEmpty && !prev.beforeIsEmpty && !prev.afterIsEmpty)
        check(isAlive && prev.isAlive)
        check(prev.hasSingleChild)
        before!!.merge(prev.before!!)
        prev.after!!.merge(after!!)
        after = prev.after!!
        parent = prev.parent
    }

    //endregion
}

private class JcConcreteEffectSequence private constructor(
    private var headNode: HeadNode,
) {
    private data class HeadNode(
        var head: EffectNode,
        var isAlive: Boolean = true
    )

    private companion object {
        private fun findCommonPartIndex(seq: List<JcConcreteEffect>, otherSeq: List<JcConcreteEffect>): Int {
            var index = min(seq.size, otherSeq.size) - 1
            while (index >= 0 && seq[index] !== otherSeq[index])
                index--

            return index
        }
    }

    constructor(): this(HeadNode(RootNode))

    private var isAlive: Boolean get() = headNode.isAlive
        private set(value) {
            headNode.isAlive = value
        }

    var head: EffectNode get() = headNode.head
        private set(value) {
            headNode.head = value
        }

    private val lastEffect: JcConcreteEffect? get() = head as? JcConcreteEffect

    private fun startNewEffect(
        ctx: JcContext,
        threadLocalHelper: ThreadLocalHelper
    ) {
        if (!isAlive)
            return

        val last = head
        if (last === RootNode) {
            head = JcConcreteEffect(ctx, threadLocalHelper, last)
            return
        }

        check(last is JcConcreteEffect)
        check(last.isAlive)
        if (last.isEmpty) {
            check(!last.hasChildren)
            val parentOfLast = last.parent
            val newEffect = JcConcreteEffect(ctx, threadLocalHelper, last.parent)
            if (parentOfLast is JcConcreteEffect) {
                parentOfLast.deleteChild(last)
                parentOfLast.addChild(newEffect)
            }
            head = newEffect
            return
        }
        val newEffect = JcConcreteEffect(ctx, threadLocalHelper, last)
        last.createAfterIfNeeded()
        last.addChild(newEffect)
        head = newEffect
    }

    fun addObjectToEffect(obj: Any) {
        check(isAlive)
        lastEffect!!.addObject(obj)
    }

    fun addObjectToEffectRec(obj: Any?) {
        check(isAlive)
        lastEffect!!.addObjectRec(obj)
    }

    fun ensureStatics() {
        check(isAlive)
        lastEffect!!.ensureStatics()
    }

    fun addStatics(type: Class<*>) {
        check(isAlive)
        lastEffect?.addStaticFields(type)
    }

    fun addNewObject(obj: Any) {
        check(isAlive)
        lastEffect?.addNewObject(obj)
    }

    fun kill() {
        isAlive = false
        var prev: JcConcreteEffect? = null
        var current = head
        while (current is JcConcreteEffect) {
            if (prev != null)
                current.deleteChild(prev)

            if (current.hasChildren)
                break

            current.kill()
            prev = current
            current = current.parent
        }
    }

    private fun toList(): List<JcConcreteEffect> {
        val result = ArrayDeque<JcConcreteEffect>()
        var current = head
        while (current is JcConcreteEffect) {
            result.addFirst(current)
            current = current.parent
        }

        return result
    }

    private val isCorrect: Boolean get() {
        var current = head
        var isAliveAcc = isAlive
        while (current is JcConcreteEffect) {
            val isAliveCurr = current.isAlive
            if (!isAliveCurr && isAliveAcc)
                return false
            isAliveAcc = isAliveCurr
            current = current.parent
        }
        return true
    }

    private fun optimize() {
        var current = head
        while (current is JcConcreteEffect) {
            val isFinished = current.hasChildren
            val isAlive = current.isAlive
            val prev = current.parent
            if (isAlive && isFinished && prev is JcConcreteEffect && prev.hasSingleChild) {
                check(!current.isEmpty && !prev.isEmpty)
                check(prev.isAlive)
                current.mergeWithParent()
                continue
            }
            current = prev
        }
    }

    private fun deleteDeadEffects() {
        var current = head
        while (current is JcConcreteEffect && !current.isAlive) {
            check(head === current)
            head = current.parent
            current = head
        }
    }

    private fun createResetPath(other: JcConcreteEffectSequence): List<JcConcreteSnapshot> {
        val seq = toList()
        val otherSeq = other.toList()
        val commonPartEnd = findCommonPartIndex(seq, otherSeq) + 1
        val snapshots = mutableListOf<JcConcreteSnapshot>()
        for (i in seq.lastIndex downTo commonPartEnd) {
            val effect = seq[i]
            if (!effect.beforeIsEmpty) {
                snapshots.add(effect.before!!)
            }
        }

        for (i in commonPartEnd until otherSeq.size) {
            val effect = otherSeq[i]
            check(effect.isAlive)
            if (!effect.afterIsEmpty) {
                snapshots.add(effect.after!!)
            }
        }

        return snapshots
    }

    fun resetTo(
        ctx: JcContext,
        threadLocalHelper: ThreadLocalHelper,
        other: JcConcreteEffectSequence
    ) {
        if (other === this || head === other.head)
            return

        startNewEffect(ctx, threadLocalHelper)

        optimize()
        other.optimize()

        val snapshots = createResetPath(other)

        deleteDeadEffects()

        if (snapshots.isNotEmpty()) {
            val snapshotSeq = JcConcreteSnapshotSequence(snapshots)
            snapshotSeq.resetObjects()
            snapshotSeq.resetStatics()
        }

        headNode = other.headNode
    }

    fun resetWeight(other: JcConcreteEffectSequence): Int {
        if (other === this || head === other.head)
            return 0

        optimize()
        other.optimize()

        val snapshots = createResetPath(other)

        if (snapshots.isEmpty())
            return 0

        val snapshotSeq = JcConcreteSnapshotSequence(snapshots)
        return snapshotSeq.weight()
    }

    fun copy(ctx: JcContext, threadLocalHelper: ThreadLocalHelper): JcConcreteEffectSequence {
        val copied = JcConcreteEffectSequence(headNode.copy())
        startNewEffect(ctx, threadLocalHelper)
        copied.startNewEffect(ctx, threadLocalHelper)
        return copied
    }

    fun clone(): JcConcreteEffectSequence {
        return JcConcreteEffectSequence(headNode)
    }
}

internal class JcConcreteEffectStorage {
    private val ctx: JcContext
    private val threadLocalHelper: ThreadLocalHelper
    private val own: JcConcreteEffectSequence
    private val current: JcConcreteEffectSequence

    private constructor(
        ctx: JcContext,
        threadLocalHelper: ThreadLocalHelper,
        own: JcConcreteEffectSequence,
        current: JcConcreteEffectSequence
    ) {
        this.ctx = ctx
        this.threadLocalHelper = threadLocalHelper
        this.own = own
        this.current = current
    }

    constructor(
        ctx: JcContext,
        threadLocalHelper: ThreadLocalHelper,
    ) {
        this.ctx = ctx
        this.threadLocalHelper = threadLocalHelper
        val effectSeq = JcConcreteEffectSequence()
        this.own = effectSeq
        this.current = effectSeq.clone()
    }

    private val isCurrent: Boolean
        get() = own.head === current.head

    fun addObjectToEffect(obj: Any) {
        check(isCurrent) {
            "addObjectToEffect: effect storage is not current"
        }
        own.addObjectToEffect(obj)
    }

    fun addObjectToEffectRec(obj: Any?) {
        check(isCurrent) {
            "addObjectToEffectRec: effect storage is not current"
        }
        own.addObjectToEffectRec(obj)
    }

    fun ensureStatics() {
        check(isCurrent) {
            "ensureStatics: effect storage is not current"
        }
        own.ensureStatics()
    }

    fun addStatics(type: Class<*>) {
        check(isCurrent) {
            "addStatics: effect storage is not current"
        }
        own.addStatics(type)
    }

    fun addNewObject(obj: Any) {
        check(isCurrent) {
            "addNewObject: effect storage is not current"
        }
        own.addNewObject(obj)
    }

    fun reset() {
        if (current === own || current.head === own.head)
            return

        // TODO: #hack #threads
        //  disabling effect storage, because other running threads may create objects, but effect storage is not ready
        JcConcreteMemoryClassLoader.disableEffectStorage()
        current.resetTo(ctx, threadLocalHelper, own)
        JcConcreteMemoryClassLoader.setEffectStorage(this)
    }

    fun resetWeight(): Int {
        return current.resetWeight(own)
    }

    internal fun kill(force: Boolean) {
        check(isCurrent || force)
        own.kill()
        JcConcreteMemoryClassLoader.disableEffectStorage()
    }

    fun copy(): JcConcreteEffectStorage {
        val wasCurrent = isCurrent
        val copied = JcConcreteEffectStorage(ctx, threadLocalHelper, own.copy(ctx, threadLocalHelper), current)
        check(!wasCurrent || isCurrent && !copied.isCurrent)
        return copied
    }
}
