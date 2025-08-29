package machine.interpreter.transformers.springjpa

import JcFieldBuilder
import JcMethodBuilder
import jpa.JcTableInfoCollector
import getterName
import jpa.BUILD_ID_ANNOT
import jpa.BUILD_ID_NAME
import jpa.CHECK_FIELD_ANNOT
import jpa.COPY_ANNOT
import jpa.COPY_NAME
import jpa.DELETE_ANNOT
import jpa.DELETE_NAME
import jpa.DTO_INFO
import jpa.GENERATED_GETTER
import jpa.GENERATED_SETTER
import jpa.GET_DTO_ANNOT
import jpa.GET_DTO_NAME
import jpa.GET_ID_ANNOT
import jpa.GET_ID_NAME
import jpa.IdColumnInfo
import jpa.JAVA_OBJ_ARR
import jpa.JAVA_VOID
import jpa.RELATIONS_INIT_ANNOT
import jpa.RELATIONS_INIT_NAME
import jpa.SAVE_UPDATE_ANNOT
import jpa.SAVE_UPDATE_NAME
import jpa.SAVE_UPD_DEL_CTX
import jpa.SET_ID_ANNOT
import jpa.SET_ID_NAME
import jpa.STATIC_BLANK_INIT_ANNOT
import jpa.STATIC_BLANK_INIT_NAME
import jpa.TableInfo
import jpa.isDataClass
import jpa.makeStaticClassMethod
import setterName
import org.jacodb.api.jvm.JcClassExtFeature
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.objectweb.asm.Opcodes
import org.usvm.jvm.util.typename

// Map wrapper where key is combination of className and fieldName
// Used to store simple fields that represent relation from key field of key class
class RelationMap<T> {
    private val data: MutableMap<String, Set<T>> = hashMapOf()

    private fun combName(clazzName: String, fieldName: String) = "${clazzName}_$fieldName"
    private fun combName(clazz: JcClassOrInterface, field: JcField) = combName(clazz.name, field.name)
    private fun combName(clazz: JcClassOrInterface, fieldName: String) = combName(clazz.name, fieldName)

    fun get(clazzName: String, fieldName: String) = data[combName(clazzName, fieldName)]
        ?: error("cannot get from relation map by ${combName(clazzName, fieldName)}")

    fun get(clazz: JcClassOrInterface, fieldName: String) = get(clazz.name, fieldName)

    fun get(clazz: JcClassOrInterface, field: JcField) = get(clazz.name, field.name)

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
                .addBlancAnnot(CHECK_FIELD_ANNOT)
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

        return functions + functions.map { makeStaticClassMethod(cp, it) } +
                getStaticBlankInit() +
                getGetDTO() +
                getSaveUpdate(relationChecks) +
                getDelete(relationChecks)
    }

    fun getStaticBlankInit() =
        JcMethodBuilder(clazz)
            .setName(STATIC_BLANK_INIT_NAME)
            .addBlancAnnot(STATIC_BLANK_INIT_ANNOT)
            .setAccess(Opcodes.ACC_STATIC)
            .setRetType(clazz.typename.typeName)
            .addFillerFuture(JcStaticBlankInitTransformer())
            .buildMethod()

    fun getRelationsInit() =
        JcMethodBuilder(clazz)
            .setName(RELATIONS_INIT_NAME)
            .addBlancAnnot(RELATIONS_INIT_ANNOT)
            .setRetType(JAVA_VOID)
            .addFillerFuture(JcRelationsInitTransformer(dataclassTransformer, relationChecks, cp, classTable))
            .buildMethod()

    fun getCopy() =
        JcMethodBuilder(clazz)
            .setName(COPY_NAME)
            .addBlancAnnot(COPY_ANNOT)
            .setRetType(clazz.name)
            .addFillerFuture(JcCopyTransformer(cp, clazz, collector))
            .buildMethod()

    fun getSpecialGetId(idField: JcField): JcMethod {
        check(idColumn is IdColumnInfo.SingleId)
        return JcMethodBuilder(clazz)
            .setName(GET_ID_NAME)
            .addBlancAnnot(GET_ID_ANNOT)
            .setRetType(idColumn.type.typeName)
            .addFillerFuture(JcSpecialGetIdTransformer(cp, idField))
            .buildMethod()
    }

    fun getSpecialSetId(idField: JcField): JcMethod {
        check(idColumn is IdColumnInfo.SingleId)
        return JcMethodBuilder(clazz)
            .setName(SET_ID_NAME)
            .addBlancAnnot(SET_ID_ANNOT)
            .addFreshParam(idColumn.type.typeName)
            .setRetType(JAVA_VOID)
            .addFillerFuture(JcSpecialSetIdTransformer(cp, idField))
            .buildMethod()
    }

    fun getBuildId() =
        JcMethodBuilder(clazz)
            .setName(BUILD_ID_NAME)
            .addBlancAnnot(BUILD_ID_ANNOT)
            .setRetType(JAVA_OBJ_ARR)
            .addFillerFuture(JcBuildIdTransformer(cp, classTable))
            .buildMethod()

    fun getGetDTO() =
        JcMethodBuilder(clazz)
            .setName(GET_DTO_NAME)
            .addBlancAnnot(GET_DTO_ANNOT)
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
                .addBlancAnnot(GENERATED_GETTER)
                .addBlancAnnot(field.name)
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
                .addBlancAnnot(GENERATED_SETTER)
                .addBlancAnnot(field.name)
                .setRetType(JAVA_VOID)
                .setSig(sig)
                .addFreshParam(field.type.typeName)
                .addFillerFuture(JcSetterTransformer(cp, field, name))
                .buildMethod()
        }

    fun getSaveUpdate(relationChecks: RelationMap<JcField>) =
        JcMethodBuilder(clazz)
            .setName(SAVE_UPDATE_NAME)
            .addBlancAnnot(SAVE_UPDATE_ANNOT)
            .setAccess(Opcodes.ACC_STATIC)
            .setRetType(JAVA_VOID)
            .addFillerFuture(JcSaveUpdateTransformer(collector, cp, relationChecks, classTable, clazz))
            .addFreshParam(clazz.name)
            .addFreshParam(SAVE_UPD_DEL_CTX)
            .buildMethod()

    fun getDelete(relationChecks: RelationMap<JcField>) =
        JcMethodBuilder(clazz)
            .setName(DELETE_NAME)
            .addBlancAnnot(DELETE_ANNOT)
            .setAccess(Opcodes.ACC_STATIC)
            .setRetType(JAVA_VOID)
            .addFillerFuture(JcDeleteTransformer(collector, cp, relationChecks, classTable, clazz))
            .addFreshParam(clazz.name)
            .addFreshParam(SAVE_UPD_DEL_CTX)
            .buildMethod()
}
