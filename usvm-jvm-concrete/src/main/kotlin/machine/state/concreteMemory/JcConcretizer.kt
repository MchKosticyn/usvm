package machine.state.concreteMemory

import machine.JcConcreteMemoryClassLoader
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcType
import org.jacodb.approximation.JcEnrichedVirtualField
import org.jacodb.approximation.JcEnrichedVirtualMethod
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UNullRef
import org.usvm.USort
import org.usvm.api.readArrayIndex
import org.usvm.api.readField
import org.usvm.api.util.JcTestInterpreterDecoderApi
import org.usvm.api.util.JcTestStateResolver
import org.usvm.collection.field.UFieldLValue
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.jvm.util.toTypedMethod
import org.usvm.machine.interpreter.JcLambdaCallSiteRegionId
import org.usvm.machine.state.JcState
import org.usvm.mkSizeExpr
import org.usvm.jvm.util.allInstanceFields
import org.usvm.model.UModelBase
import utils.JcConcreteTestStateResolver
import utils.LambdaInvocationHandler
import utils.approximationMethod
import utils.createDefault
import utils.isProxy
import utils.isThreadLocal
import utils.setArrayValue
import utils.setFieldValue
import utils.toJavaField
import java.lang.reflect.Proxy

internal class JcConcretizer(
    state: JcState,
    model: UModelBase<JcType>,
    private val bindings: JcConcreteMemoryBindings,
    private val concretizingConstraints: MutableList<UBoolExpr>
) : JcConcreteTestStateResolver<Any?>(state.ctx, model, state.memory, state.entrypoint.toTypedMethod) {
    override val decoderApi: JcTestInterpreterDecoderApi = JcTestInterpreterDecoderApi(ctx, JcConcreteMemoryClassLoader)

    override var resolveMode: ResolveMode = ResolveMode.CURRENT

    override fun <R> withMode(resolveMode: ResolveMode, body: JcTestStateResolver<Any?>.() -> R): R {
        check(resolveMode == ResolveMode.CURRENT && this.resolveMode == ResolveMode.CURRENT)
        return body()
    }

    private fun <T : USort> addConstraints(expr: UExpr<T>, evaled: UExpr<T>) {
        if (expr is UConcreteHeapRef || expr is UNullRef)
            return

        val constraint = ctx.mkEq(evaled, expr)
        check(!constraint.isFalse) { "JcConcretizer.addConstraints: false constraint" }
        if (!constraint.isTrue)
            concretizingConstraints.add(constraint)
    }

    override fun <T : USort> evaluateInModel(expr: UExpr<T>): UExpr<T> {
        val evaled = super.evaluateInModel(expr)
        addConstraints(expr, evaled)
        return evaled
    }

    override fun tryCreateObjectInstance(heapRef: UHeapRef): Any? {
        if (heapRef is UConcreteHeapRef)
            return bindings.tryFullyConcrete(heapRef.address)

        return null
    }

    private fun resolveConcreteArray(ref: UConcreteHeapRef, type: JcArrayType): Any {
        val address = ref.address
        val array = bindings.virtToPhys(address) as Array<*>
        saveResolvedRef(ref.address, array)
        // TODO: optimize #CM
        if (bindings.isMutableWithEffect())
            bindings.effectStorage.addObjectToEffectRec(array)
        val elementType = type.elementType
        val elementSort = ctx.typeToSort(type.elementType)
        val arrayDescriptor = ctx.arrayDescriptorOf(type)
        for (index in bindings.symbolicIndices(array)) {
            val value = concreteMemory.readArrayIndex(ref, ctx.mkSizeExpr(index), arrayDescriptor, elementSort)
            val resolved = resolveExpr(value, elementType)
            array.setArrayValue(index, resolved)
        }

        return array
    }

    override fun resolveArray(ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcArrayType): Any? {
        val addressInModel = ref.address
        if (heapRef is UConcreteHeapRef && bindings.contains(heapRef.address)) {
            val array = resolveConcreteArray(heapRef, type)
            saveResolvedRef(addressInModel, array)
            return array
        }

        return super.resolveArray(ref, heapRef, type)
    }

    private fun initLambdaIfNeeded(heapRef: UConcreteHeapRef, obj: Any) {
        val lambdaRegion = concreteMemory.regionStorage.getLambdaCallSiteRegion(JcLambdaCallSiteRegionId(ctx))
        val callSite = lambdaRegion.findCallSite(heapRef)
        if (callSite != null) {
            val args = callSite.callSiteArgs
            val lambda = callSite.lambda
            val argTypes = lambda.callSiteArgTypes
            val callSiteArgs = mutableListOf<Any?>()
            for ((arg, argType) in args.zip(argTypes)) {
                callSiteArgs.add(resolveExpr(arg, argType))
            }
            val invHandler = Proxy.getInvocationHandler(obj) as LambdaInvocationHandler
            val method = lambda.actualMethod.method.method
            val actualMethod =
                if (method is JcEnrichedVirtualMethod)
                    method.approximationMethod ?: error("cannot find enriched method")
                else method
            invHandler.init(actualMethod, lambda.callSiteMethodName, callSiteArgs)
        }
    }

    private fun resolveConcreteObject(ref: UConcreteHeapRef, type: JcClassType): Any {
        check(!type.jcClass.isInterface)
        val address = ref.address
        val obj = bindings.virtToPhys(address)
        check(!obj.javaClass.isThreadLocal) { "ThreadLocal concretization not fully supported" }
        saveResolvedRef(ref.address, obj)
        // TODO: optimize #CM
        if (bindings.isMutableWithEffect())
            bindings.effectStorage.addObjectToEffectRec(obj)

        if (obj.javaClass.isProxy)
            initLambdaIfNeeded(ref, obj)

        for (field in bindings.symbolicFields(obj)) {
            val jcField = type.allInstanceFields.find { it.name == field.name }
                ?: error("resolveConcreteObject: can not find field $field")
            val fieldType = jcField.type
            val fieldSort = ctx.typeToSort(fieldType)
            val value = concreteMemory.readField(ref, jcField.field, fieldSort)
            val resolved = resolveExpr(value, fieldType)
            field.setFieldValue(obj, resolved)
        }

        return obj
    }

    private fun resolveThreadLocal(ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcClassType): Any {
        val instance = allocateClassInstance(type)
        saveResolvedRef(ref.address, instance)
        for (field in type.allInstanceFields) {
            val fieldType = field.type
            val lvalue = UFieldLValue(ctx.typeToSort(fieldType), heapRef, field.field)
            val fieldValue = resolveLValue(lvalue, fieldType)

            if (field.enclosingType.jcClass.isThreadLocal && field.name == "storage") {
                concreteMemory.executor.setThreadLocalValue(instance, fieldValue)
                continue
            }

            check(field.field !is JcEnrichedVirtualField)
            val javaField = field.field.toJavaField
                ?: error("resolveThreadLocal: can not find field $field")
            javaField.setFieldValue(instance, fieldValue)
        }

        return instance
    }

    override fun resolveObject(ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcClassType): Any? {
        val addressInModel = ref.address

        if (heapRef is UConcreteHeapRef && bindings.contains(heapRef.address)) {
            val obj = resolveConcreteObject(heapRef, type)
            saveResolvedRef(addressInModel, obj)
            return obj
        }

        return if (type.jcClass.isThreadLocal) {
            resolveThreadLocal(ref, heapRef, type)
        } else {
            val resolved = super.resolveObject(ref, heapRef, type) ?: return null
            if (resolved.javaClass.isProxy && heapRef is UConcreteHeapRef)
                initLambdaIfNeeded(heapRef, resolved)
            resolved
        }
    }

    override fun allocateClassInstance(type: JcClassType): Any =
        createDefault(type) ?: error("createDefault: can not create instance of ${type.jcClass.name}")
}
