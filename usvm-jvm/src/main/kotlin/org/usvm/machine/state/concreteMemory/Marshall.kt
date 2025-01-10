package org.usvm.machine.state.concreteMemory

import io.ksmt.expr.KBitVec16Value
import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KBitVec64Value
import io.ksmt.expr.KBitVec8Value
import io.ksmt.expr.KFp32Value
import io.ksmt.expr.KFp64Value
import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypeVariable
import org.jacodb.api.jvm.JcTypedField
import org.jacodb.api.jvm.ext.annotation
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.char
import org.jacodb.api.jvm.ext.double
import org.jacodb.api.jvm.ext.findClassOrNull
import org.jacodb.api.jvm.ext.float
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.isAssignable
import org.jacodb.api.jvm.ext.long
import org.jacodb.api.jvm.ext.short
import org.jacodb.api.jvm.ext.toType
import org.jacodb.api.jvm.ext.void
import org.jacodb.approximation.JcEnrichedVirtualField
import org.jacodb.impl.features.classpaths.JcUnknownType
import org.jacodb.impl.features.hierarchyExt
import org.usvm.NULL_ADDRESS
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.UNullRef
import org.usvm.USort
import org.usvm.USymbol
import org.usvm.api.SymbolicIdentityMap
import org.usvm.api.SymbolicList
import org.usvm.api.SymbolicMap
import org.usvm.api.encoder.EncoderFor
import org.usvm.api.encoder.ObjectEncoder
import org.usvm.api.util.JcConcreteMemoryClassLoader
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.machine.JcContext
import org.usvm.util.Maybe
import org.usvm.util.name
import org.usvm.util.onNone
import org.usvm.util.onSome
import java.lang.reflect.InvocationTargetException

internal class Marshall(
    private val ctx: JcContext,
    private val bindings: JcConcreteMemoryBindings,
    var regionStorage: JcConcreteRegionStorage,
) {

    //region Helpers

    private val boolean by lazy { ctx.cp.boolean }
    private val byte by lazy { ctx.cp.byte }
    private val short by lazy { ctx.cp.short }
    private val char by lazy { ctx.cp.char }
    private val int by lazy { ctx.cp.int }
    private val long by lazy { ctx.cp.long }
    private val float by lazy { ctx.cp.float }
    private val double by lazy { ctx.cp.double }
    private val void by lazy { ctx.cp.void }
    private val trueExpr by lazy { ctx.trueExpr }
    private val falseExpr by lazy { ctx.falseExpr }

    private val Any?.maybe: Maybe<Any?>
        get() = Maybe.some(this)

    private val usvmApiSymbolicList by lazy { ctx.cp.findClassOrNull<SymbolicList<*>>()!!.toType() }
    private val javaList by lazy { ctx.cp.findClassOrNull<List<*>>()!!.toType() }
    private val usvmApiSymbolicMap by lazy { ctx.cp.findClassOrNull<SymbolicMap<*, *>>()!!.toType() }
    private val usvmApiSymbolicIdentityMap by lazy { ctx.cp.findClassOrNull<SymbolicIdentityMap<*, *>>()!!.toType() }
    private val javaMap by lazy { ctx.cp.findClassOrNull<Map<*, *>>()!!.toType() }

    //endregion

    //region Encoders

    private val encoders by lazy { loadEncoders() }

    private fun loadEncoders(): Map<JcClassOrInterface, Any> {
        val objectEncoderClass = ctx.cp.findClassOrNull(ObjectEncoder::class.java.name)!!
        return runBlocking {
            ctx.cp.hierarchyExt()
                .findSubClasses(objectEncoderClass, entireHierarchy = true, includeOwn = false)
                .mapNotNull { loadEncoder(it) }
                .toMap()
        }
    }

    private fun loadEncoder(encoder: JcClassOrInterface): Pair<JcClassOrInterface, Any>? {
        val target = encoder.annotation(EncoderFor::class.java.name)!!
        val targetCls = target.values["value"] ?: return null

        targetCls as JcClassOrInterface

        val encoderCls = JcConcreteMemoryClassLoader.loadClass(encoder)
        val encoderInstance = encoderCls.getConstructor().newInstance()

        return targetCls to encoderInstance
    }

    //endregion

    //region Expression To Object Conversion

    fun <Sort : USort> commonTryExprToObj(expr: UExpr<Sort>, type: JcType, fullyConcrete: Boolean): Maybe<Any?> {
        return when {
            expr is UNullRef -> Maybe.some(null)
            expr is UConcreteHeapRef && expr.address == NULL_ADDRESS -> Maybe.some(null)
            expr is UConcreteHeapRef && fullyConcrete ->
                bindings.tryFullyConcrete(expr.address)?.maybe ?: Maybe.none()

            expr is UConcreteHeapRef ->
                bindings.tryVirtToPhys(expr.address)?.maybe ?: Maybe.none()

            expr is USymbol -> Maybe.none()
            type == boolean -> {
                when (expr) {
                    trueExpr -> true.maybe
                    falseExpr -> false.maybe
                    else -> Maybe.none()
                }
            }

            type == byte -> {
                when (expr) {
                    is KBitVec8Value -> expr.byteValue.maybe
                    else -> Maybe.none()
                }
            }

            type == short -> {
                when (expr) {
                    is KBitVec16Value -> expr.shortValue.maybe
                    else -> Maybe.none()
                }
            }

            type == char -> {
                when (expr) {
                    is KBitVec16Value -> expr.shortValue.toInt().toChar().maybe
                    else -> Maybe.none()
                }
            }

            type == int -> {
                when (expr) {
                    is KBitVec32Value -> expr.intValue.maybe
                    else -> Maybe.none()
                }
            }

            type == long -> {
                when (expr) {
                    is KBitVec64Value -> expr.longValue.maybe
                    else -> Maybe.none()
                }
            }

            type == float -> {
                when (expr) {
                    is KFp32Value -> expr.value.maybe
                    else -> Maybe.none()
                }
            }

            type == double -> {
                when (expr) {
                    is KFp64Value -> expr.value.maybe
                    else -> Maybe.none()
                }
            }

            expr is UIteExpr && expr.condition.isTrue -> commonTryExprToObj(expr.trueBranch, type, fullyConcrete)
            expr is UIteExpr && expr.condition.isFalse -> commonTryExprToObj(expr.falseBranch, type, fullyConcrete)
            expr is UIteExpr -> Maybe.none()

            else -> error("Marshall.commonTryExprToObj: unexpected expression $expr")
        }
    }

    fun <Sort : USort> tryExprToObj(expr: UExpr<Sort>, type: JcType): Maybe<Any?> {
        return commonTryExprToObj(expr, type, false)
    }

    fun <Sort : USort> tryExprToFullyConcreteObj(expr: UExpr<Sort>, type: JcType): Maybe<Any?> {
        return commonTryExprToObj(expr, type, true)
    }

    fun tryExprListToFullyConcreteList(
        expressions: List<UExpr<*>>,
        types: List<JcType>
    ): Maybe<List<Any?>> {
        val result = mutableListOf<Any?>()
        for ((e, t) in expressions.zip(types)) {
            tryExprToFullyConcreteObj(e, t)
                .onNone { return Maybe.none() }
                .onSome { result.add(it) }
        }

        return Maybe.some(result)
    }

    //endregion

    //region Object To Expression Conversion

    private fun typeOfObject(obj: Any): JcType? {
        return obj.javaClass.toJcType(ctx)
    }

    private fun referenceTypeToExpr(obj: Any?, type: JcRefType): UHeapRef {
        if (obj == null)
            return ctx.nullRef

        var address = bindings.tryPhysToVirt(obj)
        if (address == null) {
            val objType = typeOfObject(obj)
            val mostConcreteType = when {
                objType is JcUnknownType -> type
                objType != null && (objType.isAssignable(type) || type is JcTypeVariable) -> objType
                else -> type
            }
            address = bindings.forceAllocate(obj, mostConcreteType)
            when {
                type.isAssignable(usvmApiSymbolicList) -> unmarshallSymbolicList(address, obj)
                type.isAssignable(usvmApiSymbolicMap) -> unmarshallSymbolicMap(address, obj)
                type.isAssignable(usvmApiSymbolicIdentityMap) -> unmarshallSymbolicIdentityMap(address, obj)
            }
        }

        return ctx.mkConcreteHeapRef(address)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <Sort : USort> primitiveTypeToExpr(obj: Any): UExpr<Sort> {
        with(ctx) {
            return when (obj) {
                is Boolean -> mkBool(obj)
                is Byte -> mkBv(obj)
                is Short -> mkBv(obj)
                is Char -> mkBv(obj.code, charSort)
                is Int -> mkBv(obj)
                is Long -> mkBv(obj)
                is Float -> mkFp(obj, floatSort)
                is Double -> mkFp(obj, doubleSort)
                else -> error("Marshall.primitiveTypeToExpr: unexpected object $obj")
            } as UExpr<Sort>
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <Sort : USort> objToExpr(obj: Any?, type: JcType): UExpr<Sort> {
        return when (type) {
            void -> ctx.voidValue
            is JcPrimitiveType -> primitiveTypeToExpr(obj!!)
            is JcArrayType, is JcClassType, is JcTypeVariable -> referenceTypeToExpr(obj, type as JcRefType)
            else -> error("Marshall.objToExpr: unexpected object $obj with type ${type.typeName}")
        } as UExpr<Sort>
    }

    //endregion

    //region Unmarshall

    private fun unmarshallFields(address: UConcreteHeapAddress, obj: Any, fields: List<JcTypedField>) {
        val ref = ctx.mkConcreteHeapRef(address)
        fields.forEach {
            try {
                regionStorage.getFieldRegion(it).unmarshallField(ref, obj)
            } catch (e: Exception) {
                error("unmarshallFields failed on field ${it.name}, exception: $e")
            }
        }
    }

    fun unmarshallClass(address: UConcreteHeapAddress) {
        val obj = bindings.tryVirtToPhys(address) ?: return
        val type = bindings.typeOf(address)
        type as JcClassType
        val allFields = type.allInstanceFields
        val containsApproximationFields = allFields.any { it.field.toJavaField == null }
        if (containsApproximationFields) {
            encode(address)
            return
        }

        unmarshallFields(address, obj, allFields)
        bindings.remove(address)
    }

    private fun unmarshallArray(
        address: UConcreteHeapAddress,
        obj: Any,
        descriptor: JcType,
        elemSort: USort,
    ) {
        val ref = ctx.mkConcreteHeapRef(address)
        val arrayRegion = regionStorage.getArrayRegion(descriptor, elemSort)
        val arrayLengthRegion = regionStorage.getArrayLengthRegion(descriptor)
        when (obj) {
            is IntArray -> {
                arrayRegion.unmarshallArray(address, obj)
                arrayLengthRegion.unmarshallLength(ref, obj)
            }

            is ByteArray -> {
                arrayRegion.unmarshallArray(address, obj)
                arrayLengthRegion.unmarshallLength(ref, obj)
            }

            is CharArray -> {
                arrayRegion.unmarshallArray(address, obj)
                arrayLengthRegion.unmarshallLength(ref, obj)
            }

            is LongArray -> {
                arrayRegion.unmarshallArray(address, obj)
                arrayLengthRegion.unmarshallLength(ref, obj)
            }

            is FloatArray -> {
                arrayRegion.unmarshallArray(address, obj)
                arrayLengthRegion.unmarshallLength(ref, obj)
            }

            is ShortArray -> {
                arrayRegion.unmarshallArray(address, obj)
                arrayLengthRegion.unmarshallLength(ref, obj)
            }

            is DoubleArray -> {
                arrayRegion.unmarshallArray(address, obj)
                arrayLengthRegion.unmarshallLength(ref, obj)
            }

            is BooleanArray -> {
                arrayRegion.unmarshallArray(address, obj)
                arrayLengthRegion.unmarshallLength(ref, obj)
            }

            is Array<*> -> {
                arrayRegion.unmarshallArray(address, obj, descriptor)
                arrayLengthRegion.unmarshallLength(ref, obj)
            }
        }
        bindings.remove(address)
    }

    fun unmarshallArray(address: UConcreteHeapAddress) {
        val obj = bindings.tryVirtToPhys(address) ?: return
        val type = bindings.typeOf(address)
        type as JcArrayType
        val desc = ctx.arrayDescriptorOf(type)
        val elemSort = ctx.typeToSort(type.elementType)
        unmarshallArray(address, obj, desc, elemSort)
    }

    private fun unmarshallMap(address: UConcreteHeapAddress, obj: Map<*, *>, mapType: JcType) {
        val ref = ctx.mkConcreteHeapRef(address)
        val valueSort = ctx.addressSort
        val mapRegion = regionStorage.getMapRegion(mapType, valueSort)
        val mapLengthRegion = regionStorage.getMapLengthRegion(mapType)
        val keySetRegion = regionStorage.getSetRegion(mapType)
        mapRegion.unmarshallContents(ref, obj)
        mapLengthRegion.unmarshallLength(ref, obj)
        keySetRegion.unmarshallContents(ref, obj.keys)
        bindings.remove(address)
    }

    fun unmarshallMap(address: UConcreteHeapAddress, mapType: JcType) {
        val obj = bindings.tryVirtToPhys(address) ?: return
        obj as Map<*, *>
        unmarshallMap(address, obj, mapType)
    }

    @Suppress("UNUSED_VARIABLE")
    fun unmarshallSet(address: UConcreteHeapAddress) {
        val obj = bindings.tryVirtToPhys(address) ?: return
        TODO("unmarshall set")
//        obj as Set<*, *>
//        unmarshallMap(address, obj, mapType)
    }

    fun unmarshallSymbolicList(address: UConcreteHeapAddress, obj: Any) {
        val methods = obj.javaClass.declaredMethods
        val getMethod = methods.find { it.name == "get" }!!
        val size = methods.find { it.name == "size" }!!.invoke(obj) as Int
        val array = Array<Any?>(size) { getMethod.invoke(obj, it) }
        unmarshallArray(address, array, usvmApiSymbolicList, ctx.addressSort)
    }

    private fun createMapFromSymbolicMap(obj: Any): Map<*, *> {
        val mapMethods = obj.javaClass.declaredMethods
        val entriesMethod = mapMethods.find { it.name == "entries" }!!
        val entries = entriesMethod.invoke(obj) as Array<*>
        val map = HashMap<Any?, Any?>()
        entries.forEach { entry ->
            val entryMethods = entry!!.javaClass.declaredMethods
            val getKeyMethod = entryMethods.find { it.name == "getKey" }!!
            val getValueMethod = entryMethods.find { it.name == "getValue" }!!
            getKeyMethod.isAccessible = true
            val key = getKeyMethod.invoke(entry)
            getValueMethod.isAccessible = true
            val value = getValueMethod.invoke(entry)
            map[key] = value
        }
        return map
    }

    fun unmarshallSymbolicMap(address: UConcreteHeapAddress, obj: Any) {
        val map = createMapFromSymbolicMap(obj)
        unmarshallMap(address, map, usvmApiSymbolicMap)
    }

    fun unmarshallSymbolicIdentityMap(address: UConcreteHeapAddress, obj: Any) {
        val map = createMapFromSymbolicMap(obj)
        unmarshallMap(address, map, usvmApiSymbolicIdentityMap)
    }

    //endregion

    //region Encoding

    fun encode(address: UConcreteHeapAddress) {
        val obj = bindings.virtToPhys(address)
        val type = bindings.typeOf(address) as JcClassType
        println("encoding for $address of ${type.name}")
        var encoder: Any? = null
        var jcClass: JcClassOrInterface? = type.jcClass
        var searchingEncoder = true
        while (jcClass != null && searchingEncoder) {
            encoder = encoders[jcClass]
            searchingEncoder = encoder == null
            jcClass = if (searchingEncoder) jcClass.superClass else jcClass
        }
        encoder ?: error("Failed to find encoder for type ${type.name}")
        val encodeMethod = encoder.javaClass.declaredMethods.find { it.name == "encode" }!!
        val approximatedObj = try {
            encodeMethod.invoke(encoder, obj)
        } catch (e: Throwable) {
            var exception = e
            if (e is InvocationTargetException)
                exception = e.targetException
            println("Encoder $encodeMethod threw exception $exception")
            throw exception
        }
        val allFields = type.allInstanceFields
        val approximationFields = allFields.filter { it.field is JcEnrichedVirtualField }
        unmarshallFields(address, approximatedObj, approximationFields)

        val otherFields = ArrayList<JcTypedField>()
        var currentClass = type
        while (currentClass.jcClass != jcClass) {
            val fields = currentClass.declaredInstanceFields
            if (fields.any { it.field is JcEnrichedVirtualField })
                error("Encoding: failed to find encoder for ${currentClass.name}")
            otherFields.addAll(fields)
            currentClass = currentClass.superType ?: error("Encoding: supertype is null")
        }
        if (otherFields.isNotEmpty())
            unmarshallFields(address, obj, otherFields)

        bindings.remove(address)
    }

    //endregion

    fun copy(bindings: JcConcreteMemoryBindings, regionStorage: JcConcreteRegionStorage): Marshall {
        return Marshall(ctx, bindings, regionStorage)
    }
}
