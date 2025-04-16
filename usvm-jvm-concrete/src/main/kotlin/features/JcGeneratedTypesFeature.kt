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
import utils.isLambdaTypeName
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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

    private fun getLambdaByteCode(className: String): ByteArray? {
        val lambdaDir = File(System.getenv("lambdaDir"))
        check(lambdaDir.exists())
        val file = lambdaDir.resolve(className.replace('.', '/') + ".class")
        return if (file.exists()) file.readBytes() else null
    }

    private fun getLambdaCanonicalTypeName(typeName: String): String {
        check(typeName.isLambdaTypeName)
        return typeName.split('/')[0]
    }

    private val String.isLambdaRealName: Boolean get() =
        isLambdaTypeName && split('/').size == 2

    override fun tryFindClass(classpath: JcClasspath, name: String): JcResolvedClassResult? {
        val existingJcClass = generatedTypes[name]
        if (existingJcClass != null)
            return AbstractJcResolvedResult.JcResolvedClassResultImpl(name, existingJcClass)

        if (name.isLambdaTypeName) {
            check(name.isLambdaRealName)
            val canonicalName = getLambdaCanonicalTypeName(name)
            val bytecode = getLambdaByteCode(canonicalName) ?: return null
            val jcClass = defineJcClass(classpath, name, bytecode)
            generatedTypes[canonicalName] = jcClass
            generatedTypes[name] = jcClass
            return AbstractJcResolvedResult.JcResolvedClassResultImpl(name, jcClass)
        }

        val bytecode = generatedTypeBytes[name] ?: return null
        val jcClass = defineJcClass(classpath, name, bytecode)
        return AbstractJcResolvedResult.JcResolvedClassResultImpl(name, jcClass)
    }
}
