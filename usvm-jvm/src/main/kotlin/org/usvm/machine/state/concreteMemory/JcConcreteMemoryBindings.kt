package org.usvm.machine.state.concreteMemory

import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcType
import org.jacodb.approximation.JcEnrichedVirtualMethod
import org.usvm.NULL_ADDRESS
import org.usvm.UConcreteHeapAddress
import org.usvm.api.util.JcConcreteMemoryClassLoader
import org.usvm.api.util.Reflection.allocateInstance
import org.usvm.api.util.Reflection.invoke
import org.usvm.api.util.Reflection.toJavaClass
import org.usvm.constraints.UTypeConstraints
import org.usvm.machine.JcContext
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.LinkedList
import java.util.Queue

//region Cell

private data class Cell(
    val address: PhysicalAddress?
) {
    val isConcrete = address != null

    companion object {
        operator fun invoke(): Cell {
            return Cell(null)
        }
    }
}

//endregion

//region State

private enum class State {
    Mutable,
    MutableWithEffect,
    Dead
}

//endregion

private typealias childMapType = HashMap<ChildKind, Cell>
private typealias childrenType = HashMap<PhysicalAddress, childMapType>
private typealias parentMapType = HashMap<PhysicalAddress, ChildKind>
private typealias parentsType = HashMap<PhysicalAddress, parentMapType>

internal class JcConcreteMemoryBindings private constructor(
    private val ctx: JcContext,
    private val typeConstraints: UTypeConstraints<JcType>,
    private val physToVirt: HashMap<PhysicalAddress, UConcreteHeapAddress>,
    private val virtToPhys: HashMap<UConcreteHeapAddress, PhysicalAddress>,
    private var state: State,
    private val children: childrenType,
    private val parents: parentsType,
    private val fullyConcretes: MutableSet<PhysicalAddress>,
    // TODO: make this private #CM
    val effectStorage: JcConcreteEffectStorage,
    private val threadLocalHelper: ThreadLocalHelper,
) {
    internal constructor(
        ctx: JcContext,
        typeConstraints: UTypeConstraints<JcType>,
        threadLocalHelper: ThreadLocalHelper
    ) : this(
        ctx,
        typeConstraints,
        hashMapOf(),
        hashMapOf(),
        State.Mutable,
        hashMapOf(),
        hashMapOf(),
        mutableSetOf(),
        JcConcreteEffectStorage(ctx, threadLocalHelper),
        threadLocalHelper,
    )

    init {
        JcConcreteMemoryClassLoader.cp = ctx.cp
    }

    //region Primitives

    fun typeOf(address: UConcreteHeapAddress): JcType {
        return typeConstraints.typeOf(address)
    }

    fun contains(address: UConcreteHeapAddress): Boolean {
        return virtToPhys.contains(address)
    }

    fun tryVirtToPhys(address: UConcreteHeapAddress): Any? {
        return virtToPhys[address]?.obj
    }

    fun virtToPhys(address: UConcreteHeapAddress): Any {
        return virtToPhys[address]?.obj!!
    }

    fun tryFullyConcrete(address: UConcreteHeapAddress): Any? {
        val phys = virtToPhys[address]
        if (phys != null && checkConcreteness(phys)) {
            return phys.obj
        }
        return null
    }

    fun tryPhysToVirt(obj: Any): UConcreteHeapAddress? {
        return physToVirt[PhysicalAddress(obj)]
    }

    //region State Interaction

    private fun makeMutableWithEffect() {
        check(state != State.Dead)
        state = State.MutableWithEffect
    }

    fun kill() {
        check(state != State.Dead)
        state = State.Dead
        effectStorage.kill()
    }

    fun isMutableWithEffect(): Boolean {
        return state == State.MutableWithEffect
    }

    //endregion

    //region Concreteness Tracking

    private fun hasFullyConcreteParent(phys: PhysicalAddress): Boolean {
        val tested = mutableSetOf<PhysicalAddress>()
        val queue: Queue<PhysicalAddress> = LinkedList()
        var contains = false
        var child: PhysicalAddress? = phys

        while (!contains && child != null) {
            if (tested.add(child)) {
                contains = fullyConcretes.contains(child)
                parents[child]?.forEach {
                    val parent = it.key
                    queue.add(parent)
                }
            }
            child = queue.poll()
        }

        return contains
    }

    private fun addToParents(parent: PhysicalAddress, child: PhysicalAddress, childKind: ChildKind) {
        check(parent.obj != null)
        check(child.obj != null)
        val parentMap = parents.getOrPut(child) { hashMapOf() }
        parentMap[parent] = childKind
    }

    private fun addChild(parent: PhysicalAddress, child: PhysicalAddress, childKind: ChildKind, update: Boolean) {
        if (child.obj?.javaClass?.notTracked == true)
            return

        if (parent != child) {
            if (child.isNull && update) {
                children[parent]?.remove(childKind)
            } else if (!child.isNull) {
                val childMap = children[parent]
                if (childMap != null && update) {
                    childMap[childKind] = Cell(child)
                } else if (childMap != null) {
                    val cell = childMap[childKind]
                    if (cell != null) {
                        val cellIsCorrect = !cell.isConcrete || cell.address == child
                        if (!cellIsCorrect) {
                            println("[WARNING] concreteness system is incorrect")
                            childMap[childKind] = Cell(child)
                        }
                    } else {
                        childMap[childKind] = Cell(child)
                    }
                } else {
                    val newChildMap = hashMapOf<ChildKind, Cell>()
                    newChildMap[childKind] = Cell(child)
                    children[parent] = newChildMap
                }

                if (update && hasFullyConcreteParent(parent) && !checkConcreteness(child))
                    removeFromFullyConcretesRec(parent)

                addToParents(parent, child, childKind)
            }
        }
    }

    private fun trackChild(parent: PhysicalAddress, child: PhysicalAddress, childKind: ChildKind) {
        addChild(parent, child, childKind, false)
    }

    private fun trackChild(parent: Any?, child: Any?, childKind: ChildKind) {
        check(parent !is PhysicalAddress && child !is PhysicalAddress)
        trackChild(PhysicalAddress(parent), PhysicalAddress(child), childKind)
    }

    private fun setChild(parent: PhysicalAddress, child: PhysicalAddress, childKind: ChildKind) {
        addChild(parent, child, childKind, true)
    }

    private fun setChild(parent: Any?, child: Any?, childKind: ChildKind) {
        check(parent !is PhysicalAddress && child !is PhysicalAddress)
        setChild(PhysicalAddress(parent), PhysicalAddress(child), childKind)
    }

    private fun checkConcreteness(phys: PhysicalAddress): Boolean {
        val tracked = mutableSetOf<PhysicalAddress>()
        return checkConcretenessRec(phys, tracked)
    }

    private fun checkConcretenessRec(phys: PhysicalAddress, tracked: MutableSet<PhysicalAddress>): Boolean {
        // TODO: cache not fully concrete objects #CM
        if (fullyConcretes.contains(phys) || !tracked.add(phys))
            return true

        var allConcrete = true
        children[phys]?.forEach {
            if (allConcrete) {
                val child = it.value.address
                allConcrete = child != null && checkConcretenessRec(child, tracked)
            }
        }

        if (allConcrete) fullyConcretes.add(phys)

        return allConcrete
    }

    private inner class ConcretenessTraversal: ObjectTraversal(threadLocalHelper, false) {
        override fun skip(phys: PhysicalAddress, type: Class<*>): Boolean {
            return type.isSolid
        }

        override fun skipField(field: Field): Boolean {
            return field.type.notTrackedWithSubtypes
        }

        override fun handleArray(phys: PhysicalAddress, type: Class<*>) {
        }

        override fun handleClass(phys: PhysicalAddress, type: Class<*>) {
        }

        override fun handleThreadLocal(threadLocalPhys: PhysicalAddress, valuePhys: PhysicalAddress) {
            setChild(threadLocalPhys, valuePhys, ThreadLocalValueChildKind())
        }

        override fun handleArrayIndex(arrayPhys: PhysicalAddress, index: Int, valuePhys: PhysicalAddress) {
            setChild(arrayPhys, valuePhys, ArrayIndexChildKind(index))
        }

        override fun handleClassField(parentPhys: PhysicalAddress, field: Field, valuePhys: PhysicalAddress) {
            setChild(parentPhys, valuePhys, FieldChildKind(field))
        }

    }

    fun reTrackObject(obj: Any?) {
        ConcretenessTraversal().traverse(obj)
    }

    private fun skipTrackCopy(dstArrayType: Class<*>): Boolean {
        val elemType = dstArrayType.componentType
        return elemType.notTrackedWithSubtypes
    }

    private fun trackCopy(updatedDstArray: Array<*>, dstArrayType: Class<*>, dstFromIdx: Int, dstToIdx: Int) {
        check(dstArrayType.isArray)
        check(dstFromIdx <= dstToIdx)

        if (skipTrackCopy(dstArrayType)) return

        for (i in dstFromIdx..<dstToIdx) {
            setChild(updatedDstArray, updatedDstArray[i], ArrayIndexChildKind(i))
        }
    }

    private fun removeFromFullyConcretesRec(phys: PhysicalAddress) {
        val queue: Queue<PhysicalAddress> = LinkedList()
        val removed = mutableSetOf<PhysicalAddress>()
        var child: PhysicalAddress? = phys

        while (child != null) {
            if (removed.add(child)) {
                fullyConcretes.remove(child)
                parents[child]?.forEach {
                    val parent = it.key
                    queue.add(parent)
                }
            }
            child = queue.poll()
        }
    }

    private fun markSymbolic(phys: PhysicalAddress) {
        parents[phys]?.forEach {
            val parent = it.key
            children[parent]!![it.value] = Cell()
        }
    }

    fun symbolicMembers(address: UConcreteHeapAddress): List<ChildKind> {
        check(virtToPhys.contains(address))
        val phys = virtToPhys[address]!!
        val symbolicMembers = mutableListOf<ChildKind>()
        children[phys]?.forEach {
            val child = it.value.address
            if (child == null || !checkConcreteness(child))
                symbolicMembers.add(it.key)
        }

        return symbolicMembers
    }

    // TODO: use it someday
    @Suppress("unused")
    fun checkIsCorrect(): Boolean {
        for ((parentPhys, childMap) in children) {
            val obj = parentPhys.obj ?: continue
            for ((childKind, cell) in childMap) {
                if (!cell.isConcrete)
                    continue

                val value = cell.address!!.obj
                when (childKind) {
                    is FieldChildKind -> {
                        if (childKind.field.getFieldValue(obj) !== value)
                            return false
                    }
                    is ArrayIndexChildKind -> {
                        check(obj is Array<*>)
                        if (obj[childKind.index] !== value)
                            return false
                    }
                }
            }
        }

        return true
    }

    //endregion

    //region Effect Storage Interaction

    fun reset() {
        effectStorage.reset()
    }

    //endregion

    //region Allocation

    private fun shouldAllocate(type: JcType): Boolean {
        return !type.typeName.startsWith("org.usvm.api.") &&
                !type.typeName.startsWith("generated.") &&
                !type.typeName.startsWith("stub.") &&
                !type.typeName.startsWith("runtime.")
    }

    private val interningTypes = setOf<JcType>(
        ctx.stringType,
        ctx.classType
    )

    fun allocate(address: UConcreteHeapAddress, obj: Any, type: JcType) {
        check(address != NULL_ADDRESS)
        check(!virtToPhys.containsKey(address))
        val physicalAddress = PhysicalAddress(obj)
        virtToPhys[address] = physicalAddress
        physToVirt[physicalAddress] = address
        typeConstraints.allocate(address, type)
    }

    private fun createNewAddress(type: JcType, static: Boolean): UConcreteHeapAddress {
        if (type.isEnum || type.isEnumArray || static)
            return ctx.addressCounter.freshStaticAddress()

        return ctx.addressCounter.freshAllocatedAddress()
    }

    private fun internIfNeeded(obj: Any): Any {
        return when (obj) {
            is String -> obj.intern()
            else -> obj
        }
    }

    private fun allocate(obj: Any, type: JcType, static: Boolean): UConcreteHeapAddress {
        val interned = internIfNeeded(obj)
        if (interningTypes.contains(type)) {
            val address = tryPhysToVirt(interned)
            if (address != null) {
                return address
            }
        }

        val address = createNewAddress(type, static)
        allocate(address, interned, type)
        return address
    }

    private fun allocateIfShould(obj: Any, type: JcType): UConcreteHeapAddress? {
        if (shouldAllocate(type)) {
            return allocate(obj, type, false)
        }
        return null
    }

    private fun allocateIfShould(type: JcType, static: Boolean): UConcreteHeapAddress? {
        if (shouldAllocate(type)) {
            val obj = createDefault(type) ?: return null
            return allocate(obj, type, static)
        }
        return null
    }

    fun allocate(obj: Any, type: JcType): UConcreteHeapAddress? {
        return allocateIfShould(obj, type)
    }

    fun forceAllocate(obj: Any, type: JcType): UConcreteHeapAddress {
        return allocate(obj, type, false)
    }

    class LambdaInvocationHandler : InvocationHandler {

        private var methodName: String? = null
        private var actualMethod: JcMethod? = null
        private var closureArgs: List<Any?> = listOf()

        fun init(actualMethod: JcMethod, methodName: String, args: List<Any?>) {
            check(actualMethod !is JcEnrichedVirtualMethod)
            this.methodName = methodName
            this.actualMethod = actualMethod
            closureArgs = args
        }

        override fun invoke(proxy: Any?, method: Method, args: Array<Any?>?): Any? {
            if (methodName != null && methodName == method.name) {
                var allArgs =
                    if (args == null) closureArgs
                    else closureArgs + args
                var thisArg: Any? = null
                val methodToInvoke = actualMethod!!
                if (!methodToInvoke.isStatic) {
                    thisArg = allArgs[0]
                    allArgs = allArgs.drop(1)
                }
                return methodToInvoke.invoke(JcConcreteMemoryClassLoader, thisArg, allArgs)

            }

            val newArgs = args ?: arrayOf()
            return InvocationHandler.invokeDefault(proxy, method, *newArgs)
        }
    }

    private fun createProxy(type: JcClassType): Any {
        check(type.jcClass.isInterface)
        return Proxy.newProxyInstance(
            JcConcreteMemoryClassLoader,
            arrayOf(type.toJavaClass(JcConcreteMemoryClassLoader)),
            LambdaInvocationHandler()
        )
    }

    private fun createDefault(type: JcType): Any? {
        try {
            return when (type) {
                is JcArrayType -> type.allocateInstance(JcConcreteMemoryClassLoader, 1)
                is JcClassType -> {
                    if (type.jcClass.isInterface) createProxy(type)
                    else type.allocateInstance(JcConcreteMemoryClassLoader)
                }

                is JcPrimitiveType -> null
                else -> error("JcConcreteMemoryBindings.allocateDefault: unexpected type $type")
            }
        } catch (e: Throwable) {
            println("[WARNING] failed to allocate ${type.internalName}")
            return null
        }
    }

    fun allocateDefaultConcrete(type: JcType): UConcreteHeapAddress? {
        return allocateIfShould(type, false)
    }

    fun allocateDefaultStatic(type: JcType): UConcreteHeapAddress? {
        return allocateIfShould(type, true)
    }

    //endregion

    //region Reading

    fun readClassField(address: UConcreteHeapAddress, field: Field): Any? {
        val obj = virtToPhys(address)
        val value = field.getFieldValue(obj)
        trackChild(obj, value, FieldChildKind(field))
        return value
    }

    fun readArrayIndex(address: UConcreteHeapAddress, index: Int): Any? {
        val obj = virtToPhys(address)
        val value =
            when (obj) {
                is IntArray -> obj[index]
                is ByteArray -> obj[index]
                is CharArray -> obj[index]
                is LongArray -> obj[index]
                is FloatArray -> obj[index]
                is ShortArray -> obj[index]
                is DoubleArray -> obj[index]
                is BooleanArray -> obj[index]
                is Array<*> -> obj[index]
                is String -> obj[index]
                else -> error("JcConcreteMemoryBindings.readArrayIndex: unexpected array $obj")
            }

        trackChild(obj, value, ArrayIndexChildKind(index))

        return value
    }

    // TODO: need "GetAllArrayData"?

    fun readArrayLength(address: UConcreteHeapAddress): Int {
        return when (val obj = virtToPhys(address)) {
            is IntArray -> obj.size
            is ByteArray -> obj.size
            is CharArray -> obj.size
            is LongArray -> obj.size
            is FloatArray -> obj.size
            is ShortArray -> obj.size
            is DoubleArray -> obj.size
            is BooleanArray -> obj.size
            is Array<*> -> obj.size
            is String -> obj.length
            else -> error("JcConcreteMemoryBindings.readArrayLength: unexpected array $obj")
        }
    }

    fun readMapValue(address: UConcreteHeapAddress, key: Any?): Any? {
        val obj = virtToPhys(address)
        obj as Map<*, *>
        return obj[key]
    }

    fun readMapLength(address: UConcreteHeapAddress): Int {
        val obj = virtToPhys(address)
        obj as Map<*, *>
        return obj.size
    }

    fun checkSetContains(address: UConcreteHeapAddress, element: Any?): Boolean {
        val obj = virtToPhys(address)
        obj as Set<*>
        return obj.contains(element)
    }

    fun readInvocationHandler(address: UConcreteHeapAddress): LambdaInvocationHandler {
        val obj = virtToPhys(address)
        check(Proxy.isProxyClass(obj.javaClass))
        return Proxy.getInvocationHandler(obj) as LambdaInvocationHandler
    }

    //endregion

    //region Writing

    fun writeClassField(address: UConcreteHeapAddress, field: Field, value: Any?) {
        val obj = virtToPhys(address)
        if (state == State.MutableWithEffect)
            // TODO: add to backtrack only one field #CM
            effectStorage.addObjectToEffect(obj)

        field.setFieldValue(obj, value)
        setChild(obj, value, FieldChildKind(field))
    }

    fun <Value> writeArrayIndex(address: UConcreteHeapAddress, index: Int, value: Value) {
        val obj = virtToPhys(address)
        if (state == State.MutableWithEffect)
            effectStorage.addObjectToEffect(obj)

        obj.setArrayValue(index, value)
        setChild(obj, value, ArrayIndexChildKind(index))
    }

    @Suppress("UNCHECKED_CAST")
    fun <Value> initializeArray(address: UConcreteHeapAddress, contents: List<Pair<Int, Value>>) {
        val obj = virtToPhys(address)
        if (state == State.MutableWithEffect)
            effectStorage.addObjectToEffect(obj)

        val arrayType = obj.javaClass
        check(arrayType.isArray)
        val elemType = arrayType.componentType
        when (obj) {
            is IntArray -> {
                check(elemType.notTracked)
                for ((index, value) in contents) {
                    obj[index] = value as Int
                }
            }

            is ByteArray -> {
                check(elemType.notTracked)
                for ((index, value) in contents) {
                    obj[index] = value as Byte
                }
            }

            is CharArray -> {
                check(elemType.notTracked)
                for ((index, value) in contents) {
                    obj[index] = value as Char
                }
            }

            is LongArray -> {
                check(elemType.notTracked)
                for ((index, value) in contents) {
                    obj[index] = value as Long
                }
            }

            is FloatArray -> {
                check(elemType.notTracked)
                for ((index, value) in contents) {
                    obj[index] = value as Float
                }
            }

            is ShortArray -> {
                check(elemType.notTracked)
                for ((index, value) in contents) {
                    obj[index] = value as Short
                }
            }

            is DoubleArray -> {
                check(elemType.notTracked)
                for ((index, value) in contents) {
                    obj[index] = value as Double
                }
            }

            is BooleanArray -> {
                check(elemType.notTracked)
                for ((index, value) in contents) {
                    obj[index] = value as Boolean
                }
            }

            is Array<*> -> {
                obj as Array<Value>
                for ((index, value) in contents) {
                    obj[index] = value
                    setChild(obj, value, ArrayIndexChildKind(index))
                }
            }

            else -> error("JcConcreteMemoryBindings.initializeArray: unexpected array $obj")
        }
    }

    fun writeArrayLength(address: UConcreteHeapAddress, length: Int) {
        val arrayType = typeConstraints.typeOf(address)
        arrayType as JcArrayType
        val oldObj = virtToPhys[address]
        val newObj = arrayType.allocateInstance(JcConcreteMemoryClassLoader, length)
        virtToPhys.remove(address)
        physToVirt.remove(oldObj)
        allocate(address, newObj, arrayType)
    }

    @Suppress("UNCHECKED_CAST")
    fun writeMapValue(address: UConcreteHeapAddress, key: Any?, value: Any?) {
        val obj = virtToPhys(address)
        if (state == State.MutableWithEffect)
            effectStorage.addObjectToEffect(obj)

        obj as HashMap<Any?, Any?>
        obj[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    fun writeMapLength(address: UConcreteHeapAddress, length: Int) {
        val obj = virtToPhys(address)
        obj as Map<Any?, Any?>
        check(obj.size == length)
    }

    @Suppress("UNCHECKED_CAST")
    fun changeSetContainsElement(address: UConcreteHeapAddress, element: Any?, contains: Boolean) {
        val obj = virtToPhys(address)
        if (state == State.MutableWithEffect)
            effectStorage.addObjectToEffect(obj)

        obj as MutableSet<Any?>
        if (contains)
            obj.add(element)
        else
            obj.remove(element)
    }

    //endregion

    //region Copying

    @Suppress("UNCHECKED_CAST")
    fun arrayCopy(
        srcAddress: UConcreteHeapAddress,
        dstAddress: UConcreteHeapAddress,
        fromSrcIdx: Int,
        fromDstIdx: Int,
        toDstIdx: Int
    ) {
        val srcArray = virtToPhys(srcAddress)
        val dstArray = virtToPhys(dstAddress)
        if (state == State.MutableWithEffect)
            effectStorage.addObjectToEffect(dstArray)

        val toSrcIdx = toDstIdx - fromDstIdx + fromSrcIdx
        val dstArrayType = dstArray.javaClass
        val dstArrayElemType = dstArrayType.componentType
        when {
            srcArray is IntArray && dstArray is IntArray -> {
                check(dstArrayElemType.notTracked)
                srcArray.copyInto(dstArray, fromDstIdx, fromSrcIdx, toSrcIdx)
            }

            srcArray is ByteArray && dstArray is ByteArray -> {
                check(dstArrayElemType.notTracked)
                srcArray.copyInto(dstArray, fromDstIdx, fromSrcIdx, toSrcIdx)
            }

            srcArray is CharArray && dstArray is CharArray -> {
                check(dstArrayElemType.notTracked)
                srcArray.copyInto(dstArray, fromDstIdx, fromSrcIdx, toSrcIdx)
            }

            srcArray is LongArray && dstArray is LongArray -> {
                check(dstArrayElemType.notTracked)
                srcArray.copyInto(dstArray, fromDstIdx, fromSrcIdx, toSrcIdx)
            }

            srcArray is FloatArray && dstArray is FloatArray -> {
                check(dstArrayElemType.notTracked)
                srcArray.copyInto(dstArray, fromDstIdx, fromSrcIdx, toSrcIdx)
            }

            srcArray is ShortArray && dstArray is ShortArray -> {
                check(dstArrayElemType.notTracked)
                srcArray.copyInto(dstArray, fromDstIdx, fromSrcIdx, toSrcIdx)
            }

            srcArray is DoubleArray && dstArray is DoubleArray -> {
                check(dstArrayElemType.notTracked)
                srcArray.copyInto(dstArray, fromDstIdx, fromSrcIdx, toSrcIdx)
            }

            srcArray is BooleanArray && dstArray is BooleanArray -> {
                check(dstArrayElemType.notTracked)
                srcArray.copyInto(dstArray, fromDstIdx, fromSrcIdx, toSrcIdx)
            }

            srcArray is Array<*> && dstArray is Array<*> -> {
                dstArray as Array<Any?>
                srcArray.copyInto(dstArray, fromDstIdx, fromSrcIdx, toSrcIdx)
                trackCopy(dstArray, dstArrayType, fromDstIdx, toDstIdx)
            }

            else -> error("JcConcreteMemoryBindings.arrayCopy: unexpected arrays $srcArray, $dstArray")
        }
    }

    //endregion

    //region Map Merging

    @Suppress("UNCHECKED_CAST")
    fun mapMerge(srcAddress: UConcreteHeapAddress, dstAddress: UConcreteHeapAddress) {
        val srcMap = virtToPhys(srcAddress) as HashMap<Any, Any>
        val dstMap = virtToPhys(dstAddress) as HashMap<Any, Any>
        if (state == State.MutableWithEffect)
            effectStorage.addObjectToEffect(dstMap)

        dstMap.putAll(srcMap)
    }

    //endregion

    //region Set Union

    @Suppress("UNCHECKED_CAST")
    fun setUnion(srcAddress: UConcreteHeapAddress, dstAddress: UConcreteHeapAddress) {
        val srcSet = virtToPhys(srcAddress) as MutableSet<Any>
        val dstSet = virtToPhys(dstAddress) as MutableSet<Any>
        if (state == State.MutableWithEffect)
            effectStorage.addObjectToEffect(dstSet)

        dstSet.addAll(srcSet)
    }

    //endregion

    //region Removing

    fun remove(address: UConcreteHeapAddress) {
        val phys = virtToPhys.remove(address)!!
        removeFromFullyConcretesRec(phys)
        markSymbolic(phys)
    }

    //endregion

    //region Copy

    private fun copyChildren(): childrenType {
        val newChildren = hashMapOf<PhysicalAddress, childMapType>()
        for ((parent, childMap) in children) {
            val newChildMap = HashMap(childMap)
            newChildren[parent] = newChildMap
        }

        return newChildren
    }

    private fun copyParents(): parentsType {
        val newParents = hashMapOf<PhysicalAddress, parentMapType>()
        for ((child, parentMap) in parents) {
            val newParentMap = HashMap(parentMap)
            newParents[child] = newParentMap
        }

        return newParents
    }

    fun copy(typeConstraints: UTypeConstraints<JcType>): JcConcreteMemoryBindings {
        val newBindings = JcConcreteMemoryBindings(
            ctx,
            typeConstraints,
            HashMap(physToVirt),
            HashMap(virtToPhys),
            state,
            copyChildren(),
            copyParents(),
            fullyConcretes.toMutableSet(),
            effectStorage.copy(),
            threadLocalHelper,
        )
        newBindings.makeMutableWithEffect()
        makeMutableWithEffect()
        return newBindings
    }

    //endregion
}
