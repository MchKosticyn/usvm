package machine.interpreter.transformers.springjpa

import org.jacodb.api.jvm.JcClassExtFeature
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.types.JcTypedFieldImpl
import org.jacodb.impl.types.substition.JcSubstitutorImpl
import org.objectweb.asm.Opcodes
import org.usvm.jvm.util.toJcType
import org.usvm.jvm.util.typename
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer
import util.database.JcTableInfoCollector

class JcTableIdClassTransformer(
    collector: JcTableInfoCollector
) : JcClassExtFeature {

    val embeddedIds = collector.getEmbeddedIds().associate { it.embeddedClassName to it }
    val idClasses = collector.getIdClasses().associate { it.classIdName to it }

    override fun methodsOf(clazz: JcClassOrInterface, originalMethods: List<JcMethod>): List<JcMethod>? {
        val embeddedId = embeddedIds[clazz.name]
        val idClasses = idClasses[clazz.name]
        if (embeddedId == null && idClasses == null) return null

        val cp = clazz.classpath
        val fields = listOfNotNull(embeddedId?.idFields, idClasses?.idFields).flatten().map { it.origField }
            .sortedBy(JcField::name)

        val getters = fields.map { field ->
            val getterName = getterName(field)
            val sig = field.signature?.let { "()$it" }
            JcMethodBuilder(clazz)
                .setName(getterName)
                .addBlanckAnnot(GENERATED_GETTER)
                .addBlanckAnnot(field.name)
                .setSig(sig)
                .setRetType(field.type.typeName)
                .addFillerFuture(JcGetterTransformer(cp, field, getterName))
                .buildMethod()
        }

        val setters = fields.map { field ->
            val setterName = setterName(field)
            val sig = field.signature?.let { "($it)V" }
            JcMethodBuilder(clazz)
                .setName(setterName)
                .addBlanckAnnot(GENERATED_SETTER)
                .addBlanckAnnot(field.name)
                .setSig(sig)
                .setRetType(JAVA_VOID)
                .addFillerFuture(JcSetterTransformer(cp, field, setterName))
                .buildMethod()
        }

        val gettersAndSetters = getters + setters

        val initFromIds = getInitFromIds(cp, clazz, fields)
        val buildIds = JcMethodBuilder(clazz)
            .setName(BUILD_IDS)
            .addBlanckAnnot(BUILD_IDS_ANNOT)
            .setAccess(Opcodes.ACC_STATIC)
            .addFreshParam(clazz.typename.typeName)
            .setRetType(JAVA_OBJ_ARR)
            .addFillerFuture(JcBuildIdsTransformer(cp, fields))
            .buildMethod()

        return originalMethods + initFromIds + buildIds + gettersAndSetters +
                gettersAndSetters.map { makeStaticClassMethod(cp, it) }
    }

    private fun getInitFromIds(cp: JcClasspath, clazz: JcClassOrInterface, fields: List<JcField>): JcMethod {
        val builder = JcMethodBuilder(clazz)
            .setName(BUILD_FROM_IDS)
            .addBlanckAnnot(BUILD_FROM_IDS_ANNOT)
            .setAccess(Opcodes.ACC_STATIC)
            .addFreshParam(clazz.typename.typeName)
            .setRetType(clazz.typename.typeName)
            .addFillerFuture(JcInitFromIdsTransformer(cp, clazz, fields))

        fields.forEach { builder.addFreshParam(it.type.typeName) }

        return builder.buildMethod()
    }
}

// static Object[] $buildIds(Entity entity) {
//      val idPart1 = entity.idPart1;
//      val idPart2 = entity.idPart2;
//      val id = new Object[] { idPart1, idPart2 };
//      return id;
// }
private class JcBuildIdsTransformer(
    val cp: JcClasspath,
    val fields: List<JcField>
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) = method.generatedBuildIds

    override fun JcSingleInstructionTransformer.BlockGenerationContext.generateBody(method: JcMethod) {
        val thisVal = method.parameters.single().toArgument

        val objArr = fields.sortedBy(JcField::name).mapIndexed { ix, field ->
            val fieldRef = JcFieldRef(
                thisVal,
                JcTypedFieldImpl(
                    field.enclosingClass.toType(),
                    field,
                    JcSubstitutorImpl()
                )
            )
            val variable = nextLocalVar("field_ref_$ix", field.type.toJcType(cp)!!)
            addInstruction { loc -> JcAssignInst(loc, variable, fieldRef) }
            variable
        }.let { putValuesToObjectArray(cp, "result", it) }

        addInstruction { loc -> JcReturnInst(loc, objArr) }
    }
}

// static Entity $buildFromIds(Type1 id1, Type2 id2) {
//      val newObj = new Entity();
//      newObj.id1 = id1;
//      newObj.id2 = id2;
//      return newObj;
// }
private class JcInitFromIdsTransformer(
    val cp: JcClasspath,
    val clazz: JcClassOrInterface,
    val fields: List<JcField>
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) = method.generatedBuildFromIds

    override fun JcSingleInstructionTransformer.BlockGenerationContext.generateBody(method: JcMethod) {
        val newObj = generateNewWithInit("result", clazz.toType(), emptyList())

        fields.sortedBy(JcField::name).forEachIndexed { ix, field ->
            val arg = method.parameters[ix].toArgument // it is sorted by name (same as fields)
            val fieldRef = JcFieldRef(
                newObj,
                JcTypedFieldImpl(
                    field.enclosingClass.toType(),
                    field,
                    JcSubstitutorImpl()
                )
            )

            addInstruction { loc -> JcAssignInst(loc, fieldRef, arg) }
        }

        addInstruction { loc -> JcReturnInst(loc, newObj) }
    }
}
