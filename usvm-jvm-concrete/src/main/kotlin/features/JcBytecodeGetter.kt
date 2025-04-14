package features

import machine.JcConcreteMemoryClassLoader
import utils.typeIsRuntimeGenerated
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain

class JcBytecodeGetter : ClassFileTransformer {

    override fun transform(
        loader: ClassLoader?,
        className: String?,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray
    ): ByteArray {
        if (className == null || !className.typeIsRuntimeGenerated || loader !is JcConcreteMemoryClassLoader)
            return classfileBuffer

        JcGeneratedTypesFeature.addGeneratedTypeBytes(className, classfileBuffer)

        return classfileBuffer
    }
}
