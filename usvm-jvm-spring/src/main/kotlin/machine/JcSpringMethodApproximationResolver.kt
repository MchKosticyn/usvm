package machine

import io.ksmt.utils.asExpr
import isSpringController
import machine.concreteMemory.JcConcreteMemory
import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.isAssignable
import org.jacodb.api.jvm.ext.isEnum
import org.jacodb.api.jvm.ext.isSubClassOf
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.makeSymbolicRef
import org.usvm.api.makeSymbolicRefSubtype
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcContext
import org.usvm.machine.JcMethodCall
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.skipMethodInvocationWithValue
import org.usvm.util.classesOfLocations
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

        return false
    }

    private fun approximateRegularMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        val enclosingClass = method.enclosingClass
        val className = enclosingClass.name

        if (className.contains("org.springframework.boot")) {
            if (approximateSpringBootMethod(methodCall)) return true
        }

        val repositoryType = ctx.cp.findClassOrNull("org.springframework.data.repository.Repository")
        if (repositoryType != null && enclosingClass.isSubClassOf(repositoryType)) {
            if (approximateSpringRepositoryMethod(methodCall)) return true
        }

        if (enclosingClass.annotations.any { it.name == "org.springframework.stereotype.Service" }) {
            if (approximateSpringServiceMethod(methodCall)) return true
        }

        if (className == "org.springframework.web.method.HandlerMethod") {
            if (approximateHandlerMethod(methodCall)) return true
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
            scope.doWithState {
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

        if (methodName.equals("_startAnalysis")) {
            scope.doWithState {
                println("starting, state.id = $id")
                val framesToDrop = callStack.size - 1
                callStack.dropFromBottom(framesToDrop)
                memory.stack.dropFromBottom(framesToDrop)
                skipMethodInvocationWithValue(methodCall, ctx.voidValue)
            }

            return true
        }

        if (methodName.equals("_allControllerPaths")) {
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
        when {
            returnType is JcClassType -> {
                val suitableType =
                    findSuitableTypeForMock(returnType) ?:
                    ctx.typeSystem<JcType>().findSubtypes(returnType).filterNot {
                        (it as? JcClassType)?.jcClass?.let { it.isInterface || it.isAbstract }
                            ?: true
                    }.first()
                mockedValue = scope.makeSymbolicRef(suitableType)!!
            }
            else -> {
                check(returnType is JcPrimitiveType)
                mockedValue = scope.calcOnState { makeSymbolicPrimitive(ctx.typeToSort(returnType)) }
            }
        }
        println("[Mocked] Mocked repository method")
        scope.doWithState {
            skipMethodInvocationWithValue(methodCall, mockedValue)
        }
        return true
    }

    private fun approximateSpringServiceMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        val returnType = ctx.cp.findType(methodCall.method.returnType.typeName)
        if (jcSpringMachineOptions.springAnalysisMode == SpringAnalysisMode.WebMVCTest) {
            val mockedValue: UExpr<out USort>
            when (returnType) {
                is JcClassType -> {
                    val suitableType = findSuitableTypeForMock(returnType)
                    mockedValue = if (suitableType != null) {
                        scope.makeSymbolicRef(suitableType)!!
                    } else {
                        scope.makeSymbolicRefSubtype(returnType)!!
                    }
                }

                is JcArrayType -> {
                    mockedValue = scope.makeSymbolicRef(returnType)!!
                }

                else -> {
                    check(returnType is JcPrimitiveType)
                    mockedValue = scope.calcOnState { makeSymbolicPrimitive(ctx.typeToSort(returnType)) }
                }
            }

            println("[Mocked] Mocked service method")
            scope.doWithState {
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
        return false
    }

    private fun shuoldSkipController(controllerType: JcClassOrInterface): Boolean {
        return controllerType.annotations.any {
            // TODO: support conditional controllers and dependend conditional beans
            it.name == "org.springframework.boot.autoconfigure.condition.ConditionalOnProperty"
        }
    }

    private fun getRequestMappingMethod(annotation: JcAnnotation): String {
        val values = annotation.values
        // TODO: support list #CM
        val method = (values["method"] as List<*>)[0] as JcField
        return method.name.lowercase()
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
                .filterNot { shuoldSkipController(it) }
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
                            "org.springframework.web.bind.annotation.GetMapping" -> "get"
                            "org.springframework.web.bind.annotation.PostMapping" -> "post"
                            "org.springframework.web.bind.annotation.PutMapping" -> "put"
                            "org.springframework.web.bind.annotation.DeleteMapping" -> "delete"
                            "org.springframework.web.bind.annotation.PatchMapping" -> "patch"
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

        if (method.name.equals("_println")) {
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
