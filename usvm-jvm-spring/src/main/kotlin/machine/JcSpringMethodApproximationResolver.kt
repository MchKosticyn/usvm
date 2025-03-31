package machine

import io.ksmt.utils.asExpr
import isDeserializationMethod
import isSpringController
import isSpringRepository
import machine.state.pinnedValues.JcSpringPinnedValueSource
import machine.concreteMemory.JcConcreteMemory
import machine.state.JcSpringState
import machine.state.memory.JcSpringMemory
import machine.state.pinnedValues.JcPinnedKey
import machine.state.pinnedValues.JcPinnedKey.Companion.mockCallResult
import machine.state.pinnedValues.JcPinnedKey.Companion.responseStatus
import machine.state.pinnedValues.JcPinnedValue
import machine.state.pinnedValues.JcStringPinnedKey
import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.ext.*
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UNullRef
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.makeSymbolicRef
import org.usvm.api.makeSymbolicRefSubtype
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcContext
import org.usvm.machine.JcMethodCall
import org.usvm.machine.state.skipMethodInvocationWithValue
import org.usvm.types.single
import org.usvm.util.allInstanceFields
import org.usvm.util.classesOfLocations
import utils.toJcType
import java.util.ArrayList
import java.util.TreeMap

class JcSpringMethodApproximationResolver (
    ctx: JcContext,
    applicationGraph: JcApplicationGraph,
    private val jcConcreteMachineOptions: JcConcreteMachineOptions,
    private val jcSpringMachineOptions: JcSpringMachineOptions
) : JcConcreteMethodApproximationResolver(ctx, applicationGraph) {

    override fun approximate(callJcInst: JcMethodCall): Boolean {
        return approximateInternal(callJcInst) || super.approximate(callJcInst)
    }

    private fun approximateInternal(callJcInst: JcMethodCall): Boolean {
        if (callJcInst.method.isStatic) {
            return approximateStaticMethod(callJcInst)
        }

        return approximateRegularMethod(callJcInst)
    }

    private fun approximateStaticMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        val enclosingClass = method.enclosingClass
        val className = enclosingClass.name

        if (className.contains("org.springframework.boot")) {
            if (approximateSpringBootStaticMethod(methodCall)) return true
        }

        if (className == "com.fasterxml.jackson.databind.deser.BeanDeserializer") {
            if (approximateBeanDeserializerStatic(methodCall)) return true
        }

        if (className == "org.springframework.web.method.annotation.AbstractNamedValueMethodArgumentResolver") {
            if (approximateArgumentResolverStatic(methodCall)) return true
        }

        if (className == "generated.org.springframework.boot.pinnedValues.PinnedValueStorage") {
            if (approximatePinnedValueStorage(methodCall)) return true
        }

        return false
    }

    private fun approximateRegularMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        val enclosingClass = method.enclosingClass
        val className = enclosingClass.name

        if (className.contains("org.springframework.boot")) {
            if (approximateSpringBootMethod(methodCall)) return true
        }

        if (enclosingClass.isSpringRepository) {
            if (approximateSpringRepositoryMethod(methodCall)) return true
        }

        if (enclosingClass.annotations.any { it.name == "org.springframework.stereotype.Service" }) {
            if (approximateSpringServiceMethod(methodCall)) return true
        }

        if (className == "org.springframework.web.method.HandlerMethod") {
            if (approximateHandlerMethod(methodCall)) return true
        }

        if (className.contains("org.springframework.web.servlet.mvc.method.annotation.AbstractMessageConverterMethodArgumentResolver\$EmptyBodyCheckingHttpInputMessage")) {
            if (approximateMessageConverter(methodCall)) return true
        }

        if (className.contains("org.springframework.mock.web.MockHttpServletRequest")) {
            if (approximateMockHttpRequest(methodCall)) return true
        }

        return false
    }

    private fun getTypeFromParameter(parameter: UHeapRef) : JcType? = scope.calcOnState {
        // TODO: rework (ask Artur)
        this as JcSpringState
        val annotatedMethodParameterType = memory.types.typeOf((parameter as UConcreteHeapRef).address) as JcClassType
        val parameterTypeField = annotatedMethodParameterType.allInstanceFields.single {it.name == "parameterType"}
        val parameterTypeRef = memory.readField(parameter, parameterTypeField.field, ctx.addressSort) as UConcreteHeapRef
        val typeType = memory.types.getTypeStream(parameterTypeRef).single() as JcClassType
        val typeNameField = typeType.allInstanceFields.single {it.name == "name"}
        val typeNameRef = memory.readField(parameterTypeRef, typeNameField.field, ctx.addressSort) as UConcreteHeapRef
        val typeName = springMemory.tryHeapRefToObject(typeNameRef) as String
        val type = ctx.cp.findTypeOrNull(typeName)

        if (type == null) {
            println("Non-concrete type is not supported for controller parameter")
            return@calcOnState null
        }

        return@calcOnState type
    }

    private fun accessPinnedValue(
        state: JcSpringState,
        nameArg: UExpr<out USort>,
        pinnedSourceNameArg: UConcreteHeapRef,
        clazzArg: UConcreteHeapRef
    ): Pair<JcPinnedKey, JcType>? = with(state) {
        val pinnedSourceName = springMemory.tryHeapRefToObject(pinnedSourceNameArg) as String?
        val name =
            if (nameArg is UNullRef) null
            else springMemory.tryHeapRefToObject(nameArg as UConcreteHeapRef) as String
        val clazz = springMemory.tryHeapRefToObject(clazzArg) as Class<*>?

        if (clazz == null || pinnedSourceName == null) {
            println("Source and class should be concrete")
            return null
        }

        val type = clazz.toJcType(ctx)
        if (type == null) {
            println("Type cannot be found: ${clazz.name}")
            return null
        }

        val source = JcSpringPinnedValueSource.valueOf(pinnedSourceName)
        val key = if (name != null) JcPinnedKey.ofName(source, name) else JcPinnedKey.ofSource(source)
        return key to type
    }

    private fun pinnedValueToStringArray(value: JcPinnedValue, state: JcSpringState): JcPinnedValue? {
        val memory = state.memory as JcSpringMemory
        val concretizer = memory.getConcretizer(state)
        val result = concretizer.resolveExpr(value.getExpr(), value.getType()) ?: return null
        val stringArrayType = ctx.cp.arrayTypeOf(ctx.stringType)
        val expr = memory.objectToExpr(arrayOf(result.toString()), stringArrayType)
        return JcPinnedValue(expr, stringArrayType)
    }

    private fun approximatePinnedValueStorage(methodCall: JcMethodCall): Boolean = with(methodCall) {
        if (method.name == "_writePinnedInner") {
            return scope.calcOnState {
                this as JcSpringState
                val pinnedSourceNameArg = methodCall.arguments[0] as UConcreteHeapRef
                val nameArg = methodCall.arguments[1]
                val sourceArg = methodCall.arguments[2]
                val clazzArg = methodCall.arguments[3] as UConcreteHeapRef
                val (key, type) = accessPinnedValue(this, nameArg, pinnedSourceNameArg, clazzArg)
                    ?: return@calcOnState false

                setPinnedValue(key, sourceArg, type)

                skipMethodInvocationWithValue(methodCall, ctx.voidValue)
                return@calcOnState true
            }
        }

        if (method.name == "_readPinnedInner") {
            return scope.calcOnState {
                this as JcSpringState
                val pinnedSourceNameArg = methodCall.arguments[0] as UConcreteHeapRef
                val nameArg = methodCall.arguments[1]
                val clazzArg = methodCall.arguments[2] as UConcreteHeapRef

                val (key, type) = accessPinnedValue(this, nameArg, pinnedSourceNameArg, clazzArg)
                    ?: return@calcOnState false

                // TODO: Other sorts? #AA
                val value = createPinnedIfAbsent(key, type, scope, ctx.addressSort) ?: return@calcOnState false

                skipMethodInvocationWithValue(methodCall, value.getExpr())
                return@calcOnState true
            }
        }

        if (method.name == "preparePinnedValues") {
            return scope.calcOnState {
                this as JcSpringState
                val headers = pinnedValues.getValuesOfSource<JcStringPinnedKey>(JcSpringPinnedValueSource.REQUEST_HEADER)
                val parameters = pinnedValues.getValuesOfSource<JcStringPinnedKey>(JcSpringPinnedValueSource.REQUEST_PARAM)
                (headers + parameters)
                    .map { it.key to pinnedValueToStringArray(it.value, this) }
                    .filter { it.second != null }
                    .forEach { (key, value) -> setPinnedValue(key, value!!.getExpr(), value.getType()) }

                skipMethodInvocationWithValue(methodCall, ctx.voidValue)
                return@calcOnState true
            }
        }

        return false
    }

    private fun approximateArgumentResolverStatic(methodCall: JcMethodCall): Boolean = with(methodCall) {
        /* AbstractNamedValueMethodArgumentResolver
         * Web data binder convert is too hard to execute symbolically
         * If it is convertible, will just replace string argument given in state user values
         */
        if (method.name == "convertIfNecessary") {
            val parameter = methodCall.arguments[0] as UConcreteHeapRef
            val source = methodCall.arguments[4]
            return scope.calcOnState {
                this as JcSpringState
                val type = getTypeFromParameter(parameter)?.autoboxIfNeeded()!!
                val key = getPinnedValueKey(source)!!
                val newSymbolicValue = createPinnedAndReplace(key, type, scope, ctx.addressSort, false)
                    ?: return@calcOnState false
                skipMethodInvocationWithValue(methodCall, newSymbolicValue.getExpr())

                return@calcOnState true
            }
        }

        return@with false
    }

    private fun approximateMockHttpRequest(methodCall: JcMethodCall): Boolean = with(methodCall) {
        if (method.name == "setAttribute") {
            val keyArgument = arguments[1].asExpr(ctx.addressSort)
            val valueArgument = arguments[2].asExpr(ctx.addressSort)
            return scope.calcOnState {
                this as JcSpringState
                val key = springMemory.tryHeapRefToObject(keyArgument as UConcreteHeapRef) as String?
                val value = springMemory.tryHeapRefToObject(valueArgument as UConcreteHeapRef)

                // TODO: Use other symbolic check if possible #AA
                if (value != null || key == null) return@calcOnState false

                val pinnedValueKey = JcPinnedKey.requestAttribute(key)
                setPinnedValue(pinnedValueKey, valueArgument, ctx.cp.objectType)
                skipMethodInvocationWithValue(methodCall, ctx.voidValue)
                return@calcOnState true
            }
        }

        if (method.name == "getAttribute") {
            val keyArgument = arguments[1].asExpr(ctx.addressSort)
            return scope.calcOnState {
                this as JcSpringState
                val key = springMemory.tryHeapRefToObject(keyArgument as UConcreteHeapRef) as String?
                    ?: return@calcOnState false
                val userValueKey = JcPinnedKey.requestAttribute(key)
                val writtenValue = getPinnedValue(userValueKey) ?: return@calcOnState false
                skipMethodInvocationWithValue(methodCall, writtenValue.getExpr())
                return@calcOnState true
            }
        }

        return false
    }

    private fun approximateMessageConverter(methodCall: JcMethodCall): Boolean {
        if (methodCall.method.name == "hasBody") {
            return scope.calcOnState {
                this as JcSpringState
                val hasBodyKey = JcPinnedKey.requestHasBody()
                val hasBody = createPinnedIfAbsent(hasBodyKey, ctx.cp.boolean, scope, ctx.booleanSort)
                    ?: return@calcOnState false
                skipMethodInvocationWithValue(methodCall, hasBody.getExpr())
                return@calcOnState true
            }
        }
        return false
    }

    private fun approximateBeanDeserializerStatic(methodCall: JcMethodCall): Boolean = with(methodCall) {
        if (method.name == "_concreteDeserialization") {
            return scope.calcOnState {
                // TODO: In any user code too #AA
                val concreteDeserializationMode = callStack.any { it.method.isDeserializationMethod }
                skipMethodInvocationWithValue(methodCall, ctx.mkBool(!concreteDeserializationMode))
                return@calcOnState true
            }
        }
        return false
    }

    private fun approximateSpringBootMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        val methodName = method.name
        if (methodName == "deduceMainApplicationClass") {
            scope.doWithState {
                val memory = memory as JcConcreteMemory
                val firstMethod = callStack.firstMethod()
                val mainApplicationClass = JcConcreteMemoryClassLoader.loadClass(firstMethod.enclosingClass)
                val typeRef = memory.tryAllocateConcrete(mainApplicationClass, ctx.classType)!!
                skipMethodInvocationWithValue(methodCall, typeRef)
            }

            return true
        }

        if (methodName == "printBanner") {
            val bannerType = ctx.cp.findTypeOrNull(method.returnType.typeName) as JcClassType
            val bannerModeType = bannerType.innerTypes.single()
            check(bannerModeType.jcClass.isEnum)
            val enumField = bannerModeType.declaredFields.single { it.isStatic && it.name == "OFF" }
            val fieldRef = JcFieldRef(instance = null, field = enumField)
            val bannerModeOffValue = fieldRef.accept(exprResolver)?.asExpr(ctx.addressSort) ?: return true
            val bannerModeField =
                method.enclosingClass
                    .declaredFields
                    .singleOrNull { it.name == "bannerMode" }
            val springApplication = arguments.first().asExpr(ctx.addressSort)
            scope.doWithState {
                if (bannerModeField != null)
                    memory.writeField(springApplication, bannerModeField, ctx.addressSort, bannerModeOffValue, ctx.trueExpr)
                skipMethodInvocationWithValue(methodCall, ctx.nullRef)
            }

            return true
        }

        val className = method.enclosingClass.name
        if (className.contains("SpringApplicationShutdownHook") && methodName == "registerApplicationContext") {
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, ctx.voidValue)
            }

            return true
        }

        if (methodName == "_startAnalysis") {
            scope.doWithState {
                println("starting, state.id = $id")
                val framesToDrop = callStack.size - 1
                callStack.dropFromBottom(framesToDrop)
                memory.stack.dropFromBottom(framesToDrop)
                skipMethodInvocationWithValue(methodCall, ctx.voidValue)
            }

            return true
        }

        if (methodName == "_allControllerPaths") {
            val allControllerPaths = allControllerPaths()
            scope.doWithState {
                val memory = memory as JcConcreteMemory
                val type = allControllerPaths.javaClass
                val jcType = ctx.cp.findTypeOrNull(type.typeName)!!
                val heapRef = memory.tryAllocateConcrete(allControllerPaths, jcType)!!
                skipMethodInvocationWithValue(methodCall, heapRef)
            }

            return true
        }

        return false
    }

    private fun approximateSpringRepositoryMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        val returnType = ctx.cp.findType(methodCall.method.returnType.typeName)
        val mockedValue: UExpr<out USort>
        val mockedValueType: JcType
        when {
            returnType is JcClassType -> {
                val suitableType =
                    findSuitableTypeForMock(returnType) ?:
                    ctx.typeSystem<JcType>().findSubtypes(returnType).filterNot { type ->
                        (type as? JcClassType)?.jcClass?.let { it.isInterface || it.isAbstract }
                            ?: true
                    }.first()
                mockedValueType = suitableType
                mockedValue = scope.makeSymbolicRef(suitableType)!!
            }
            else -> {
                check(returnType is JcPrimitiveType)
                mockedValueType = returnType
                mockedValue = scope.calcOnState { makeSymbolicPrimitive(ctx.typeToSort(returnType)) }
            }
        }
        println("[Mocked] Mocked repository method")
        scope.doWithState {
            this as JcSpringState
            setPinnedValue(mockCallResult(method), mockedValue, mockedValueType)
            skipMethodInvocationWithValue(methodCall, mockedValue)
        }
        return true
    }

    private fun approximateSpringServiceMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        val returnType = ctx.cp.findType(methodCall.method.returnType.typeName)
        if (jcSpringMachineOptions.springAnalysisMode == SpringAnalysisMode.WebMVCTest) {
            val mockedValue: UExpr<out USort>
            val mockedValueType: JcType
            when (returnType) {
                is JcClassType -> {
                    val suitableType = findSuitableTypeForMock(returnType)
                    if (suitableType != null) {
                        mockedValueType = suitableType
                        mockedValue = scope.makeSymbolicRef(suitableType)!!
                    } else {
                        mockedValueType = returnType
                        mockedValue = scope.makeSymbolicRefSubtype(returnType)!!
                    }
                }

                is JcArrayType -> {
                    mockedValueType = returnType
                    mockedValue = scope.makeSymbolicRef(returnType)!!
                }

                else -> {
                    check(returnType is JcPrimitiveType)
                    mockedValueType = returnType
                    mockedValue = scope.calcOnState { makeSymbolicPrimitive(ctx.typeToSort(returnType)) }
                }
            }

            println("[Mocked] Mocked service method")
            scope.doWithState {
                this as JcSpringState
                setPinnedValue(mockCallResult(methodCall.method), mockedValue, mockedValueType)
                skipMethodInvocationWithValue(methodCall, mockedValue)
            }

            return true
        }

        return false
    }

    private fun findSuitableTypeForMock(type: JcClassType): JcClassType? {
        val arrayListType by lazy { ctx.cp.findType("java.util.ArrayList") }
        val hashMapType by lazy { ctx.cp.findType("java.util.HashMap") }
        val hashSetType by lazy { ctx.cp.findType("java.util.HashSet") }

        return when {
            !type.jcClass.isInterface && !type.isAbstract -> type
            type.typeName == "java.util.List" || type.typeName == "java.util.Collection"
                    || arrayListType.isAssignable(type) -> arrayListType
            type.typeName == "java.util.Map" || hashMapType.isAssignable(type) -> hashMapType
            type.typeName == "java.util.Set" || hashSetType.isAssignable(type) -> hashSetType
            else -> null
        } as? JcClassType
    }

    private fun approximateHandlerMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        val methodName = method.name
        if (methodName == "formatInvokeError") {
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, arguments[1])
            }

            return true
        }

        return false
    }


    private fun pathFromAnnotation(annotation: JcAnnotation): String {
        val values = annotation.values
        check(values.contains("value"))
        val value = values["value"] as List<*>
        return value[0] as String
    }

    private fun reqMappingPath(controllerType: JcClassOrInterface): String? {
        for (annotation in controllerType.annotations) {
            if (annotation.name != "org.springframework.web.bind.annotation.RequestMapping")
                continue

            return pathFromAnnotation(annotation)
        }

        return null
    }

    @Suppress("UNUSED_PARAMETER")
    private fun shouldSkipPath(path: String, kind: String, controllerTypeName: String): Boolean {
        return path != "/service/link_node"
    }

    private fun shouldSkipController(controllerType: JcClassOrInterface): Boolean {
        return controllerType.annotations.any {
            // TODO: support conditional controllers and dependent conditional beans
            it.name == "org.springframework.boot.autoconfigure.condition.ConditionalOnProperty"
        }
    }

    private fun getRequestMappingMethod(annotation: JcAnnotation): String {
        val values = annotation.values
        // TODO: support list #CM
        val method = (values["method"] as List<*>)[0] as JcField
        return method.name.uppercase()
    }

    private fun combinePaths(basePath: String, localPath: String): String {
        val basePathEndsWithSlash = basePath.endsWith('/')
        val localPathStartsWithSlash = localPath.startsWith('/')
        if (basePathEndsWithSlash && localPathStartsWithSlash)
            return basePath + localPath.substring(1)
        if (basePathEndsWithSlash || localPathStartsWithSlash)
            return basePath + localPath
        return "$basePath/$localPath"
    }

    private fun allControllerPaths(): Map<String, Map<String, List<Any>>> {
        val controllerTypes =
            ctx.classesOfLocations(jcConcreteMachineOptions.projectLocations)
                .filter { !it.isAbstract && !it.isInterface && !it.isAnonymous && it.isSpringController }
                .filterNot { shouldSkipController(it) }
        val result = TreeMap<String, Map<String, List<Any>>>()
        for (controllerType in controllerTypes) {
            val basePath: String? = reqMappingPath(controllerType)
            val paths = TreeMap<String, List<Any>>()
            val methods = controllerType.declaredMethods
            for (method in methods) {
                for (annotation in method.annotations) {
                    val kind =
                        when (annotation.name) {
                            "org.springframework.web.bind.annotation.RequestMapping" -> getRequestMappingMethod(annotation)
                            "org.springframework.web.bind.annotation.GetMapping" -> "GET"
                            "org.springframework.web.bind.annotation.PostMapping" -> "POST"
                            "org.springframework.web.bind.annotation.PutMapping" -> "PUT"
                            "org.springframework.web.bind.annotation.DeleteMapping" -> "DELETE"
                            "org.springframework.web.bind.annotation.PatchMapping" -> "PATCH"
                            else -> null
                        }

                    if (kind != null) {
                        val localPath = pathFromAnnotation(annotation)
                        val path = if (basePath != null) combinePaths(basePath, localPath) else localPath
                        if (shouldSkipPath(path, kind, controllerType.name))
                            continue
                        val pathArgsCount = path.filter { it == '{' }.length
                        val properties = ArrayList(listOf(kind, Integer.valueOf(pathArgsCount)))
                        paths[path] = properties
                    }
                }
            }
            if (paths.isNotEmpty())
                result[controllerType.name] = paths
        }

        return result
    }


    private fun approximateSpringBootStaticMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        if (method.name == "deduceFromClasspath") {
            val returnType = ctx.cp.findTypeOrNull(method.returnType.typeName) as? JcClassType
                ?: return false
            check(returnType.jcClass.isEnum)
            val enumField = returnType.declaredFields.single { it.isStatic && it.name == "SERVLET" }
            val fieldRef = JcFieldRef(instance = null, field = enumField)
            val value = fieldRef.accept(exprResolver) ?: return true
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, value)
            }

            return true
        }

        if (method.name == "_println") {
            scope.doWithState {
                val memory = memory as JcConcreteMemory
                val messageExpr = methodCall.arguments[0].asExpr(ctx.addressSort) as UConcreteHeapRef
                val message = memory.tryHeapRefToObject(messageExpr) as String
                println("\u001B[36m$message\u001B[0m")
                skipMethodInvocationWithValue(methodCall, ctx.voidValue)
            }

            return true
        }

        return false
    }

}
