package machine.interpreter.transformers.springjpa

import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcMethodExtFeature
import org.jacodb.api.jvm.JcMethodExtFeature.JcInstListResult
import org.jacodb.api.jvm.JcParameter
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.ext.jvmName
import org.jacodb.approximation.JcEnrichedVirtualParameter
import org.jacodb.impl.bytecode.JcAnnotationImpl
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethodImpl
import org.jacodb.impl.types.AnnotationInfo
import org.jacodb.impl.types.ParameterInfo
import org.objectweb.asm.Opcodes
import org.usvm.jvm.util.typeName
import util.database.blancAnnotation

fun getterName(field: JcField) = "\$get_${field.name}"

fun setterName(field: JcField) = "\$set_${field.name}"

fun toAnnotation(cp: JcClasspath, info: AnnotationInfo) = JcAnnotationImpl(info, cp)

fun toEnriched(cp: JcClasspath, info: ParameterInfo) = with(info) {
    JcEnrichedVirtualParameter(index, type.typeName, name, annotations.map { toAnnotation(cp, it) }, access)
}

fun toEnriched(param: JcParameter) = with(param) {
    JcEnrichedVirtualParameter(index, type, name, annotations, access)
}

class JcJpaMethod(
    name: String,
    access: Int = Opcodes.ACC_PUBLIC,
    returnType: TypeName,
    parameters: List<JcEnrichedVirtualParameter>,
    description: String,
    override val signature: String?,
    private val featuresChain: JcFeaturesChain,
    override val annotations: List<JcAnnotation>,
) : JcVirtualMethodImpl(name, access, returnType, parameters, description) {

    override val instList: JcInstList<JcInst>
        get() = featuresChain.call<JcMethodExtFeature, JcInstListResult> {
            it.instList(this)
        }!!.instList

    companion object {
        fun of(method: JcMethod, clazz: JcClassOrInterface = method.enclosingClass) = with(method) {
            JcJpaMethod(
                name,
                access,
                returnType,
                parameters.map(::toEnriched),
                description,
                signature,
                JcFeaturesChain(clazz.classpath.features!!),
                annotations
            ).also { it.bind(clazz) }
        }
    }
}

class JcMethodBuilder(
    val clazz: JcClassOrInterface
) {
    val cp = clazz.classpath
    private val paramsBuilder = JcParamBuilder()

    private val features = cp.features!!.toMutableList()

    private var name: String? = null
    private var sig: String? = null
    private var retType: TypeName? = null
    private var access = Opcodes.ACC_PUBLIC
    private val annots = mutableListOf<AnnotationInfo>()
    private val params = mutableListOf<ParameterInfo>()

    fun addFillerFuture(feature: JcMethodExtFeature) = this.also { it.features.add(0, feature) }

    fun setName(name: String) = this.also { it.name = name }

    fun setSig(sig: String?) = this.also { it.sig = sig }

    fun setRetType(type: TypeName) = this.also { it.retType = type }

    fun setRetType(type: String) = this.also { it.retType = type.typeName }

    fun setAccess(access: Int) = this.also { it.access = access }

    fun addAnnot(annot: AnnotationInfo) = this.also { annots.add(annot) }

    fun addBlanckAnnot(name: String) = this.addAnnot(blancAnnotation(name))

    fun addParam(param: ParameterInfo) = this.also { it.params.add(param) }

    fun addFreshParam(type: String) = addParam(paramsBuilder.setType(type).buildParam())

    private fun buildDesc() = buildString {
        append("(")
        params.forEach {
            append(it.type.jvmName())
        }
        append(")")
        append(retType!!.typeName.jvmName())
    }

    fun buildMethod() = JcJpaMethod(
        name!!,
        access,
        retType!!,
        params.map { toEnriched(cp, it) },
        buildDesc(),
        sig,
        JcFeaturesChain(features),
        annots.map { toAnnotation(cp, it) }
    ).also { it.bind(clazz) } as JcMethod
}

class JcParamBuilder {

    private var type: String? = null
    private var ix = 0
    private var access = Opcodes.ACC_PUBLIC
    private var name: String? = null
    private val annots = mutableListOf<AnnotationInfo>()

    fun setType(type: String) = this.also { it.type = type }

    fun setIndex(ix: Int) = this.also { it.ix = ix }

    fun setAccess(access: Int) = this.also { it.access = access }

    fun setName(name: String) = this.also { it.name = name }

    fun addAnnot(annot: AnnotationInfo) = this.also { it.annots.add(annot) }

    fun buildParam() = ParameterInfo(type!!, ix++, access, name ?: "\$p$ix", annots)
}
