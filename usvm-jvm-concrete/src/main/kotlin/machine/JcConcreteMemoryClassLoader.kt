package machine

import features.JcGeneratedTypesFeature
import machine.state.concreteMemory.JcConcreteEffectStorage
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.allSuperHierarchySequence
import org.jacodb.approximation.ApproximationClassName
import org.jacodb.approximation.Approximations
import org.jacodb.approximation.JcEnrichedVirtualMethod
import org.jacodb.impl.cfg.MethodNodeBuilder
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.usvm.concrete.api.internal.ClassLoaderGetHelper
import org.usvm.concrete.api.internal.InitHelper
import org.usvm.jvm.concrete.JcConcreteClassLoader
import org.usvm.jvm.util.JcClassLoaderExt
import org.usvm.jvm.util.cpWithoutApproximations
import org.usvm.jvm.util.javaName
import org.usvm.jvm.util.replace
import org.usvm.jvm.util.staticFields
import org.usvm.jvm.util.toByteArray
import utils.isInstrumentedClinit
import utils.isInstrumentedGetClassLoader
import utils.isInstrumentedInit
import utils.isInstrumentedInternalInit
import utils.setStaticFieldValue
import utils.typeIsRuntimeGenerated
import java.io.File
import java.lang.reflect.Field
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
import utils.isLambdaRealName
import utils.isLoadableRuntimeClassName
import utils.isNotLoadableRuntimeClassName

/**
 * Loads known classes using [ClassLoader.getSystemClassLoader], or defines them using bytecode from jacodb if they are unknown.
 */
// TODO: make this 'class'
object JcConcreteMemoryClassLoader : SecureClassLoader(ClassLoader.getSystemClassLoader()), JcConcreteClassLoader, JcClassLoaderExt {

    lateinit var cp: JcClasspath
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
                || relativePath.endsWith(name + "/")
    }

    private fun JarEntry.matchResource(name: String, single: Boolean): Boolean {
        check(name.isNotEmpty())
        val entryName = this.name
        return entryName == name
                || entryName.endsWith(name)
                || !single && entryName.contains(name + "/")
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

    private fun internalFindResources(name: String, single: Boolean): Enumeration<URL>? {
        if (name.isEmpty())
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
            val clazz = findLoadedClass(className) ?: return@Function null
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

    private val replaceGetClassLoaderAction: java.util.function.Supplier<ClassLoader> =
        java.util.function.Supplier { JcConcreteMemoryClassLoader as ClassLoader }

    fun startInternalsCollecting() {
        _internalObjects = Collections.newSetFromMap(IdentityHashMap())
    }

    fun endInternalsCollecting(): MutableSet<Any> {
        val result = _internalObjects!!
        _internalObjects = null
        return result
    }

    private fun initStaticInstrumentationClass(clazz: Class<*>, targetClass: Class<*>, fieldInit: (Field) -> Unit) {
        check(clazz.typeName == targetClass.typeName)
        // Forcing `<clinit>` of class
        clazz.declaredFields.first().get(null)
        // Initializing static fields
        clazz.staticFields.forEach(fieldInit)
    }

    private fun initInitHelper(type: Class<*>) {
        fun fieldInit(field: Field) = when (field.name) {
            InitHelper::afterClinitAction.javaName -> field.setStaticFieldValue(afterClinitAction)
            InitHelper::afterInitAction.javaName -> field.setStaticFieldValue(afterInitAction)
            InitHelper::afterInternalInitAction.javaName -> field.setStaticFieldValue(afterInternalInitAction)
            else -> error("unexpected field in initInitHelper")
        }
        initStaticInstrumentationClass(type, InitHelper::class.java, ::fieldInit)
    }

    private fun initClassLoaderGetHelper(type: Class<*>) {
        fun fieldInit(field: Field) = when(field.name) {
            ClassLoaderGetHelper::replaceGetClassLoaderAction.javaName ->
                field.setStaticFieldValue(replaceGetClassLoaderAction)
            else -> error("unexpected field in initClassLoaderGetHelper")
        }
        initStaticInstrumentationClass(type, ClassLoaderGetHelper::class.java, ::fieldInit)
    }

    override fun loadClass(name: String?): Class<*> {
        if (name == null)
            throw ClassNotFoundException()

        val loaded = findLoadedClass(name)
        if (loaded != null)
            return loaded

        if (name.isLambdaRealName)
            throw ClassNotFoundException()

        if (name.isLoadableRuntimeClassName)
            return super.loadClass(name)

        val jcClassLookup = cp.findClassOrNull(name)
        val jcClass = when (jcClassLookup) {
            is JcUnknownClass -> null
            else -> jcClassLookup
        }
        return when {
            jcClass == null || name.isNotLoadableRuntimeClassName -> throw ClassNotFoundException()
            else -> defineClassRecursively(jcClass)
        }
    }

    fun isLoaded(jcClass: JcClassOrInterface): Boolean {
        return findLoadedClass(jcClass.name) != null
    }

    private fun loadLambdaClass(name: String): Class<*> {
        return checkNotNull(JcGeneratedTypesFeature.getHiddenClass(name)) {
            "lambda class not found for name $name"
        }
    }

    override fun addTypeBytes(name: String, typeBytes: ByteArray) {
        val className = name.replace('/', '.')
        if (!className.typeIsRuntimeGenerated)
            return

        JcGeneratedTypesFeature.addGeneratedTypeBytes(className, typeBytes)
    }

    override fun loadClass(jcClass: JcClassOrInterface, initialize: Boolean): Class<*> {
        val name = jcClass.name
        val isRuntimeLambdaClass = name.isLambdaRealName
        val loadedClass = when {
            isRuntimeLambdaClass -> loadLambdaClass(name)
            name.isNotLoadableRuntimeClassName -> defineClassRecursively(jcClass)
            else -> loadClass(name)
        }

        val allowInitialize = !isRuntimeLambdaClass && !name.isNotLoadableRuntimeClassName
        if (initialize && allowInitialize)
            Class.forName(loadedClass.name, true, this)

        return loadedClass
    }

    private fun defineClass(name: String, code: ByteArray): Class<*> {
        return defineClass(name, ByteBuffer.wrap(code), null as CodeSource?)
    }

    override fun getResource(name: String?): URL? {
        if (name == null)
            throw NullPointerException()
        try {
            return internalFindResources(name, true)?.nextElement()
        } catch (e: Throwable) {
            error("Failed getting resource ${e.message}")
        }
    }

    override fun findResource(name: String?): URL? = getResource(name)

    override fun getResources(name: String?): Enumeration<URL> {
        if (name == null)
            throw NullPointerException()
        try {
            return internalFindResources(name, false) ?: Collections.emptyEnumeration()
        } catch (e: Throwable) {
            error("Failed getting resources ${e.message}")
        }
    }

    override fun findResources(name: String?): Enumeration<URL> = getResources(name)

    private fun defineClassRecursively(jcClass: JcClassOrInterface): Class<*> =
        defineClassRecursively(jcClass, hashSetOf())
            ?: error("Can't define class $jcClass")

    private val cpWithoutApproximations by lazy { cp.cpWithoutApproximations() }

    private fun getBytecode(jcClass: JcClassOrInterface): ByteArray {
        val instrumentedMethods = jcClass.declaredMethods.filter {
            it.isInstrumentedClinit || it.isInstrumentedInit || it.isInstrumentedInternalInit
                    || it.isInstrumentedGetClassLoader
        }

        if (instrumentedMethods.isEmpty())
            return jcClass.bytecode()

        return jcClass.withAsmNode { asmNode ->
            val name = jcClass.name
            val cp = jcClass.classpath
            val approximations = cp.features?.filterIsInstance<Approximations>()?.singleOrNull()
            for (method in instrumentedMethods) {
                val isApproximated = method is JcEnrichedVirtualMethod
                        || approximations?.findOriginalByApproximationOrNull(ApproximationClassName(name)) != null
                if (isApproximated && asmNode.methods.none { it.name == method.name && it.desc == method.description })
                    continue

                val rawInstList = if (isApproximated) {
                    val newMethod = with(cpWithoutApproximations) { method.withoutApproximations }
                        ?: error("JcConcreteMemoryClassLoader.getBytecode: unable to find original method $method")
                    newMethod.rawInstList
                } else { method.rawInstList }

                val newMethodNode = MethodNodeBuilder(method, rawInstList).build()
                val oldMethodNode = asmNode.methods.find { it.name == method.name && it.desc == method.description }
                asmNode.methods.replace(oldMethodNode, newMethodNode)
            }

            asmNode.toByteArray(cp)
        }
    }

    private fun defineClassRecursively(
        jcClass: JcClassOrInterface,
        visited: MutableSet<JcClassOrInterface>
    ): Class<*>? {
        val className = jcClass.name
        val loaded = findLoadedClass(className)
        if (loaded != null)
            return loaded

        if (!visited.add(jcClass))
            return null

        if (jcClass.declaration.location.isRuntime || jcClass is JcUnknownClass && className.typeIsRuntimeGenerated)
            return super.loadClass(className)

        if (jcClass is JcUnknownClass)
            throw ClassNotFoundException(className)

        val notVisitedSupers = jcClass.allSuperHierarchySequence.filterNot { it in visited }
        notVisitedSupers.forEach { defineClassRecursively(it, visited) }

        val bytecode = getBytecode(jcClass)
        val loadedClass = defineClass(className, bytecode)

        if (loadedClass.typeName == InitHelper::class.java.typeName) initInitHelper(loadedClass)
        if (loadedClass.typeName == ClassLoaderGetHelper::class.java.typeName) initClassLoaderGetHelper(loadedClass)

        return loadedClass
    }
}
