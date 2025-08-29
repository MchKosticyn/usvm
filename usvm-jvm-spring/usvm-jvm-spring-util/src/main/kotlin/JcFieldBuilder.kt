import jpa.blancAnnotation
import jpa.dummyAnnot
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcField
import org.jacodb.impl.bytecode.JcFieldImpl
import org.jacodb.impl.types.AnnotationInfo
import org.jacodb.impl.types.FieldInfo
import org.objectweb.asm.Opcodes

class JcFieldBuilder(val clazz: JcClassOrInterface) {

    private var name: String? = null
    private var sig: String? = null
    private var access = Opcodes.ACC_PUBLIC
    private var type: String? = null
    private var annots = mutableListOf<AnnotationInfo>()

    fun setName(name: String) = this.also { it.name = name }

    fun setSig(sig: String) = this.also { it.sig = sig }

    fun setAccess(access: Int) = this.also { it.access = access }

    fun setType(type: String) = this.also { it.type = type }

    fun addAnnotation(annot: AnnotationInfo) = this.also { it.annots.add(annot) }

    fun addBlancAnnot(name: String) = addAnnotation(blancAnnotation(name))

    fun addDummyFieldAnnot() = addAnnotation(dummyAnnot)

    fun buildField(): JcField {
        val info = FieldInfo(
            name!!,
            sig,
            access,
            type!!,
            annots
        )
        return JcFieldImpl(clazz, info)
    }
}
