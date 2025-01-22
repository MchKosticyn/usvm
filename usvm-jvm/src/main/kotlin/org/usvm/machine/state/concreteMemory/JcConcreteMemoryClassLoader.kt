package org.usvm.api.util

import bench.JcLambdaFeature
import bench.replace
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.allSuperHierarchySequence
import org.jacodb.impl.cfg.MethodNodeBuilder
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.usvm.api.internal.ClinitHelper
import org.usvm.instrumentation.util.toByteArray
import org.usvm.machine.state.concreteMemory.JcConcreteEffectStorage
import org.usvm.machine.state.concreteMemory.isInstrumentedClinit
import org.usvm.machine.state.concreteMemory.isLambdaTypeName
import org.usvm.machine.state.concreteMemory.javaName
import org.usvm.machine.state.concreteMemory.setStaticFieldValue
import org.usvm.machine.state.concreteMemory.staticFields
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.security.CodeSource
import java.security.SecureClassLoader
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Loads known classes using [ClassLoader.getSystemClassLoader], or defines them using bytecode from jacodb if they are unknown.
 */
// TODO: make this 'class'
internal object JcConcreteMemoryClassLoader : SecureClassLoader(ClassLoader.getSystemClassLoader()) {

    var webApplicationClass: JcClassOrInterface? = null
    lateinit var cp: JcClasspath
    private val loadedClasses = hashMapOf<String, Class<*>>()
    private val initializedStatics = hashSetOf<Class<*>>()
    private lateinit var effectStorage: JcConcreteEffectStorage

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
        val jarPath = "jar:file:${locFile.absolutePath}!"
        if (single) {
            for (current in jar.entries()) {
                if (current.matchResource(name, true))
                    return listOf(URL("$jarPath/${current.name}"))
            }
        } else {
            val result = jar.entries().toList().mapNotNull {
                if (it.matchResource(name, false))
                    URL("$jarPath/${it.name}")
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

    fun setEffectStorage(effectStorage: JcConcreteEffectStorage) {
        this.effectStorage = effectStorage
    }

    fun initializedStatics(): Set<Class<*>> {
        return initializedStatics
    }

    private fun doLoadClass(name: String): Class<*> {
        if (name.isLambdaTypeName)
            return JcLambdaFeature.lambdaClassByName(name) ?: super.loadClass(name)

        val jcClass = cp.findClassOrNull(name) ?: throw ClassNotFoundException(name)

        if (jcClass.declaration.location.isRuntime)
            return super.loadClass(name)

        return loadClass(jcClass)
    }

    private val afterClinitAction: java.util.function.Function<String, Void?> =
        java.util.function.Function { className: String ->
            val clazz = loadedClasses[className]!!
            initializedStatics.add(clazz)
            effectStorage.addStatics(clazz)
            null
        }

    private fun initClinitHelper(type: Class<*>) {
        check(type.name == ClinitHelper::class.java.name)
        type.staticFields
            .find { it.name == ClinitHelper::afterClinitAction.javaName }!!
            .setStaticFieldValue(afterClinitAction)
    }

    override fun loadClass(name: String?): Class<*> {
        if (name == null)
            throw ClassNotFoundException()

        val loadedClass = loadedClasses[name]
        if (loadedClass != null)
            return loadedClass

        val type = doLoadClass(name)
        loadedClasses[name] = type

        if (name == ClinitHelper::class.java.name) {
            initClinitHelper(type)
        }

        return type
    }

    fun isLoaded(jcClass: JcClassOrInterface): Boolean {
        return loadedClasses.containsKey(jcClass.name)
    }

    fun loadClass(jcClass: JcClassOrInterface): Class<*> = defineClassRecursively(jcClass)

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

    private fun getBytecode(jcClass: JcClassOrInterface): ByteArray {
        val clinit = jcClass.declaredMethods.find { it.isClassInitializer }
        if (clinit != null && clinit.isInstrumentedClinit) {
            val newClinitNode = MethodNodeBuilder(clinit, clinit.rawInstList).build()
            return jcClass.withAsmNode { asmNode ->
                val clinitNode = asmNode.methods.find { it.name == clinit.name }
                asmNode.methods.replace(clinitNode, newClinitNode)
                asmNode.toByteArray(jcClass.classpath)
            }
        }

        return jcClass.bytecode()
    }

    private fun defineClassRecursively(
        jcClass: JcClassOrInterface,
        visited: MutableSet<JcClassOrInterface>
    ): Class<*>? {
        if (!visited.add(jcClass)) {
            return null
        }

        if (jcClass.declaration.location.isRuntime || typeIsRuntimeGenerated(jcClass)) {
            val name = jcClass.name
            val type = super.loadClass(name)
            loadedClasses[name] = type
            return type
        }

        if (jcClass is JcUnknownClass) {
            throw ClassNotFoundException(jcClass.name)
        }

        with(jcClass) {
            // For unknown class we need to load all its supers, all classes mentioned in its ALL (not only declared)
            // fields (as they are used in resolving), and then define the class itself using its bytecode from jacodb

            val notVisitedSupers = allSuperHierarchySequence.filterNot { it in visited }
            notVisitedSupers.forEach { defineClassRecursively(it, visited) }

            return loadedClasses.getOrPut(name) {
                val bytecode = getBytecode(jcClass)
                defineClass(name, bytecode)
            }
        }
    }
}
