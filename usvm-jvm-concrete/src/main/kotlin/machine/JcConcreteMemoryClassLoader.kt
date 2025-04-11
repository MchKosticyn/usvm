package machine

import com.sun.jdi.VirtualMachine
import features.JcLambdaFeature
import machine.concreteMemory.JcConcreteEffectStorage
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcClasspathFeature
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.allSuperHierarchySequence
import org.jacodb.approximation.ApproximationClassName
import org.jacodb.approximation.Approximations
import org.jacodb.approximation.JcEnrichedVirtualMethod
import org.jacodb.impl.bytecode.JcMethodImpl
import org.jacodb.impl.cfg.MethodNodeBuilder
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.features.classpaths.ClasspathCache
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.jacodb.impl.types.MethodInfo
import org.jacodb.impl.types.ParameterInfo
import org.usvm.concrete.api.internal.InitHelper
import org.usvm.jvm.util.replace
import org.usvm.jvm.util.toByteArray
import org.usvm.util.javaName
import utils.isInstrumentedClinit
import utils.isInstrumentedInit
import utils.isInstrumentedInternalInit
import utils.isLambdaTypeName
import utils.setStaticFieldValue
import utils.staticFields
import java.io.File
import java.lang.instrument.Instrumentation
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.security.CodeSource
import java.security.SecureClassLoader
import java.util.Collections
import java.util.Enumeration
import java.util.IdentityHashMap
import java.util.LinkedList
import java.util.Queue
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Loads known classes using [ClassLoader.getSystemClassLoader], or defines them using bytecode from jacodb if they are unknown.
 */
// TODO: make this 'class'
object JcConcreteMemoryClassLoader : SecureClassLoader(ClassLoader.getSystemClassLoader()) {

    lateinit var cp: JcClasspath
    private val loadedClasses = hashMapOf<String, Class<*>>()
    private val initializedStatics = hashSetOf<Class<*>>()
    private var effectStorage: JcConcreteEffectStorage? = null

    private val File.isJar
        get() = this.extension == "jar"

    private val File.URL
        get() = this.toURI().toURL()

    private fun File.matchResource(locURI: URI, name: String): Boolean {
        check(name.isNotEmpty())
        val relativePath by lazy { locURI.relativize(this.toURI()).toString() }
        return this.name == name
                || relativePath == name
                || relativePath.endsWith(name)
    }

    private fun JarEntry.matchResource(name: String, single: Boolean): Boolean {
        check(name.isNotEmpty())
        val entryName = this.name
        return entryName == name
                || entryName.endsWith(name)
                || !single && entryName.contains(name)
    }

    private fun findResourcesInFolder(
        locFile: File,
        name: String,
        single: Boolean
    ): List<URL>? {
        check(locFile.isDirectory)
        val result = mutableListOf<URL>()

        val locURI = locFile.toURI()
        val queue: Queue<File> = LinkedList()
        var current: File? = locFile
        while (current != null) {
            if (current.matchResource(locURI, name)) {
                result.add(current.URL)
                if (single)
                    break
            }

            if (current.isDirectory)
                queue.addAll(current.listFiles()!!)

            current = queue.poll()
        }

        if (result.isNotEmpty())
            return result

        return null
    }

    private fun findResourcesInJar(locFile: File, name: String, single: Boolean): List<URL>? {
        val jar = JarFile(locFile)
        val jarPath = "jar:file:${locFile.absolutePath}!".replace("\\", "/")
        if (single) {
            for (current in jar.entries()) {
                if (current.matchResource(name, true))
                    return listOf(URI("$jarPath/${current.name}").toURL())
            }
        } else {
            val result = jar.entries().toList().mapNotNull {
                if (it.matchResource(name, false))
                    URI("$jarPath/${it.name}").toURL()
                else null
            }
            if (result.isNotEmpty())
                return result
        }

        return null
    }

    private fun tryGetResource(locFile: File, name: String): List<URL>? {
        check(locFile.isFile)
        return if (locFile.name == name) listOf(locFile.URL) else null
    }

    private fun internalFindResources(name: String?, single: Boolean): Enumeration<URL>? {
        if (name.isNullOrEmpty())
            return null

        val result = mutableListOf<URL>()
        for (loc in cp.locations) {
            val locFile = loc.jarOrFolder
            val resources =
                if (locFile.isJar) findResourcesInJar(locFile, name, single)
                else if (locFile.isDirectory) findResourcesInFolder(locFile, name, single)
                else tryGetResource(locFile, name)
            if (resources != null) {
                if (single)
                    return Collections.enumeration(resources)
                result += resources
            }
        }

        if (result.isNotEmpty())
            return Collections.enumeration(result)

        return null
    }

    internal fun setEffectStorage(storage: JcConcreteEffectStorage) {
        effectStorage = storage
    }

    internal fun disableEffectStorage() {
        effectStorage = null
    }

    fun initializedStatics(): Set<Class<*>> {
        return initializedStatics
    }

    private val afterClinitAction: java.util.function.Function<String, Void?> =
        java.util.function.Function { className: String ->
            val storage = effectStorage ?: return@Function null
            val clazz = loadedClasses[className] ?: return@Function null
            initializedStatics.add(clazz)
            storage.addStatics(clazz)
            null
        }

    private val afterInitAction: java.util.function.Function<Any, Void?> =
        java.util.function.Function { newObj: Any ->
            val storage = effectStorage ?: return@Function null
            storage.addNewObject(newObj)
            null
        }

    private var _internalObjects: MutableSet<Any>? = null

    private val afterInternalInitAction: java.util.function.Function<Any, Void?> =
        java.util.function.Function { newObj: Any ->
            _internalObjects?.add(newObj)
            null
        }

    fun startInternalsCollecting() {
        _internalObjects = Collections.newSetFromMap(IdentityHashMap())
    }

    fun endInternalsCollecting(): MutableSet<Any> {
        val result = _internalObjects!!
        _internalObjects = null
        return result
    }

    private fun initInitHelper(type: Class<*>) {
        check(type.typeName == InitHelper::class.java.typeName)
        // Forcing `<clinit>` of `InitHelper`
        type.declaredFields.first().get(null)
        // Initializing static fields
        val staticFields = type.staticFields
        staticFields
            .find { it.name == InitHelper::afterClinitAction.javaName }!!
            .setStaticFieldValue(afterClinitAction)
        staticFields
            .find { it.name == InitHelper::afterInitAction.javaName }!!
            .setStaticFieldValue(afterInitAction)
        staticFields
            .find { it.name == InitHelper::afterInternalInitAction.javaName }!!
            .setStaticFieldValue(afterInternalInitAction)
    }

    override fun loadClass(name: String?): Class<*> {
        if (name == null)
            throw ClassNotFoundException()

        if (name.contains("net.javacrumbs.shedlock.spring.aop.MethodProxyLockConfiguration\$\$SpringCGLIB\$\$0"))
            println()
        val loadedClass = loadedClasses[name]
        if (loadedClass != null)
            return loadedClass

        if (name.isLambdaTypeName)
            return loadLambdaClass(name)

        val jcClass = cp.findClassOrNull(name) ?: throw ClassNotFoundException(name)
        return defineClassRecursively(jcClass)
    }

    fun isLoaded(jcClass: JcClassOrInterface): Boolean {
        return loadedClasses.containsKey(jcClass.name)
    }

    private fun loadLambdaClass(name: String): Class<*> {
        val type = JcLambdaFeature.lambdaClassByName(name) ?: super.loadClass(name)
        loadedClasses[name] = type
        return type
    }

    fun loadClass(jcClass: JcClassOrInterface): Class<*> {
        if (jcClass.name.isLambdaTypeName)
            return loadLambdaClass(jcClass.name)

        return defineClassRecursively(jcClass)
    }

    private fun defineClass(name: String, code: ByteArray): Class<*> {
        return defineClass(name, ByteBuffer.wrap(code), null as CodeSource?)
    }

    override fun getResource(name: String?): URL? {
        try {
            return internalFindResources(name, true)?.nextElement()
        } catch (e: Throwable) {
            error("Failed getting resource ${e.message}")
        }
    }

    override fun getResources(name: String?): Enumeration<URL> {
        try {
            return internalFindResources(name, false) ?: Collections.emptyEnumeration()
        } catch (e: Throwable) {
            error("Failed getting resources ${e.message}")
        }
    }

    private fun typeIsRuntimeGenerated(jcClass: JcClassOrInterface): Boolean {
        return jcClass.name == "org.mockito.internal.creation.bytebuddy.inject.MockMethodDispatcher"
    }

    private fun defineClassRecursively(jcClass: JcClassOrInterface): Class<*> =
        defineClassRecursively(jcClass, hashSetOf())
            ?: error("Can't define class $jcClass")

    private class JcCpWithoutApproximations(val cp: JcClasspath) : JcClasspath by cp {
        override val features: List<JcClasspathFeature> by lazy {
            cp.featuresWithoutApproximations()
        }
    }

    private class JcClassWithoutApproximations(
        private val cls: JcClassOrInterface, private val cp: JcCpWithoutApproximations
    ) : JcClassOrInterface by cls {
        override val classpath: JcClasspath get() = cp
    }

    private val cpWithoutApproximations by lazy { JcCpWithoutApproximations(cp) }

    private fun JcMethod.methodWithoutApproximations(): JcMethod {
        val parameters = parameters.map {
            ParameterInfo(it.type.typeName, it.index, it.access, it.name, emptyList())
        }
        val info = MethodInfo(name, description, signature, access, emptyList(), emptyList(), parameters)

        val cp = cpWithoutApproximations
        check(cp.cp === enclosingClass.classpath) { "Classpath mismatch" }

        val featuresChain = JcFeaturesChain(cp.features)
        val cls = JcClassWithoutApproximations(enclosingClass, cp)
        return JcMethodImpl(info, featuresChain, cls)
    }

    private fun JcClasspath.featuresWithoutApproximations(): List<JcClasspathFeature> {
        val featuresChainField = this.javaClass.getDeclaredField("featuresChain")
        featuresChainField.isAccessible = true
        val featuresChain = featuresChainField.get(this) as JcFeaturesChain
        return featuresChain.features.filterNot { it is Approximations || it is ClasspathCache }
    }

    private fun getBytecode(jcClass: JcClassOrInterface): ByteArray {
        val instrumentedMethods = jcClass.declaredMethods.filter {
            it.isInstrumentedClinit || it.isInstrumentedInit || it.isInstrumentedInternalInit
        }

        if (jcClass.name.contains("LibSLRuntime"))
            println()
        if (instrumentedMethods.isEmpty())
            return jcClass.bytecode()

        return jcClass.withAsmNode { asmNode ->
            for (method in instrumentedMethods) {
                val isApproximated = method is JcEnrichedVirtualMethod
                        || Approximations.findOriginalByApproximationOrNull(ApproximationClassName(jcClass.name)) != null
                if (isApproximated && asmNode.methods.none { it.name == method.name && it.desc == method.description })
                    continue

                val rawInstList = if (isApproximated) {
                    val newMethod = method.methodWithoutApproximations()
                    newMethod.rawInstList
                } else { method.rawInstList }

                val newMethodNode = MethodNodeBuilder(method, rawInstList).build()
                val oldMethodNode = asmNode.methods.find { it.name == method.name && it.desc == method.description }
                asmNode.methods.replace(oldMethodNode, newMethodNode)
            }

            asmNode.toByteArray(jcClass.classpath)
        }
    }

    private fun defineClassRecursively(
        jcClass: JcClassOrInterface,
        visited: MutableSet<JcClassOrInterface>
    ): Class<*>? {
        val className = jcClass.name
        return loadedClasses.getOrPut(className) {
            if (!visited.add(jcClass))
                return null

            if (jcClass.declaration.location.isRuntime || typeIsRuntimeGenerated(jcClass))
                return@getOrPut super.loadClass(className)

            if (jcClass is JcUnknownClass)
                throw ClassNotFoundException(className)

            val notVisitedSupers = jcClass.allSuperHierarchySequence.filterNot { it in visited }
            notVisitedSupers.forEach { defineClassRecursively(it, visited) }

            val bytecode = getBytecode(jcClass)
            val loadedClass = defineClass(className, bytecode)
            if (loadedClass.typeName == InitHelper::class.java.typeName)
                initInitHelper(loadedClass)

            return@getOrPut loadedClass
        }
    }
}
