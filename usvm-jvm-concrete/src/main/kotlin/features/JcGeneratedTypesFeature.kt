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
import java.util.concurrent.ConcurrentHashMap

// TODO: unify with lambda feature
object JcGeneratedTypesFeature: JcClasspathExtFeature {

    private val generatedTypeBytes = ConcurrentHashMap<String, ByteArray>()

    private val generatedTypes = ConcurrentHashMap<String, JcClassOrInterface>()

    fun addGeneratedTypeBytes(name: String, bytes: ByteArray) {
        generatedTypeBytes[name] = bytes
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
        val bytecode = generatedTypeBytes[name] ?: return null
        val jcClass = generatedTypes.computeIfAbsent(name) {
            defineJcClass(classpath, name, bytecode)
        }

        return AbstractJcResolvedResult.JcResolvedClassResultImpl(name, jcClass)
    }
}
