package machine.concreteMemory

import io.ksmt.utils.asExpr
import machine.JcConcreteInvocationResult
import machine.JcConcreteMachineOptions
import machine.JcConcreteMemoryClassLoader
import machine.concreteMemory.concreteMemoryRegions.JcConcreteArrayLengthRegion
import machine.concreteMemory.concreteMemoryRegions.JcConcreteArrayRegion
import machine.concreteMemory.concreteMemoryRegions.JcConcreteCallSiteLambdaRegion
import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.api.jvm.ext.findTypeOrNull
import org.jacodb.api.jvm.ext.humanReadableSignature
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.isEnum
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.approximation.JcEnrichedVirtualMethod
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UIndexedMocker
import org.usvm.USort
import org.usvm.api.util.JcTestStateResolver.ResolveMode
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
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.constraints.UTypeConstraints
import org.usvm.machine.JcContext
import org.usvm.machine.JcMethodCall
import org.usvm.machine.USizeSort
import org.usvm.machine.interpreter.JcExprResolver
import org.usvm.machine.interpreter.JcLambdaCallSiteMemoryRegion
import org.usvm.machine.interpreter.JcLambdaCallSiteRegionId
import org.usvm.machine.interpreter.statics.JcStaticFieldRegionId
import org.usvm.machine.interpreter.statics.JcStaticFieldsMemoryRegion
import org.usvm.machine.state.JcState
import machine.concreteMemory.concreteMemoryRegions.JcConcreteFieldRegion
import machine.concreteMemory.concreteMemoryRegions.JcConcreteMapLengthRegion
import machine.concreteMemory.concreteMemoryRegions.JcConcreteRefMapRegion
import machine.concreteMemory.concreteMemoryRegions.JcConcreteRefSetRegion
import machine.concreteMemory.concreteMemoryRegions.JcConcreteRegion
import machine.concreteMemory.concreteMemoryRegions.JcConcreteStaticFieldsRegion
import org.usvm.api.util.JcTestStateResolver
import org.usvm.concrete.api.internal.InitHelper
import org.usvm.jvm.util.toJavaClass
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.skipMethodInvocationWithValue
import org.usvm.machine.state.throwExceptionWithoutStackFrameDrop
import org.usvm.memory.UMemory
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.URegistersStack
import org.usvm.util.name
import org.usvm.util.onNone
import org.usvm.util.onSome
import org.usvm.utils.applySoftConstraints
import utils.getStaticFieldValue
import utils.isExceptionCtor
import utils.isInstanceApproximation
import utils.isInternalType
import utils.isStaticApproximation
import utils.jcTypeOf
import utils.setStaticFieldValue
import utils.toJavaField
import utils.toJavaMethod
import utils.typedField
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException

//region Concrete Memory

open class JcConcreteMemory(
    private val ctx: JcContext,
    ownership: MutabilityOwnership,
    typeConstraints: UTypeConstraints<JcType>,
) : UMemory<JcType, JcMethod>(
    ctx,
    ownership,
    typeConstraints,
    URegistersStack(),
    UIndexedMocker(),
    persistentHashMapOf()
), JcConcreteRegionGetter {

    internal val executor: JcConcreteExecutor = JcConcreteExecutor()
    private var bindings: JcConcreteMemoryBindings = JcConcreteMemoryBindings(ctx, typeConstraints, executor)
    internal var regionStorage: JcConcreteRegionStorage
    private var marshall: Marshall

    init {
        val storage = JcConcreteRegionStorage(ctx, this)
        val marshall = Marshall(ctx, bindings, executor, storage)
        this.regionStorage = storage
        this.marshall = marshall
    }

    private val ansiReset: String = "\u001B[0m"
    private val ansiBlack: String = "\u001B[30m"
    private val ansiRed: String = "\u001B[31m"
    private val ansiGreen: String = "\u001B[32m"
    private val ansiYellow: String = "\u001B[33m"
    private val ansiBlue: String = "\u001B[34m"
    private val ansiWhite: String = "\u001B[37m"
    private val ansiPurple: String = "\u001B[35m"
    private val ansiCyan: String = "\u001B[36m"

    private var concretization = false

    //region 'JcConcreteRegionGetter' implementation

    @Suppress("UNCHECKED_CAST")
    override fun <Key, Sort : USort> getConcreteRegion(regionId: UMemoryRegionId<Key, Sort>): JcConcreteRegion {
        val baseRegion = super.getRegion(regionId)
        return when (regionId) {
            is UFieldsRegionId<*, *> -> {
                baseRegion as UFieldsRegion<JcField, Sort>
                regionId as UFieldsRegionId<JcField, Sort>
                JcConcreteFieldRegion(
                    regionId,
                    ctx,
                    bindings,
                    baseRegion,
                    marshall,
                    ownership
                )
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

    fun tryAllocateConcrete(obj: Any, type: JcType): UConcreteHeapRef? {
        val address = bindings.allocate(obj, type)
        if (address != null)
            return ctx.mkConcreteHeapRef(address)
        return null
    }

    fun tryHeapRefToObject(heapRef: UConcreteHeapRef): Any? {
        val maybe = marshall.tryExprToFullyConcreteObj(heapRef, ctx.cp.objectType)
        check(!(maybe.isSome && maybe.getOrThrow() == null))
        maybe.onSome { return it }

        return null
    }

    fun <Sort : USort> tryExprToInt(expr: UExpr<Sort>): Int? {
        val maybe = marshall.tryExprToFullyConcreteObj(expr, ctx.cp.int)
        check(!(maybe.isSome && maybe.getOrThrow() == null))
        maybe.onSome { return it as Int }

        return null
    }

    fun objectToExpr(obj: Any?, type: JcType): UExpr<USort> {
        return marshall.objToExpr(obj, type)
    }

    override fun clone(
        typeConstraints: UTypeConstraints<JcType>,
        thisOwnership: MutabilityOwnership,
        cloneOwnership: MutabilityOwnership
    ): UMemory<JcType, JcMethod> {
        check(!concretization)

        val clonedMemory = super.clone(typeConstraints, thisOwnership, cloneOwnership) as JcConcreteMemory

        val clonedBindings = bindings.copy(typeConstraints)
        val clonedMarshall = marshall.copy(clonedBindings, regionStorage)
        val clonedRegionStorage = regionStorage.copy(clonedBindings, clonedMemory, clonedMarshall, cloneOwnership)
        clonedMarshall.regionStorage = clonedRegionStorage

        clonedMemory.ownership = cloneOwnership
        clonedMemory.bindings = clonedBindings
        clonedMemory.regionStorage = clonedRegionStorage
        clonedMemory.marshall = clonedMarshall

        this.ownership = thisOwnership
        val myRegionStorage = regionStorage.copy(bindings, this, marshall, thisOwnership)
        this.marshall.regionStorage = myRegionStorage
        regionStorage = myRegionStorage

        return clonedMemory
    }

    fun getConcretizer(state: JcState): JcTestStateResolver<Any?> {
        return JcConcretizer(state, bindings)
    }

    //region Concrete Invoke

    protected open fun shouldNotInvoke(method: JcMethod): Boolean {
        return forbiddenInvocations.contains(method.humanReadableSignature)
    }

    private fun methodIsInvokable(method: JcMethod): Boolean {
        val enclosingClass = method.enclosingClass
        return !(
                method.isConstructor && enclosingClass.isAbstract ||
                        enclosingClass.isEnum && method.isConstructor ||
                        // Case for method, which exists only in approximations
                        method is JcEnrichedVirtualMethod && !method.isClassInitializer && method.toJavaMethod == null ||
                        enclosingClass.isInternalType && enclosingClass.name != InitHelper::class.java.typeName ||
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

    // TODO: move to bindings?


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
                    if (ensureClinit(field.enclosingClass)) {
                        val currentValue = javaField.getStaticFieldValue()
                        if (concretizedValue != currentValue)
                            javaField.setStaticFieldValue(concretizedValue)
                    }
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

        val concretizer = JcConcretizer(state, bindings)

        if (bindings.isMutableWithEffect())
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

    private fun ensureClinit(type: JcClassOrInterface): Boolean {
        if (type.isInternalType)
            return true

        var success = true
        executor.execute {
            try {
                type.toJavaClass(JcConcreteMemoryClassLoader)
            } catch (e: Throwable) {
                println("[WARNING] clinit should not throw exceptions: $e")
                success = false
            }
        }

        return success
    }

    private fun invoke(
        state: JcState,
        exprResolver: JcExprResolver,
        stmt: JcMethodCall,
        method: JcMethod,
        thisObj: Any?,
        objParameters: List<Any?>
    ) {
        if (bindings.isMutableWithEffect()) {
            // TODO: if method is not mutating (guess via IFDS), backtrack is useless #CM
            bindings.effectStorage.addObjectToEffectRec(thisObj)
            for (arg in objParameters)
                bindings.effectStorage.addObjectToEffectRec(arg)
        }

        val (resultObj, exception) = executor.executeWithResult {
            method.invoke(JcConcreteMemoryClassLoader, thisObj, objParameters)
        }

        if (exception == null) {
            // No exception
            try {
                println("Result $resultObj")
            } catch (e: Throwable) {
                println("unable to print method invocation result")
            }
            if (method.isConstructor) {
                check(thisObj != null && resultObj != null)
                // TODO: think about this:
                //  A <: B
                //  A.ctor is called symbolically, but B.ctor called concretelly #CM
                check(thisObj.javaClass == resultObj.javaClass)
                val thisAddress = bindings.tryPhysToVirt(thisObj)
                check(thisAddress != null)
                val type = bindings.typeOf(thisAddress)
                bindings.remove(thisAddress, isSymbolic = false)
                bindings.allocate(thisAddress, resultObj, type)
            }

            val returnType = ctx.cp.findTypeOrNull(method.returnType)!!
            val result: UExpr<USort> = marshall.objToExpr(resultObj, returnType)
            exprResolver.ensureExprCorrectness(result, returnType)
            state.newStmt(JcConcreteInvocationResult(result, stmt))
        } else {
            // Exception thrown
            val jcType = ctx.cp.jcTypeOf(exception)!!
            println("Exception ${exception.javaClass} with message ${exception.message}")
            val exceptionObj = allocateObject(exception, jcType)
            state.throwExceptionWithoutStackFrameDrop(exceptionObj, jcType)
        }
    }

    private interface TryConcreteInvokeResult

    private class TryConcreteInvokeSuccess : TryConcreteInvokeResult

    private data class TryConcreteInvokeFail(val symbolicArguments: Boolean) : TryConcreteInvokeResult

    protected open fun shouldConcretizeMethod(method: JcMethod): Boolean {
        // TODO: for tests only (remove after testing) #CM
        return method.humanReadableSignature == "org.usvm.samples.Encoding#concretize():void"
    }

    private fun tryConcreteInvokeInternal(
        stmt: JcMethodCall,
        state: JcState,
        exprResolver: JcExprResolver,
        jcConcreteMachineOptions: JcConcreteMachineOptions
    ): TryConcreteInvokeResult {
        val method = stmt.method
        val arguments = stmt.arguments
        if (!methodIsInvokable(method))
            return TryConcreteInvokeFail(false)

        val signature = method.humanReadableSignature

        if (method.isClassInitializer) {
            val success = ensureClinit(method.enclosingClass)

            if (shouldAnalyzeClinit(method) || !success) {
                // Executing clinit symbolically
                return TryConcreteInvokeFail(false)
            }

            state.skipMethodInvocationWithValue(stmt, ctx.voidValue)
            return TryConcreteInvokeSuccess()
        }

        if (concretization || shouldConcretizeMethod(method)) {
            concretize(state, exprResolver, stmt, method)
            return TryConcreteInvokeSuccess()
        }

        // TODO: change on checking coverage zone #CM
        if (jcConcreteMachineOptions.isProjectLocation(method))
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
            if (method.isConstructor && thisObj?.javaClass?.typeName != thisType.name)
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
        if (bindings.isMutableWithEffect()) {
            bindings.effectStorage.ensureStatics()
            println(ansiGreen + "Invoking (B) $signature" + ansiReset)
        } else {
            println(ansiGreen + "Invoking $signature" + ansiReset)
        }

        invoke(state, exprResolver, stmt, method, thisObj, objParameters)

        return TryConcreteInvokeSuccess()
    }

    fun tryConcreteInvoke(
        methodCall: JcMethodCall,
        state: JcState,
        exprResolver: JcExprResolver,
        jcConcreteMachineOptions: JcConcreteMachineOptions
    ): Boolean {
        val success = tryConcreteInvokeInternal(methodCall, state, exprResolver, jcConcreteMachineOptions)
        // If constructor was not invoked and arguments were symbolic, deleting default 'this' from concrete memory:
        // + No need to encode objects in inconsistent state (created via allocConcrete -- objects with default fields)
        // - During symbolic execution, 'this' may stay concrete
        if (success is TryConcreteInvokeFail && success.symbolicArguments && methodCall.method.isConstructor) {
            // TODO: only if arguments are symbolic? #CM
            val thisArg = methodCall.arguments[0]
            if (thisArg is UConcreteHeapRef && bindings.contains(thisArg.address) && types.typeOf(thisArg.address).isInstanceApproximation)
                bindings.remove(thisArg.address, isNew = true)
        }

        return success is TryConcreteInvokeSuccess
    }

    fun kill() {
        bindings.kill()
    }

    fun reset() {
        bindings.reset()
    }

    //endregion

    companion object {

        //region Concrete Invocations

        private val forbiddenInvocations = setOf(
            "org.apache.commons.logging.Log#isFatalEnabled():boolean",
            "org.apache.commons.logging.Log#isErrorEnabled():boolean",
            "org.apache.commons.logging.Log#isWarnEnabled():boolean",
            "org.apache.commons.logging.Log#isInfoEnabled():boolean",
            "org.apache.commons.logging.Log#isDebugEnabled():boolean",
            "org.apache.commons.logging.Log#isTraceEnabled():boolean",
            "org.apache.commons.logging.Log#info(java.lang.Object):void",

            "java.lang.invoke.StringConcatFactory#makeConcatWithConstants(java.lang.invoke.MethodHandles\$Lookup,java.lang.String,java.lang.invoke.MethodType,java.lang.String,java.lang.Object[]):java.lang.invoke.CallSite",

            "java.lang.reflect.Method#invoke(java.lang.Object,java.lang.Object[]):java.lang.Object",

            "java.lang.Object#<init>():void",
        )

        //endregion
    }
}

//endregion
