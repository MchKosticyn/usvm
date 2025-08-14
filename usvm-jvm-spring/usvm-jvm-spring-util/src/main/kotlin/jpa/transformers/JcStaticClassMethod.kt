import jpa.generateVirtualCall
import jpa.generateVoidVirtualCall
import jpa.toArgument
import jpa.transformers.JcBodyFillerFeature
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.ext.toType
import org.usvm.jvm.util.isVoid
import org.usvm.jvm.util.transformers.JcSingleInstructionTransformer.BlockGenerationContext

class JcStaticClassMethod(
    val cp: JcClasspath,
    val newName: String,
    val targetMethod: JcMethod
) : JcBodyFillerFeature() {
    override fun condition(method: JcMethod) =
        method.name == newName && method.isStatic && method.enclosingClass.equals(targetMethod.enclosingClass)

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val obj = method.parameters.first().toArgument
        val args = method.parameters.takeLast(method.parameters.size - 1).map { it.toArgument }
        if (method.isVoid) {
            generateVoidVirtualCall(targetMethod.name, method.enclosingClass.toType(), obj, args)
            addInstruction { loc -> JcReturnInst(loc, null) }
        } else {
            val call = generateVirtualCall("call", targetMethod.name, method.enclosingClass.toType(), obj, args)
            addInstruction { loc -> JcReturnInst(loc, call) }
        }
    }
}
