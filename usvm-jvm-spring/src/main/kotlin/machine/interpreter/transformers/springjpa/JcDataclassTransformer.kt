package machine.interpreter.transformers.springjpa

import org.jacodb.api.jvm.JcClassExtFeature
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.objectweb.asm.Opcodes
import org.usvm.jvm.util.typename
import util.database.IdColumnInfo
import util.database.JcTableInfoCollector
import util.database.TableInfo

class RelationMap<T> {
    private val data: MutableMap<String, Set<T>> = hashMapOf()

    private fun combName(clazz: JcClassOrInterface, field: JcField) = "${clazz.name}_${field.name}"

    fun get(clazz: JcClassOrInterface, field: JcField) = data[combName(clazz, field)]!!
    fun add(clazz: JcClassOrInterface, field: JcField, t: T) {
        val name = combName(clazz, field)
        val value = data.getOrDefault(name, emptySet())
        data[name] = value.plus(t)
    }
}

class JcDataclassTransformer(
    val collector: JcTableInfoCollector,
    val isNeedTrackTable: Boolean
) : JcClassExtFeature {

    private val relationChecks = RelationMap<JcField>()

    override fun fieldsOf(clazz: JcClassOrInterface, originalFields: List<JcField>): List<JcField>? {
        if (!clazz.isDataClass) return null

        val fields = originalFields.toMutableList()
        val classTable = collector.getTable(clazz) ?: return null

        classTable.columnsInOrder().filter { !it.isOrig }.forEach { col ->
            val name = "\$${col.name}_id_check"
            val field = JcFieldBuilder(clazz)
                .setName(name)
                .addDummyFieldAnnot()
                .setType(col.type.typeName)
                .addBlanckAnnot(CHECK_FIELD_ANNOT)
                .buildField()

            relationChecks.add(clazz, col.origField, field)
            fields.add(field)
        }

        return fields
    }

    override fun methodsOf(clazz: JcClassOrInterface, originalMethods: List<JcMethod>): List<JcMethod>? {
        if (!clazz.isDataClass) return null

        val cp = clazz.classpath
        val classTable = collector.getTable(clazz) ?: return null
        val generator = SignatureGenerator(this, cp, collector, clazz, classTable, isNeedTrackTable, relationChecks)

        return originalMethods + generator.getFunctions()
    }
}

private class SignatureGenerator(
    val dataclassTransformer: JcDataclassTransformer,
    val cp: JcClasspath,
    val collector: JcTableInfoCollector,
    val clazz: JcClassOrInterface,
    val classTable: TableInfo.TableWithIdInfo,
    val isNeedTrackTable: Boolean,
    val relationChecks: RelationMap<JcField>
) {

    val idColumn = classTable.idColumn

    fun getFunctions(): List<JcMethod> {
        val functions = mutableListOf(
            getRelationsInit(),
            getCopy(),
            getBuildId()
        )
        functions.addAll(getters() + setters())

        if (idColumn is IdColumnInfo.SingleId) {
            val idField = idColumn.origField
            functions.add(getSpecialGetId(idField))
            functions.add(getSpecialSetId(idField))
        }

        return functions + functions.map { makeStaticClassMethod(cp, clazz, it) } +
                getStaticBlankInit() +
                getGetDTO() +
                getSaveUpdate(relationChecks) +
                getDelete(relationChecks)
    }

    fun getStaticBlankInit() =
        JcMethodBuilder(clazz)
            .setName(STATIC_BLANK_INIT_NAME)
            .addBlanckAnnot(STATIC_BLANK_INIT_ANNOT)
            .setAccess(Opcodes.ACC_STATIC)
            .setRetType(clazz.typename.typeName)
            .addFillerFuture(JcStaticBlankInitTransformer())
            .buildMethod()

    fun getRelationsInit() =
        JcMethodBuilder(clazz)
            .setName(RELATIONS_INIT_NAME)
            .addBlanckAnnot(RELATIONS_INIT_ANNOT)
            .setRetType(JAVA_VOID)
            .addFillerFuture(JcRelationsInitTransformer(dataclassTransformer, relationChecks, cp, classTable))
            .buildMethod()

    fun getCopy() =
        JcMethodBuilder(clazz)
            .setName(COPY_NAME)
            .addBlanckAnnot(COPY_ANNOT)
            .setRetType(clazz.name)
            .addFillerFuture(JcCopyTransformer(cp, clazz, collector))
            .buildMethod()

    fun getSpecialGetId(idField: JcField): JcMethod {
        check(idColumn is IdColumnInfo.SingleId)
        return JcMethodBuilder(clazz)
            .setName(GET_ID_NAME)
            .addBlanckAnnot(GET_ID_ANNOT)
            .setRetType(idColumn.type.typeName)
            .addFillerFuture(JcSpecialGetIdTransformer(cp, idField))
            .buildMethod()
    }

    fun getSpecialSetId(idField: JcField): JcMethod {
        check(idColumn is IdColumnInfo.SingleId)
        return JcMethodBuilder(clazz)
            .setName(SET_ID_NAME)
            .addBlanckAnnot(SET_ID_ANNOT)
            .addFreshParam(idColumn.type.typeName)
            .setRetType(JAVA_VOID)
            .addFillerFuture(JcSpecialSetIdTransformer(cp, idField))
            .buildMethod()
    }

    fun getBuildId() =
        JcMethodBuilder(clazz)
            .setName(BUILD_ID_NAME)
            .addBlanckAnnot(BUILD_ID_ANNOT)
            .setRetType(JAVA_OBJ_ARR)
            .addFillerFuture(JcBuildIdTransformer(cp, classTable))
            .buildMethod()

    fun getGetDTO() =
        JcMethodBuilder(clazz)
            .setName(GET_DTO_NAME)
            .addBlanckAnnot(GET_DTO_ANNOT)
            .setAccess(Opcodes.ACC_STATIC)
            .setRetType(DTO_INFO)
            .addFillerFuture(JcGetDTOTransformer(cp, clazz, classTable, isNeedTrackTable))
            .buildMethod()

    fun getters() =
        collector.collectFields(clazz) { !it.isStatic }.map { field ->
            val name = getterName(field)
            val sig = field.signature?.let { "()$it" }
            JcMethodBuilder(clazz)
                .setName(name)
                .addBlanckAnnot(GENERATED_GETTER)
                .addBlanckAnnot(field.name)
                .setSig(sig)
                .setRetType(field.type.typeName)
                .addFillerFuture(JcGetterTransformer(cp, field, name))
                .buildMethod()
        }

    fun setters() =
        collector.collectFields(clazz) { !it.isStatic }.map { field ->
            val name = setterName(field)
            val sig = field.signature?.let { "($it)V" }
            JcMethodBuilder(clazz)
                .setName(name)
                .addBlanckAnnot(GENERATED_SETTER)
                .addBlanckAnnot(field.name)
                .setRetType(JAVA_VOID)
                .setSig(sig)
                .addFreshParam(field.type.typeName)
                .addFillerFuture(JcSetterTransformer(cp, field, name))
                .buildMethod()
        }

    fun getSaveUpdate(relationChecks: RelationMap<JcField>) =
        JcMethodBuilder(clazz)
            .setName(SAVE_UPDATE_NAME)
            .addBlanckAnnot(SAVE_UPDATE_ANNOT)
            .setAccess(Opcodes.ACC_STATIC)
            .setRetType(JAVA_VOID)
            .addFillerFuture(JcSaveUpdateTransformer(collector, cp, relationChecks, classTable, clazz))
            .addFreshParam(clazz.name)
            .addFreshParam(SAVE_UPD_DEL_CTX)
            .buildMethod()

    fun getDelete(relationChecks: RelationMap<JcField>) =
        JcMethodBuilder(clazz)
            .setName(DELETE_NAME)
            .addBlanckAnnot(DELETE_ANNOT)
            .setAccess(Opcodes.ACC_STATIC)
            .setRetType(JAVA_VOID)
            .addFillerFuture(JcDeleteTransformer(collector, cp, relationChecks, classTable, clazz))
            .addFreshParam(clazz.name)
            .addFreshParam(SAVE_UPD_DEL_CTX)
            .buildMethod()
}
