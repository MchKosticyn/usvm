package features

import org.jacodb.api.jvm.ClassSource
import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcClasspathExtFeature
import org.jacodb.api.jvm.JcClasspathExtFeature.JcResolvedClassResult
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.impl.bytecode.JcClassOrInterfaceImpl
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.features.classpaths.AbstractJcResolvedResult
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import utils.isLambdaRealName

object JcGeneratedTypesFeature: JcClasspathExtFeature {

    private val generatedTypeBytes = ConcurrentHashMap<String, ByteArray>()

    private val generatedTypes = HashMap<String, JcClassOrInterface>()

    private val hiddenClasses = HashMap<String, Class<*>>()

    fun addGeneratedTypeBytes(name: String, bytes: ByteArray) {
        generatedTypeBytes[name] = bytes
    }

    fun addHiddenClass(name: String, clazz: Class<*>) {
        hiddenClasses[name] = clazz
    }

    fun getHiddenClass(name: String): Class<*>? {
        return hiddenClasses[name]
    }

    private object GeneratedLocation: RegisteredLocation {
        override val jcLocation: JcByteCodeLocation? get() = null
        override val id: Long get() = -2
        override val path: String = "generated"
        override val isRuntime: Boolean get() = false
    }

    private class GeneratedClassSource(
        override val location: RegisteredLocation,
        override val className: String,
        override val byteCode: ByteArray,
    ) : ClassSource

    private fun defineJcClass(cp: JcClasspath, name: String, bytes: ByteArray): JcClassOrInterface {
        val source = GeneratedClassSource(GeneratedLocation, name, bytes)
        val featuresChainField = cp.javaClass.getDeclaredField("featuresChain")
        featuresChainField.isAccessible = true
        val featuresChain = featuresChainField.get(cp) as JcFeaturesChain
        return JcClassOrInterfaceImpl(cp, source, featuresChain)
    }

    override fun tryFindClass(classpath: JcClasspath, name: String): JcResolvedClassResult? {
        val jcdbClassName = name.replace('/', '.')
        val existingJcClass = generatedTypes[jcdbClassName]
        if (existingJcClass != null)
            return AbstractJcResolvedResult.JcResolvedClassResultImpl(name, existingJcClass)

        if (jcdbClassName.isLambdaRealName) {
            val bytecode = LambdaBytecodeProvider.instance.forName(jcdbClassName) ?: return null
            val jcClass = defineJcClass(classpath, jcdbClassName, bytecode)
            generatedTypes[jcdbClassName] = jcClass
            return AbstractJcResolvedResult.JcResolvedClassResultImpl(name, jcClass)
        }

        val bytecode = generatedTypeBytes[name] ?: return null
        val jcClass = defineJcClass(classpath, name, bytecode)
        generatedTypes[name] = jcClass
        return AbstractJcResolvedResult.JcResolvedClassResultImpl(name, jcClass)
    }
}

abstract class LambdaBytecodeProvider {

    companion object {

        val currentJavaMajorVersion: Int =
            System.getProperty("java.version").split(".").firstOrNull()?.toIntOrNull() ?: error("cannot parse java.version")

        val lambdaTypeNameIdentifier: String = if (currentJavaMajorVersion > 20) "\$\$Lambda" else "\$\$Lambda\$"

        val instance: LambdaBytecodeProvider by lazy {
            if (currentJavaMajorVersion <= 20)
                ByCanonicalName()
            else
                ByRealName()
        }
    }

    protected val lambdaDir by lazy {
        val dir = File(System.getenv("lambdaDir"))
        check(dir.exists() && dir.isDirectory) {
            "lambda dir does not exist"
        }
        dir
    }

    protected fun replaceCanonicalNameWith(jcdbResolvableName: String, bytes: ByteArray): ByteArray {
        val reader = ClassReader(bytes)
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        val canonicalName = jcdbResolvableName.substringBeforeLast('.')
        val remapper = ClassRemapper(writer, SimpleRemapper(canonicalName, jcdbResolvableName))
        reader.accept(remapper, ClassReader.EXPAND_FRAMES)
        return writer.toByteArray()
    }

    protected fun String.toAsmLambdaName() = "${substringBeforeLast('.').replace('.', '/')}.${substringAfterLast('.')}"

    abstract fun forName(jcdbRuntimeName: String): ByteArray?

    private class ByRealName: LambdaBytecodeProvider() {
        override fun forName(jcdbRuntimeName: String): ByteArray? {
            val resolvableName = jcdbRuntimeName.toAsmLambdaName()

            val file = lambdaDir.resolve("$resolvableName.class")

            return if (file.exists()) replaceCanonicalNameWith(resolvableName, file.readBytes()) else null
        }
    }

    private class ByCanonicalName: LambdaBytecodeProvider() {
        override fun forName(jcdbRuntimeName: String): ByteArray? {
            val resolvableName = jcdbRuntimeName.toAsmLambdaName()
            val fileName = resolvableName.substringBeforeLast(".")
            val file = lambdaDir.resolve("$fileName.class")
            return if (file.exists()) replaceCanonicalNameWith(resolvableName, file.readBytes()) else null
        }
    }
}

