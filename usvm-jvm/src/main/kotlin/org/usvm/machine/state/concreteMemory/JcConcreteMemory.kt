package org.usvm.machine.state.concreteMemory

import io.ksmt.utils.asExpr
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.api.jvm.ext.findFieldOrNull
import org.jacodb.api.jvm.ext.findTypeOrNull
import org.jacodb.api.jvm.ext.humanReadableSignature
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.isEnum
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.approximation.JcEnrichedVirtualMethod
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIndexedMocker
import org.usvm.USort
import org.usvm.api.readArrayIndex
import org.usvm.api.readField
import org.usvm.api.util.JcConcreteMemoryClassLoader
import org.usvm.api.util.JcTestInterpreterDecoderApi
import org.usvm.api.util.JcTestStateResolver
import org.usvm.api.util.JcTestStateResolver.ResolveMode
import org.usvm.api.util.Reflection.allocateInstance
import org.usvm.api.util.Reflection.invoke
import org.usvm.collection.array.UArrayRegion
import org.usvm.collection.array.UArrayRegionId
import org.usvm.collection.array.length.UArrayLengthsRegion
import org.usvm.collection.array.length.UArrayLengthsRegionId
import org.usvm.collection.field.UFieldsRegion
import org.usvm.collection.field.UFieldsRegionId
import org.usvm.collection.map.length.UMapLengthRegion
import org.usvm.collection.map.length.UMapLengthRegionId
import org.usvm.collection.map.primitive.UMapRegionId
import org.usvm.collection.map.ref.URefMapRegion
import org.usvm.collection.map.ref.URefMapRegionId
import org.usvm.collection.set.primitive.USetRegionId
import org.usvm.collection.set.ref.URefSetRegion
import org.usvm.collection.set.ref.URefSetRegionId
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.constraints.UTypeConstraints
import org.usvm.machine.JcConcreteInvocationResult
import org.usvm.machine.JcContext
import org.usvm.machine.JcMethodCall
import org.usvm.machine.USizeSort
import org.usvm.machine.interpreter.JcExprResolver
import org.usvm.machine.interpreter.JcLambdaCallSiteMemoryRegion
import org.usvm.machine.interpreter.JcLambdaCallSiteRegionId
import org.usvm.machine.interpreter.statics.JcStaticFieldRegionId
import org.usvm.machine.interpreter.statics.JcStaticFieldsMemoryRegion
import org.usvm.machine.state.JcState
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteArrayLengthRegion
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteArrayRegion
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteCallSiteLambdaRegion
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteFieldRegion
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteMapLengthRegion
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteRefMapRegion
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteRefSetRegion
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteRegion
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteStaticFieldsRegion
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.skipMethodInvocationWithValue
import org.usvm.machine.state.throwExceptionWithoutStackFrameDrop
import org.usvm.memory.UMemory
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.URegistersStack
import org.usvm.mkSizeExpr
import org.usvm.util.jcTypeOf
import org.usvm.util.name
import org.usvm.util.onNone
import org.usvm.util.onSome
import org.usvm.util.typedField
import org.usvm.utils.applySoftConstraints
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException

//region Concrete Memory

class JcConcreteMemory private constructor(
    private val ctx: JcContext,
    ownership: MutabilityOwnership,
    typeConstraints: UTypeConstraints<JcType>,
    stack: URegistersStack,
    mocks: UIndexedMocker<JcMethod>,
    regions: UPersistentHashMap<UMemoryRegionId<*, *>, UMemoryRegion<*, *>>,
    private val executor: JcConcreteExecutor,
    private val bindings: JcConcreteMemoryBindings,
    private var regionStorageVar: JcConcreteRegionStorage? = null,
    private var marshallVar: Marshall? = null,
) : UMemory<JcType, JcMethod>(ctx, ownership, typeConstraints, stack, mocks, regions), JcConcreteRegionGetter {

    private val ansiReset: String = "\u001B[0m"
    private val ansiBlack: String = "\u001B[30m"
    private val ansiRed: String = "\u001B[31m"
    private val ansiGreen: String = "\u001B[32m"
    private val ansiYellow: String = "\u001B[33m"
    private val ansiBlue: String = "\u001B[34m"
    private val ansiWhite: String = "\u001B[37m"
    private val ansiPurple: String = "\u001B[35m"
    private val ansiCyan: String = "\u001B[36m"

    private val regionStorage: JcConcreteRegionStorage
        get() = regionStorageVar!!

    private val marshall: Marshall
        get() = marshallVar!!

    private var concretization = false

    //region 'JcConcreteRegionGetter' implementation

    @Suppress("UNCHECKED_CAST")
    override fun <Key, Sort : USort> getConcreteRegion(regionId: UMemoryRegionId<Key, Sort>): JcConcreteRegion {
        val baseRegion = super.getRegion(regionId)
        return when (regionId) {
            is UFieldsRegionId<*, *> -> {
                baseRegion as UFieldsRegion<JcField, Sort>
                regionId as UFieldsRegionId<JcField, Sort>
                JcConcreteFieldRegion(regionId, ctx, bindings, baseRegion, marshall, ownership)
            }

            is UArrayRegionId<*, *, *> -> {
                baseRegion as UArrayRegion<JcType, Sort, USizeSort>
                regionId as UArrayRegionId<JcType, Sort, USizeSort>
                JcConcreteArrayRegion(regionId, ctx, bindings, baseRegion, marshall, ownership)
            }

            is UArrayLengthsRegionId<*, *> -> {
                baseRegion as UArrayLengthsRegion<JcType, USizeSort>
                regionId as UArrayLengthsRegionId<JcType, USizeSort>
                JcConcreteArrayLengthRegion(regionId, ctx, bindings, baseRegion, marshall, ownership)
            }

            is URefMapRegionId<*, *> -> {
                baseRegion as URefMapRegion<JcType, Sort>
                regionId as URefMapRegionId<JcType, Sort>
                JcConcreteRefMapRegion(regionId, ctx, bindings, baseRegion, marshall, ownership)
            }

            is UMapLengthRegionId<*, *> -> {
                baseRegion as UMapLengthRegion<JcType, USizeSort>
                regionId as UMapLengthRegionId<JcType, USizeSort>
                JcConcreteMapLengthRegion(regionId, ctx, bindings, baseRegion, marshall, ownership)
            }

            is URefSetRegionId<*> -> {
                baseRegion as URefSetRegion<JcType>
                regionId as URefSetRegionId<JcType>
                JcConcreteRefSetRegion(regionId, ctx, bindings, baseRegion, marshall, ownership)
            }

            is JcStaticFieldRegionId<*> -> {
                baseRegion as JcStaticFieldsMemoryRegion<Sort>
                val id = regionId as JcStaticFieldRegionId<Sort>
                JcConcreteStaticFieldsRegion(id, baseRegion, marshall, ownership)
            }

            is JcLambdaCallSiteRegionId -> {
                baseRegion as JcLambdaCallSiteMemoryRegion
                JcConcreteCallSiteLambdaRegion(ctx, bindings, baseRegion, marshall, ownership)
            }

            else -> baseRegion
        } as JcConcreteRegion
    }

    //endregion

    //region 'UMemory' implementation

    @Suppress("UNCHECKED_CAST")
    override fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UMemoryRegion<Key, Sort> {
        return when (regionId) {
            is UFieldsRegionId<*, *> -> regionStorage.getFieldRegion(regionId)
            is UArrayRegionId<*, *, *> -> regionStorage.getArrayRegion(regionId)
            is UArrayLengthsRegionId<*, *> -> regionStorage.getArrayLengthRegion(regionId)
            is UMapRegionId<*, *, *, *> -> error("ConcreteMemory.getRegion: unexpected 'UMapRegionId'")
            is URefMapRegionId<*, *> -> regionStorage.getMapRegion(regionId)
            is UMapLengthRegionId<*, *> -> regionStorage.getMapLengthRegion(regionId)
            is USetRegionId<*, *, *> -> error("ConcreteMemory.getRegion: unexpected 'USetRegionId'")
            is URefSetRegionId<*> -> regionStorage.getSetRegion(regionId)
            is JcStaticFieldRegionId<*> -> regionStorage.getStaticFieldsRegion(regionId)
            is JcLambdaCallSiteRegionId -> regionStorage.getLambdaCallSiteRegion(regionId)
            else -> super.getRegion(regionId)
        } as UMemoryRegion<Key, Sort>
    }

    override fun <Key, Sort : USort> setRegion(
        regionId: UMemoryRegionId<Key, Sort>,
        newRegion: UMemoryRegion<Key, Sort>
    ) {
        when (regionId) {
            is UFieldsRegionId<*, *> -> check(newRegion is JcConcreteFieldRegion)
            is UArrayRegionId<*, *, *> -> check(newRegion is JcConcreteArrayRegion)
            is UArrayLengthsRegionId<*, *> -> check(newRegion is JcConcreteArrayLengthRegion)
            is UMapRegionId<*, *, *, *> -> error("ConcreteMemory.setRegion: unexpected 'UMapRegionId'")
            is URefMapRegionId<*, *> -> check(newRegion is JcConcreteRefMapRegion)
            is UMapLengthRegionId<*, *> -> check(newRegion is JcConcreteMapLengthRegion)
            is USetRegionId<*, *, *> -> error("ConcreteMemory.setRegion: unexpected 'USetRegionId'")
            is URefSetRegionId<*> -> check(newRegion is JcConcreteRefSetRegion)
            is JcStaticFieldRegionId<*> -> check(newRegion is JcConcreteStaticFieldsRegion)
            is JcLambdaCallSiteRegionId -> check(newRegion is JcConcreteCallSiteLambdaRegion)
            else -> {
                super.setRegion(regionId, newRegion)
            }
        }
    }

    //endregion

    private fun allocateObject(obj: Any, type: JcType): UConcreteHeapRef {
        val address = bindings.allocate(obj, type)!!
        return ctx.mkConcreteHeapRef(address)
    }

    override fun allocConcrete(type: JcType): UConcreteHeapRef {
        val address = bindings.allocateDefaultConcrete(type)
        if (address != null)
            return ctx.mkConcreteHeapRef(address)
        return super.allocConcrete(type)
    }

    override fun allocStatic(type: JcType): UConcreteHeapRef {
        val address = bindings.allocateDefaultStatic(type)
        if (address != null)
            return ctx.mkConcreteHeapRef(address)
        return super.allocStatic(type)
    }

    override fun tryAllocateConcrete(obj: Any, type: JcType): UConcreteHeapRef? {
        val address = bindings.allocate(obj, type)
        if (address != null)
            return ctx.mkConcreteHeapRef(address)
        return null
    }

    override fun forceAllocConcrete(type: JcType): UConcreteHeapRef {
        val address = bindings.allocateDefaultConcrete(type)
        if (address != null)
            return ctx.mkConcreteHeapRef(address)
        return super.allocConcrete(type)
    }

    override fun tryHeapRefToObject(heapRef: UConcreteHeapRef): Any? {
        val maybe = marshall.tryExprToFullyConcreteObj(heapRef, ctx.cp.objectType)
        check(!(maybe.isSome && maybe.getOrThrow() == null))
        maybe.onSome { return it }

        return null
    }

    override fun <Sort : USort> tryExprToInt(expr: UExpr<Sort>): Int? {
        val maybe = marshall.tryExprToFullyConcreteObj(expr, ctx.cp.int)
        check(!(maybe.isSome && maybe.getOrThrow() == null))
        maybe.onSome { return it as Int }

        return null
    }

    override fun tryObjectToExpr(obj: Any?, type: JcType): UExpr<USort> {
        return marshall.objToExpr(obj, type)
    }

    override fun clone(
        typeConstraints: UTypeConstraints<JcType>,
        thisOwnership: MutabilityOwnership,
        cloneOwnership: MutabilityOwnership
    ): UMemory<JcType, JcMethod> {
        check(!concretization)

        val clonedStack = stack.clone()
        val clonedMocks = mocks.clone()
        val clonedBindings = bindings.copy(typeConstraints)
        val clonedMemory = JcConcreteMemory(
            ctx,
            cloneOwnership,
            typeConstraints,
            clonedStack,
            clonedMocks,
            regions,
            executor,
            clonedBindings,
            regionStorage,
            marshall
        )
        val clonedMarshall = marshall.copy(clonedBindings, regionStorage)
        val clonedRegionStorage = regionStorage.copy(clonedBindings, clonedMemory, clonedMarshall, cloneOwnership)
        clonedMarshall.regionStorage = clonedRegionStorage
        clonedMemory.regionStorageVar = clonedRegionStorage
        clonedMemory.marshallVar = clonedMarshall

        this.ownership = thisOwnership
        val myRegionStorage = regionStorage.copy(bindings, this, marshall, thisOwnership)
        this.marshall.regionStorage = myRegionStorage
        regionStorageVar = myRegionStorage

        return clonedMemory
    }

    //region Concrete Invoke

    private fun shouldNotInvoke(method: JcMethod): Boolean {
        return forbiddenInvocations.contains(method.humanReadableSignature) ||
                method.enclosingClass.isSpringFilter(ctx) && (method.name == "doFilter" || method.name == "doFilterInternal") ||
                method.enclosingClass.isSpringFilterChain(ctx) && (method.name == "doFilter" || method.name == "doFilterInternal")
    }

    private fun methodIsInvokable(method: JcMethod): Boolean {
        return !(
                method.isConstructor && method.enclosingClass.isAbstract ||
                        method.enclosingClass.isEnum && method.isConstructor ||
                        // Case for method, which exists only in approximations
                        method is JcEnrichedVirtualMethod && !method.isClassInitializer && method.toJavaMethod == null ||
                        method.humanReadableSignature.let {
                            it.startsWith("org.usvm.api.") ||
                                    it.startsWith("runtime.LibSLRuntime") ||
                                    it.startsWith("generated.") ||
                                    it.startsWith("stub.")
                        } ||
                        shouldNotInvoke(method)
                )
    }

    private val forcedInvokeMethods = setOf(
        // TODO: think about this! delete? #CM
        "java.lang.System#<clinit>():void",
    )

    private fun forceMethodInvoke(method: JcMethod): Boolean {
        return forcedInvokeMethods.contains(method.humanReadableSignature) || method.isExceptionCtor
    }

    private fun shouldAnalyzeClinit(method: JcMethod): Boolean {
        check(method.isClassInitializer)
        // TODO: add recursive static fields check: if static field of another class was read it should not be symbolic #CM
        return !forceMethodInvoke(method)
                // TODO: delete this, but create encoding for static fields (analyze clinit symbolically and write fields) #CM
                && method is JcEnrichedVirtualMethod && method.enclosingClass.toType().isStaticApproximation
    }

    private inner class JcConcretizer(
        state: JcState
    ) : JcTestStateResolver<Any?>(state.ctx, state.models.first(), state.memory, state.callStack.lastMethod().toTypedMethod) {
        override val decoderApi: JcTestInterpreterDecoderApi = JcTestInterpreterDecoderApi(ctx, JcConcreteMemoryClassLoader)

        override fun tryCreateObjectInstance(ref: UConcreteHeapRef, heapRef: UHeapRef): Any? {
            if (heapRef is UConcreteHeapRef) {
                check(heapRef == ref)
                return bindings.tryFullyConcrete(heapRef.address)
            }

            val addressInModel = ref.address
            if (bindings.contains(addressInModel))
                return bindings.tryFullyConcrete(addressInModel)

            return null
        }

        private fun resolveConcreteArray(ref: UConcreteHeapRef, type: JcArrayType): Any {
            val address = ref.address
            val obj = bindings.virtToPhys(address)
            saveResolvedRef(ref.address, obj)
            // TODO: optimize #CM
            if (bindings.state.isMutableWithEffect())
                bindings.effectStorage.addObjectToEffectRec(obj)
            val elementType = type.elementType
            val elementSort = ctx.typeToSort(type.elementType)
            val arrayDescriptor = ctx.arrayDescriptorOf(type)
            for (kind in bindings.symbolicMembers(address)) {
                check(kind is ArrayIndexChildKind)
                val index = kind.index
                val value = readArrayIndex(ref, ctx.mkSizeExpr(index), arrayDescriptor, elementSort)
                val resolved = resolveExpr(value, elementType)
                obj.setArrayValue(index, resolved)
            }

            return obj
        }

        override fun resolveArray(ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcArrayType): Any? {
            val addressInModel = ref.address
            if (heapRef is UConcreteHeapRef && bindings.contains(heapRef.address)) {
                val array = resolveConcreteArray(heapRef, type)
                saveResolvedRef(addressInModel, array)
                return array
            }

            val array = super.resolveArray(ref, heapRef, type)
            if (array != null && !bindings.contains(addressInModel)) {
                bindings.allocate(addressInModel, array, type)
            }

            return array
        }

        private fun resolveConcreteObject(ref: UConcreteHeapRef, type: JcClassType): Any {
            val address = ref.address
            val obj = bindings.virtToPhys(address)
            saveResolvedRef(ref.address, obj)
            // TODO: optimize #CM
            if (bindings.state.isMutableWithEffect())
                bindings.effectStorage.addObjectToEffectRec(obj)
            for (kind in bindings.symbolicMembers(address)) {
                check(kind is FieldChildKind)
                val field = kind.field
                val jcField = type.findFieldOrNull(field.name)
                    ?: error("resolveConcreteObject: can not find field $field")
                val fieldType = jcField.type
                val fieldSort = ctx.typeToSort(fieldType)
                val value = readField(ref, jcField.field, fieldSort)
                val resolved = resolveExpr(value, fieldType)
                field.setFieldValue(obj, resolved)
            }

            return obj
        }

        override fun resolveObject(ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcClassType): Any? {
            val addressInModel = ref.address
            if (heapRef is UConcreteHeapRef && bindings.contains(heapRef.address)) {
                val obj = resolveConcreteObject(heapRef, type)
                saveResolvedRef(addressInModel, obj)
                return obj
            }

            val obj = super.resolveObject(ref, heapRef, type)
            if (obj != null && !bindings.contains(addressInModel)) {
                bindings.allocate(addressInModel, obj, type)
            }

            return obj
        }

        override fun allocateClassInstance(type: JcClassType): Any =
            type.allocateInstance(JcConcreteMemoryClassLoader)

        override fun allocateString(value: Any?): Any = when (value) {
            is CharArray -> String(value)
            is ByteArray -> String(value)
            else -> String()
        }
    }

    private fun shouldNotConcretizeField(field: JcField): Boolean {
        return field.enclosingClass.name.startsWith("stub.")
    }

    private fun concretizeStatics(jcConcretizer: JcConcretizer) {
        println(ansiGreen + "Concretizing statics" + ansiReset)
        val statics = regionStorage.allMutatedStaticFields()
        // TODO: redo #CM
        statics.forEach { (field, value) ->
            if (!shouldNotConcretizeField(field)) {
                val javaField = field.toJavaField
                if (javaField != null) {
                    val typedField = field.typedField
                    val concretizedValue = jcConcretizer.withMode(ResolveMode.CURRENT) {
                        resolveExpr(value, typedField.type)
                    }
                    // TODO: need to call clinit? #CM
                    ensureClinit(field.enclosingClass)
                    val currentValue = javaField.getStaticFieldValue()
                    if (concretizedValue != currentValue)
                        javaField.setStaticFieldValue(concretizedValue)
                }
            }
        }
    }

    private fun concretize(
        state: JcState,
        exprResolver: JcExprResolver,
        stmt: JcMethodCall,
        method: JcMethod,
    ) {
        if (!concretization) {
            // Getting better model (via soft constraints)
            state.applySoftConstraints()
        }

        val concretizer = JcConcretizer(state)

        if (bindings.state.isMutableWithEffect())
            bindings.effectStorage.ensureStatics()

        if (!concretization) {
            concretizeStatics(concretizer)
            concretization = true
        }

        val parameterInfos = method.parameters
        var parameters = stmt.arguments
        val isStatic = method.isStatic
        var thisObj: Any? = null
        val objParameters = mutableListOf<Any?>()

        if (!isStatic) {
            val thisType = method.enclosingClass.toType()
            thisObj = concretizer.withMode(ResolveMode.CURRENT) {
                resolveReference(parameters[0].asExpr(ctx.addressSort), thisType)
            }
            parameters = parameters.drop(1)
        }

        check(parameterInfos.size == parameters.size)
        for (i in parameterInfos.indices) {
            val info = parameterInfos[i]
            val value = parameters[i]
            val type = ctx.cp.findTypeOrNull(info.type)!!
            val elem = concretizer.withMode(ResolveMode.CURRENT) {
                resolveExpr(value, type)
            }
            objParameters.add(elem)
        }

        check(objParameters.size == parameters.size)
        println(ansiGreen + "Concretizing ${method.humanReadableSignature}" + ansiReset)
        invoke(state, exprResolver, stmt, method, thisObj, objParameters)
    }

    private fun ensureClinit(type: JcClassOrInterface) {
        executor.execute {
            try {
                // Loading type and executing its class initializer
                JcConcreteMemoryClassLoader.loadClass(type)
            } catch (e: Throwable) {
                error("clinit should not throw exceptions")
            }
        }
    }

    private fun unfoldException(e: Throwable): Throwable {
        return when {
            e is ExecutionException && e.cause != null -> unfoldException(e.cause!!)
            e is InvocationTargetException -> e.targetException
            else -> e
        }
    }

    private fun invoke(
        state: JcState,
        exprResolver: JcExprResolver,
        stmt: JcMethodCall,
        method: JcMethod,
        thisObj: Any?,
        objParameters: List<Any?>
    ) {
        if (bindings.state.isMutableWithEffect()) {
            // TODO: if method is not mutating (guess via IFDS), backtrack is useless #CM
            bindings.effectStorage.addObjectToEffectRec(thisObj)
            for (arg in objParameters)
                bindings.effectStorage.addObjectToEffectRec(arg)
        }

        var thisArg = thisObj
        try {
            var resultObj: Any? = null
            var exception: Throwable? = null
            executor.execute {
                try {
                    resultObj = method.invoke(JcConcreteMemoryClassLoader, thisObj, objParameters)
                } catch (e: Throwable) {
                    exception = unfoldException(e)
                }
            }

            if (exception == null) {
                // No exception
                println("Result $resultObj")
                if (method.isConstructor) {
                    check(thisObj != null && resultObj != null)
                    // TODO: think about this:
                    //  A <: B
                    //  A.ctor is called symbolically, but B.ctor called concretelly #CM
                    check(thisObj.javaClass == resultObj!!.javaClass)
                    val thisAddress = bindings.tryPhysToVirt(thisObj)
                    check(thisAddress != null)
                    thisArg = resultObj
                    val type = bindings.typeOf(thisAddress)
                    bindings.remove(thisAddress)
                    bindings.allocate(thisAddress, resultObj!!, type)
                }

                val returnType = ctx.cp.findTypeOrNull(method.returnType)!!
                val result: UExpr<USort> = marshall.objToExpr(resultObj, returnType)
                exprResolver.ensureExprCorrectness(result, returnType)
                state.newStmt(JcConcreteInvocationResult(result, stmt))
            } else {
                // Exception thrown
                val e = exception!!
                val jcType = ctx.jcTypeOf(e)
                println("Exception ${e.javaClass} with message ${e.message}")
                val exceptionObj = allocateObject(e, jcType)
                state.throwExceptionWithoutStackFrameDrop(exceptionObj, jcType)
            }
        } finally {
            // TODO: if method is not mutating (guess via IFDS), do not reTrack #CM
            objParameters.forEach {
                bindings.reTrackObject(it)
            }
            if (thisObj != null)
                bindings.reTrackObject(thisArg)
        }
    }

    private interface TryConcreteInvokeResult

    private class TryConcreteInvokeSuccess : TryConcreteInvokeResult

    private data class TryConcreteInvokeFail(val symbolicArguments: Boolean) : TryConcreteInvokeResult

    private fun RegisteredLocation.isProjectLocation(projectLocations: List<JcByteCodeLocation>): Boolean {
        return projectLocations.any { it == jcLocation }
    }

    private fun tryConcreteInvoke(
        stmt: JcMethodCall,
        state: JcState,
        exprResolver: JcExprResolver
    ): TryConcreteInvokeResult {
        val method = stmt.method
        val arguments = stmt.arguments
        if (!methodIsInvokable(method))
            return TryConcreteInvokeFail(false)

        val signature = method.humanReadableSignature

        if (method.isClassInitializer) {
            ensureClinit(method.enclosingClass)

            if (shouldAnalyzeClinit(method)) {
                // Executing clinit symbolically
                return TryConcreteInvokeFail(false)
            }

            state.skipMethodInvocationWithValue(stmt, ctx.voidValue)
            return TryConcreteInvokeSuccess()
        }

        if (concretization || concretizeInvocations.contains(signature)) {
            concretize(state, exprResolver, stmt, method)
            return TryConcreteInvokeSuccess()
        }

        val methodLocation = method.declaration.location
        val projectLocations = exprResolver.options.projectLocations
        val isProjectLocation = projectLocations == null || methodLocation.isProjectLocation(projectLocations)
        if (isProjectLocation)
            return TryConcreteInvokeFail(false)

        val parameterInfos = method.parameters
        val isStatic = method.isStatic
        var thisObj: Any? = null
        var parameters = arguments

        if (!isStatic) {
            val thisType = method.enclosingClass.toType()
            marshall.tryExprToFullyConcreteObj(arguments[0], thisType)
                .onNone { return TryConcreteInvokeFail(true) }
                .onSome { thisObj = it }

            // TODO: support this case:
            //  A <: B
            //  A.ctor is called symbolically, but B.ctor called concretelly #CM
            if (method.isConstructor && thisObj?.javaClass?.name != thisType.name)
                return TryConcreteInvokeFail(false)

            parameters = arguments.drop(1)
        }

        val objParameters = mutableListOf<Any?>()
        check(parameterInfos.size == parameters.size)
        for (i in parameterInfos.indices) {
            val info = parameterInfos[i]
            val value = parameters[i]
            val type = ctx.cp.findTypeOrNull(info.type)!!
            marshall.tryExprToFullyConcreteObj(value, type)
                .onNone { return TryConcreteInvokeFail(true) }
                .onSome { objParameters.add(it) }
        }

        check(objParameters.size == parameters.size)
        if (bindings.state.isMutableWithEffect()) {
            bindings.effectStorage.ensureStatics()
            println(ansiGreen + "Invoking (B) $signature" + ansiReset)
        } else {
            println(ansiGreen + "Invoking $signature" + ansiReset)
        }

        invoke(state, exprResolver, stmt, method, thisObj, objParameters)

        return TryConcreteInvokeSuccess()
    }

    override fun <Inst, State, Resolver> tryConcreteInvoke(stmt: Inst, state: State, exprResolver: Resolver): Boolean {
        stmt as JcMethodCall
        state as JcState
        exprResolver as JcExprResolver
        val success = tryConcreteInvoke(stmt, state, exprResolver)
        // If constructor was not invoked and arguments were symbolic, deleting default 'this' from concrete memory:
        // + No need to encode objects in inconsistent state (created via allocConcrete -- objects with default fields)
        // - During symbolic execution, 'this' may stay concrete
        if (success is TryConcreteInvokeFail && success.symbolicArguments && stmt.method.isConstructor) {
            // TODO: only if arguments are symbolic? #CM
            val thisArg = stmt.arguments[0]
            if (thisArg is UConcreteHeapRef && bindings.contains(thisArg.address) && types.typeOf(thisArg.address).isInstanceApproximation)
                bindings.remove(thisArg.address)
        }

        return success is TryConcreteInvokeSuccess
    }

    fun kill() {
        bindings.state.makeDead()
    }

    fun reset() {
        bindings.effectStorage.reset()
    }

    //endregion

    companion object {

        operator fun invoke(
            ctx: JcContext,
            ownership: MutabilityOwnership,
            typeConstraints: UTypeConstraints<JcType>,
        ): JcConcreteMemory {
            val executor = JcConcreteExecutor()
            val bindings = JcConcreteMemoryBindings(ctx, typeConstraints, executor)
            val stack = URegistersStack()
            val mocks = UIndexedMocker<JcMethod>()
            val regions: UPersistentHashMap<UMemoryRegionId<*, *>, UMemoryRegion<*, *>> = persistentHashMapOf()
            val memory = JcConcreteMemory(ctx, ownership, typeConstraints, stack, mocks, regions, executor, bindings)
            val storage = JcConcreteRegionStorage(ctx, memory)
            val marshall = Marshall(ctx, bindings, storage)
            memory.regionStorageVar = storage
            memory.marshallVar = marshall
            return memory
        }

        //region Concrete Invocations

        private val forbiddenInvocations = setOf(
            "org.springframework.test.context.TestContextManager#<init>(java.lang.Class):void",
            "org.springframework.test.context.TestContextManager#<init>(org.springframework.test.context.TestContextBootstrapper):void",
            "org.springframework.boot.test.context.SpringBootTestContextBootstrapper#buildTestContext():org.springframework.test.context.TestContext",
            "org.springframework.test.context.support.AbstractTestContextBootstrapper#buildTestContext():org.springframework.test.context.TestContext",
            "org.springframework.test.context.support.AbstractTestContextBootstrapper#buildMergedContextConfiguration():org.springframework.test.context.MergedContextConfiguration",
            "org.springframework.test.context.support.AbstractTestContextBootstrapper#buildDefaultMergedContextConfiguration(java.lang.Class,org.springframework.test.context.CacheAwareContextLoaderDelegate):org.springframework.test.context.MergedContextConfiguration",
            "org.apache.commons.logging.Log#isFatalEnabled():boolean",
            "org.apache.commons.logging.Log#isErrorEnabled():boolean",
            "org.apache.commons.logging.Log#isWarnEnabled():boolean",
            "org.apache.commons.logging.Log#isInfoEnabled():boolean",
            "org.apache.commons.logging.Log#isDebugEnabled():boolean",
            "org.apache.commons.logging.Log#isTraceEnabled():boolean",
            "org.springframework.test.context.support.AbstractTestContextBootstrapper#buildMergedContextConfiguration(java.lang.Class,java.util.List,org.springframework.test.context.MergedContextConfiguration,org.springframework.test.context.CacheAwareContextLoaderDelegate,boolean):org.springframework.test.context.MergedContextConfiguration",
            "org.springframework.test.context.MergedContextConfiguration#<init>(java.lang.Class,java.lang.String[],java.lang.Class[],java.util.Set,java.lang.String[],java.util.List,java.lang.String[],java.util.Set,org.springframework.test.context.ContextLoader,org.springframework.test.context.CacheAwareContextLoaderDelegate,org.springframework.test.context.MergedContextConfiguration):void",
            "org.springframework.test.context.MergedContextConfiguration#processContextCustomizers(java.util.Set):java.util.Set",
            "org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTestContextBootstrapper#processMergedContextConfiguration(org.springframework.test.context.MergedContextConfiguration):org.springframework.test.context.MergedContextConfiguration",
            "org.springframework.boot.test.context.SpringBootTestContextBootstrapper#processMergedContextConfiguration(org.springframework.test.context.MergedContextConfiguration):org.springframework.test.context.MergedContextConfiguration",
            "org.springframework.boot.test.context.SpringBootTestContextBootstrapper#getOrFindConfigurationClasses(org.springframework.test.context.MergedContextConfiguration):java.lang.Class[]",
            "org.springframework.boot.test.context.SpringBootTestContextBootstrapper#findConfigurationClass(java.lang.Class):java.lang.Class",
            "org.springframework.boot.test.context.AnnotatedClassFinder#findFromClass(java.lang.Class):java.lang.Class",
            "org.springframework.util.ClassUtils#getPackageName(java.lang.Class):java.lang.String",
            "org.springframework.util.ClassUtils#getMainPackageName():java.lang.String",
            "java.lang.invoke.StringConcatFactory#makeConcatWithConstants(java.lang.invoke.MethodHandles\$Lookup,java.lang.String,java.lang.invoke.MethodType,java.lang.String,java.lang.Object[]):java.lang.invoke.CallSite",
            "org.apache.commons.logging.Log#info(java.lang.Object):void",
            "org.springframework.test.context.TestContextManager#getTestContext():org.springframework.test.context.TestContext",
            "org.springframework.test.context.support.DefaultTestContext#getApplicationContext():org.springframework.context.ApplicationContext",
            "org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate#loadContext(org.springframework.test.context.MergedContextConfiguration):org.springframework.context.ApplicationContext",
            "org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate#loadContextInternal(org.springframework.test.context.MergedContextConfiguration):org.springframework.context.ApplicationContext",
            "org.springframework.boot.test.context.SpringBootContextLoader#loadContext(org.springframework.test.context.MergedContextConfiguration):org.springframework.context.ApplicationContext",
            "org.springframework.boot.test.context.SpringBootContextLoader#loadContext(org.springframework.test.context.MergedContextConfiguration,org.springframework.boot.test.context.SpringBootContextLoader\$Mode,org.springframework.context.ApplicationContextInitializer):org.springframework.context.ApplicationContext",
            "org.springframework.boot.test.context.SpringBootContextLoader\$ContextLoaderHook#<init>(org.springframework.boot.test.context.SpringBootContextLoader\$Mode,org.springframework.context.ApplicationContextInitializer,java.util.function.Consumer):void",
            "org.springframework.boot.test.context.SpringBootContextLoader\$ContextLoaderHook#run(org.springframework.util.function.ThrowingSupplier):org.springframework.context.ApplicationContext",
            "org.springframework.boot.test.context.SpringBootContextLoader#getSpringApplication():org.springframework.boot.SpringApplication",
            "org.springframework.boot.SpringApplication#withHook(org.springframework.boot.SpringApplicationHook,org.springframework.util.function.ThrowingSupplier):java.lang.Object",
            "org.springframework.boot.test.context.SpringBootContextLoader#lambda\$loadContext\$3(org.springframework.boot.SpringApplication,java.lang.String[]):org.springframework.context.ConfigurableApplicationContext",
            "org.springframework.boot.SpringApplication#run(java.lang.String[]):org.springframework.context.ConfigurableApplicationContext",
            "org.springframework.boot.SpringApplication#printBanner(org.springframework.core.env.ConfigurableEnvironment):org.springframework.boot.Banner",
            "org.springframework.boot.SpringApplication#afterRefresh(org.springframework.context.ConfigurableApplicationContext,org.springframework.boot.ApplicationArguments):void",
            "org.springframework.test.web.servlet.MockMvc#perform(org.springframework.test.web.servlet.RequestBuilder):org.springframework.test.web.servlet.ResultActions",
            "org.springframework.mock.web.MockFilterChain#doFilter(jakarta.servlet.ServletRequest,jakarta.servlet.ServletResponse):void",
            "org.springframework.web.filter.RequestContextFilter#doFilterInternal(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,jakarta.servlet.FilterChain):void",
            "org.springframework.web.filter.FormContentFilter#doFilterInternal(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,jakarta.servlet.FilterChain):void",
            "org.springframework.web.filter.CharacterEncodingFilter#doFilterInternal(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,jakarta.servlet.FilterChain):void",
            "org.springframework.mock.web.MockFilterChain\$ServletFilterProxy#doFilter(jakarta.servlet.ServletRequest,jakarta.servlet.ServletResponse,jakarta.servlet.FilterChain):void",
            "jakarta.servlet.http.HttpServlet#service(jakarta.servlet.ServletRequest,jakarta.servlet.ServletResponse):void",
            "org.springframework.test.web.servlet.TestDispatcherServlet#service(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.FrameworkServlet#service(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "jakarta.servlet.http.HttpServlet#service(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",

            "org.springframework.web.servlet.FrameworkServlet#doGet(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.FrameworkServlet#doPost(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.FrameworkServlet#doPut(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.FrameworkServlet#doDelete(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.FrameworkServlet#doOptions(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.FrameworkServlet#doTrace(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",

            "org.springframework.web.servlet.FrameworkServlet#processRequest(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.DispatcherServlet#doService(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.DispatcherServlet#doDispatch(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter#handle(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,java.lang.Object):org.springframework.web.servlet.ModelAndView",
            "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter#handleInternal(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,org.springframework.web.method.HandlerMethod):org.springframework.web.servlet.ModelAndView",
            "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter#invokeHandlerMethod(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,org.springframework.web.method.HandlerMethod):org.springframework.web.servlet.ModelAndView",
            "org.springframework.web.method.annotation.ModelFactory#initModel(org.springframework.web.context.request.NativeWebRequest,org.springframework.web.method.support.ModelAndViewContainer,org.springframework.web.method.HandlerMethod):void",
            "org.springframework.web.method.annotation.ModelFactory#invokeModelAttributeMethods(org.springframework.web.context.request.NativeWebRequest,org.springframework.web.method.support.ModelAndViewContainer):void",
            "org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod#invokeAndHandle(org.springframework.web.context.request.ServletWebRequest,org.springframework.web.method.support.ModelAndViewContainer,java.lang.Object[]):void",
            "org.springframework.web.method.support.InvocableHandlerMethod#invokeForRequest(org.springframework.web.context.request.NativeWebRequest,org.springframework.web.method.support.ModelAndViewContainer,java.lang.Object[]):java.lang.Object",
            "org.springframework.web.method.support.InvocableHandlerMethod#getMethodArgumentValues(org.springframework.web.context.request.NativeWebRequest,org.springframework.web.method.support.ModelAndViewContainer,java.lang.Object[]):java.lang.Object[]",
            "org.springframework.web.method.support.HandlerMethodArgumentResolverComposite#resolveArgument(org.springframework.core.MethodParameter,org.springframework.web.method.support.ModelAndViewContainer,org.springframework.web.context.request.NativeWebRequest,org.springframework.web.bind.support.WebDataBinderFactory):java.lang.Object",
            "org.springframework.web.servlet.mvc.method.annotation.PathVariableMethodArgumentResolver#resolveArgument(org.springframework.core.MethodParameter,org.springframework.web.method.support.ModelAndViewContainer,org.springframework.web.context.request.NativeWebRequest,org.springframework.web.bind.support.WebDataBinderFactory):java.lang.Object",
            "org.springframework.web.method.annotation.RequestParamMethodArgumentResolver#resolveArgument(org.springframework.core.MethodParameter,org.springframework.web.method.support.ModelAndViewContainer,org.springframework.web.context.request.NativeWebRequest,org.springframework.web.bind.support.WebDataBinderFactory):java.lang.Object",
            "org.springframework.web.method.annotation.ModelAttributeMethodProcessor#resolveArgument(org.springframework.core.MethodParameter,org.springframework.web.method.support.ModelAndViewContainer,org.springframework.web.context.request.NativeWebRequest,org.springframework.web.bind.support.WebDataBinderFactory):java.lang.Object",
            "org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor#resolveArgument(org.springframework.core.MethodParameter,org.springframework.web.method.support.ModelAndViewContainer,org.springframework.web.context.request.NativeWebRequest,org.springframework.web.bind.support.WebDataBinderFactory):java.lang.Object",
            "org.springframework.web.method.support.InvocableHandlerMethod#doInvoke(java.lang.Object[]):java.lang.Object",
            "java.lang.reflect.Method#invoke(java.lang.Object,java.lang.Object[]):java.lang.Object",

            "org.springframework.web.servlet.mvc.method.annotation.ServletModelAttributeMethodProcessor#bindRequestParameters(org.springframework.web.bind.WebDataBinder,org.springframework.web.context.request.NativeWebRequest):void",
            "org.springframework.web.bind.ServletRequestDataBinder#bind(jakarta.servlet.ServletRequest):void",
            "org.springframework.web.bind.WebDataBinder#doBind(org.springframework.beans.MutablePropertyValues):void",
            "org.springframework.validation.DataBinder#doBind(org.springframework.beans.MutablePropertyValues):void",
            "org.springframework.validation.AbstractBindingResult#getModel():java.util.Map",

            "java.lang.Object#<init>():void",
            "org.springframework.util.function.ThrowingSupplier#get():java.lang.Object",
            "org.springframework.util.function.ThrowingSupplier#get(java.util.function.BiFunction):java.lang.Object",

            "org.springframework.web.servlet.handler.HandlerMappingIntrospector#lambda\$createCacheFilter\$3(jakarta.servlet.ServletRequest,jakarta.servlet.ServletResponse,jakarta.servlet.FilterChain):void",
            "org.springframework.security.web.FilterChainProxy#doFilterInternal(jakarta.servlet.ServletRequest,jakarta.servlet.ServletResponse,jakarta.servlet.FilterChain):void",
            "org.springframework.security.web.session.DisableEncodeUrlFilter#doFilterInternal(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,jakarta.servlet.FilterChain):void",
            "org.springframework.security.web.header.HeaderWriterFilter#doHeadersAfter(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,jakarta.servlet.FilterChain):void",
        )

        private val concretizeInvocations = setOf(
            "org.springframework.web.servlet.DispatcherServlet#processDispatchResult(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,org.springframework.web.servlet.HandlerExecutionChain,org.springframework.web.servlet.ModelAndView,java.lang.Exception):void",
        )

        //endregion

        //region Invariants check

        init {
            check(concretizeInvocations.intersect(forbiddenInvocations).isEmpty())
        }

        //endregion
    }
}

//endregion
