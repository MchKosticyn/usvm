package features

import org.jacodb.api.jvm.ClassSource
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcClasspathExtFeature
import org.jacodb.api.jvm.JcClasspathExtFeature.JcResolvedClassResult
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.api.jvm.throwClassNotFound
import org.jacodb.impl.bytecode.JcClassOrInterfaceImpl
import org.jacodb.impl.features.classpaths.AbstractJcResolvedResult
import org.usvm.jvm.util.toClassNode
import org.usvm.jvm.util.write
import utils.allInstanceFields
import utils.getFieldValue
import java.io.File

// TODO: unify with lambda feature
object JcGeneratedTypesFeature: JcClasspathExtFeature {

    private val generatedTypeBytes = hashMapOf<String, ByteArray>()

    private val generatedTypes = hashMapOf<String, JcClassOrInterface>()

    fun addGeneratedTypeBytes(name: String, bytes: ByteArray) {
        generatedTypeBytes[name] = bytes
    }

    private class GeneratedClassSource(
        override val location: RegisteredLocation,
        override val className: String
    ) : ClassSource {
        override val byteCode by lazy {
            location.jcLocation?.resolve(className) ?: className.throwClassNotFound()
        }
    }

    private fun defineJcClass(cp: JcClasspath, name: String, bytes: ByteArray): JcClassOrInterface {
        // TODO: add dynamic load of classes into jacodb
        val db = cp.db
        val vfs = db.javaClass.allInstanceFields.find { it.name == "classesVfs" }!!.getFieldValue(db)!!
        val generatedTypesDir = System.getenv("generatedTypesDir")
        val generatedTypesLoc = cp.registeredLocations.find {
            it.jcLocation?.jarOrFolder?.absolutePath == generatedTypesDir
        }!!
        val addMethod = vfs.javaClass.methods.find { it.name == "addClass" }!!
        val classNode = bytes.toClassNode()
        check(name == classNode.name)
        val generatedTypesDirFile = File(generatedTypesDir)
        val generatedClassPath = generatedTypesDirFile.resolve("$name.class").toPath()
        classNode.write(cp, generatedClassPath, checkClass = true)

        val source = GeneratedClassSource(generatedTypesLoc, name)
        addMethod.invoke(vfs, source)

        // TODO: cycles?
        val type = cp.findClassOrNull(name)
        check(type is JcClassOrInterfaceImpl)
        return type
    }

    override fun tryFindClass(classpath: JcClasspath, name: String): JcResolvedClassResult? {
        val bytecode = generatedTypeBytes[name] ?: return null
        val jcClass = generatedTypes.getOrPut(name) {
            defineJcClass(classpath, name, bytecode)
        }

        return AbstractJcResolvedResult.JcResolvedClassResultImpl(name, jcClass)
    }
}
