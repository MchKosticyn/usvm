package machine.concreteMemory

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import machine.JcConcreteMemoryClassLoader
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcType
import org.usvm.NULL_ADDRESS
import org.usvm.UConcreteHeapAddress
import org.usvm.api.util.Reflection.allocateInstance
import org.usvm.constraints.UTypeConstraints
import org.usvm.machine.JcContext
import utils.ForbiddenModificationException
import utils.LambdaInvocationHandler
import utils.allInstanceFields
import utils.createDefault
import utils.getFieldValue
import utils.isEnum
import utils.isEnumArray
import utils.isInternalType
import utils.isSolid
import utils.notTracked
import utils.notTrackedWithSubtypes
import utils.setArrayValue
import utils.setFieldValue
import java.lang.reflect.Field
import java.lang.reflect.Proxy
import java.util.IdentityHashMap

//region State

private enum class State {
    Mutable,
    MutableWithEffect,
    Dead
}

//endregion

internal class JcConcreteMemoryBindings private constructor(
    private val ctx: JcContext,
    private val typeConstraints: UTypeConstraints<JcType>,
    private val physToVirt: IdentityHashMap<Any?, UConcreteHeapAddress>,
    private val virtToPhys: Object2ObjectOpenHashMap<UConcreteHeapAddress, Any?>,
    private var state: State,
    private val transitiveConcretes: IdentityHashMap<Any, Unit>,
    private val transitiveSymbolics: IdentityHashMap<Any, Unit>,
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
        IdentityHashMap(),
        Object2ObjectOpenHashMap(),
        State.Mutable,
        IdentityHashMap(),
        IdentityHashMap(),
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
        return virtToPhys[address]
    }

    fun virtToPhys(address: UConcreteHeapAddress): Any {
        return virtToPhys[address]!!
    }

    fun tryFullyConcrete(address: UConcreteHeapAddress): Any? {
        val obj = virtToPhys[address]
        if (obj != null && checkConcreteness(obj)) {
            return obj
        }
        return null
    }

    fun tryPhysToVirt(obj: Any): UConcreteHeapAddress? {
        return physToVirt[obj]
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

    private fun isConcrete(obj: Any): Boolean {
        val address = physToVirt[obj] ?: return true
        return virtToPhys.contains(address)
    }

    private fun checkConcreteness(obj: Any): Boolean {
        val traverser = ConcretenessTraversal()
        val handledObjects = traverser.traverse(obj)
        val isConcrete = traverser.isConcrete
        if (isConcrete)
            transitiveConcretes.putAll(handledObjects)
        else
            transitiveSymbolics[obj] = Unit

        return isConcrete
    }

    // TODO: cache more info about symbolic objects
    private inner class ConcretenessTraversal : ObjectTraversal(threadLocalHelper, false) {

        private var isConcreteVar = true

        private fun symbolicFound() {
            isConcreteVar = false
            stop()
        }

        private fun checkConcreteness(obj: Any) {
            if (transitiveSymbolics.contains(obj)) {
                symbolicFound()
                return
            }

            if (!isConcrete(obj))
                symbolicFound()
        }

        override fun skip(obj: Any, type: Class<*>): Boolean {
            return type.isSolid || transitiveConcretes.contains(obj)
        }

        override fun skipField(field: Field): Boolean {
            return field.type.notTrackedWithSubtypes
        }

        override fun skipArrayIndices(elementType: Class<*>): Boolean {
            return elementType.notTrackedWithSubtypes
        }

        override fun handleArray(array: Any, type: Class<*>) {
            checkConcreteness(array)
        }

        override fun handleClass(obj: Any, type: Class<*>) {
            checkConcreteness(obj)
        }

        override fun handleThreadLocal(threadLocal: Any, value: Any?) {
            if (value != null)
                checkConcreteness(value)
        }

        val isConcrete: Boolean
            get() = isConcreteVar
    }

    fun symbolicIndices(array: Array<*>): List<Int> {
        val symbolicIndices = mutableListOf<Int>()
        array.forEachIndexed { i, e ->
            if (e != null && !checkConcreteness(e)) {
                symbolicIndices.add(i)
            }
        }

        return symbolicIndices
    }

    fun symbolicFields(obj: Any): List<Field> {
        val type = obj.javaClass
        val symbolicFields = mutableListOf<Field>()
        for (field in type.allInstanceFields) {
            val value = try {
                field.getFieldValue(obj)
            } catch (e: Throwable) {
                println("[WARNING] symbolicFields: ${type.name} failed on field ${field.name}, cause: ${e.message}")
                continue
            }
            if (value != null && !checkConcreteness(value))
                symbolicFields.add(field)
        }

        return symbolicFields
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
        virtToPhys[address] = obj
        physToVirt[obj] = address
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
            if (address != null)
                return address
        }

        val address = createNewAddress(type, static)
        allocate(address, interned, type)
        return address
    }

    private fun allocateIfShould(obj: Any, type: JcType): UConcreteHeapAddress? {
        if (shouldAllocate(type))
            return allocate(obj, type, false)

        return null
    }

    private fun allocateIfShould(type: JcType, static: Boolean): UConcreteHeapAddress? {
        if (shouldAllocate(type)) {
            val obj = createDefault(type) ?: return null
            transitiveConcretes[obj] = Unit
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

    fun allocateDefaultConcrete(type: JcType): UConcreteHeapAddress? {
        return allocateIfShould(type, false)
    }

    fun allocateDefaultStatic(type: JcType): UConcreteHeapAddress? {
        return allocateIfShould(type, true)
    }

    fun dummyAllocate(type: JcType): UConcreteHeapAddress {
        val address = createNewAddress(type, false)
        typeConstraints.allocate(address, type)
        return address
    }

    //endregion

    //region Reading

    fun readClassField(address: UConcreteHeapAddress, field: Field): Pair<Boolean, Any?> {
        val obj = virtToPhys(address)
        if (!field.declaringClass.isAssignableFrom(obj.javaClass))
            return false to null

        return true to field.getFieldValue(obj)
    }

    private fun indexIsValid(obj: Any, index: Int): Boolean {
        return when (obj) {
            is IntArray -> index in 0 until obj.size
            is ByteArray -> index in 0 until obj.size
            is CharArray -> index in 0 until obj.size
            is LongArray -> index in 0 until obj.size
            is FloatArray -> index in 0 until obj.size
            is ShortArray -> index in 0 until obj.size
            is DoubleArray -> index in 0 until obj.size
            is BooleanArray -> index in 0 until obj.size
            is Array<*> -> index in 0 until obj.size
            is String -> index in 0 until obj.length
            else -> error("JcConcreteMemoryBindings.readArrayIndex: unexpected array $obj")
        }
    }

    fun readArrayIndex(address: UConcreteHeapAddress, index: Int): Pair<Boolean, Any?> {
        val obj = virtToPhys(address)
        if (!indexIsValid(obj, index))
            return false to null

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

        return true to value
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

    fun writeClassField(address: UConcreteHeapAddress, field: Field, value: Any?): Boolean {
        val obj = virtToPhys(address)

        if (!field.declaringClass.isAssignableFrom(obj.javaClass))
            return false

        if (state == State.MutableWithEffect)
            // TODO: add to backtrack only one field #CM
            effectStorage.addObjectToEffect(obj)

        try {
            field.setFieldValue(obj, value)
        } catch (e: ForbiddenModificationException) {
            return false
        }

        concreteChange()
        return true
    }

    fun <Value> writeArrayIndex(address: UConcreteHeapAddress, index: Int, value: Value): Boolean {
        val obj = virtToPhys(address)
        if (!indexIsValid(obj, index))
            return false

        if (state == State.MutableWithEffect)
            effectStorage.addObjectToEffect(obj)

        obj.setArrayValue(index, value)

        concreteChange()
        return true
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
                }
            }

            else -> error("JcConcreteMemoryBindings.initializeArray: unexpected array $obj")
        }
        concreteChange()
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
        concreteChange()
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

        concreteChange()
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
            }

            else -> error("JcConcreteMemoryBindings.arrayCopy: unexpected arrays $srcArray, $dstArray")
        }

        concreteChange()
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
        concreteChange()
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
        concreteChange()
    }

    //endregion

    private fun symbolicChange(obj: Any, isNew: Boolean) {
        if (isNew)
            transitiveConcretes.remove(obj)
        else
            transitiveConcretes.clear()
    }

    private fun concreteChange() {
        transitiveSymbolics.clear()
    }

    //region Removing

    fun remove(
        address: UConcreteHeapAddress,
        isSymbolic: Boolean = true,
        isNew: Boolean = false
    ) {
        val obj = virtToPhys.remove(address) ?: return
        if (isSymbolic && !obj.javaClass.isInternalType)
            symbolicChange(obj, isNew)
    }

    //endregion

    //region Copy

    fun copy(typeConstraints: UTypeConstraints<JcType>): JcConcreteMemoryBindings {
        val newBindings = JcConcreteMemoryBindings(
            ctx,
            typeConstraints,
            IdentityHashMap(physToVirt),
            virtToPhys.clone(),
            state,
            IdentityHashMap(transitiveConcretes),
            IdentityHashMap(transitiveSymbolics),
            effectStorage.copy(),
            threadLocalHelper,
        )
        newBindings.makeMutableWithEffect()
        makeMutableWithEffect()
        return newBindings
    }

    //endregion
}
