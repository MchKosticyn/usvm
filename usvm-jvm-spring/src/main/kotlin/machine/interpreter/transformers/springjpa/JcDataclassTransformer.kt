package machine.interpreter.transformers.springjpa

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.jacodb.api.jvm.JcClassExtFeature
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.jvmName
import org.jacodb.api.jvm.ext.toType
import org.objectweb.asm.Opcodes
import org.usvm.jvm.util.jvmDescriptor
import org.usvm.jvm.util.typename
import util.database.JcTableInfoCollector
import util.database.Relation
import util.database.TableInfo
import util.database.getSetFieldName

class RelationMap<T> {
    private val data: MutableMap<String, T> = hashMapOf()

    private fun combName(clazz: JcClassOrInterface, field: JcField) = "${clazz.name}_${field.name}"

    fun get(clazz: JcClassOrInterface, field: JcField): T = data[combName(clazz, field)]!!
    fun set(clazz: JcClassOrInterface, field: JcField, t: T) {
        data[combName(clazz, field)] = t
    }
}

class JcDataclassTransformer(
    val collector: JcTableInfoCollector
) : JcClassExtFeature {

    val relationLambdas = RelationMap<List<JcMethod>>()
    val relationSets = RelationMap<JcField>()
    val relationChecks = RelationMap<JcField>()

    override fun fieldsOf(clazz: JcClassOrInterface, originalFields: List<JcField>): List<JcField>? {
        if (!clazz.isDataClass) return null

        val fields = originalFields.toMutableList()
        val classTable = collector.getTable(clazz) ?: return null

        classTable.relations.filterIsInstance<Relation.RelationByTable>().forEach { rel ->
            val subTable = collector.findSubTable(rel.origField)!!
            val subIdType = subTable.idColumn.type.typeName

            val desc = JAVA_SET.jvmName()
            val sig = "${desc.dropLast(1)}<${subIdType.jvmName()}>;"

            val field = JcFieldBuilder(clazz)
                .setName(getSetFieldName(rel))
                .setSig(sig)
                .addDummyFieldAnnot()
                .setType(JAVA_SET)
                .buildField()

            relationSets.set(clazz, rel.origField, field)
            fields.add(field)
        }

        classTable.columns.filter { !it.isOrig }.forEach { col ->
            val subTable = collector.findSubTable(col.origField)!!
            val subIdType = subTable.idColumn.type.typeName

            val name = "\$${col.origField.name}_id_check"
            val field = JcFieldBuilder(clazz)
                .setName(name)
                .addDummyFieldAnnot()
                .setType(subIdType)
                .addBlanckAnnot(CHECK_FIELD_ANNOT)
                .buildField()

            relationChecks.set(clazz, col.origField, field)
            fields.add(field)
        }

        return fields
    }

    override fun methodsOf(clazz: JcClassOrInterface, originalMethods: List<JcMethod>): List<JcMethod>? {
        if (!clazz.isDataClass) return null

        val cp = clazz.classpath
        val classTable = collector.getTable(clazz) ?: return null
        val gen = SignatureGenerator(this, cp, collector, clazz, classTable, originalMethods)

        return originalMethods.toPersistentList().addAll(gen.getFunctions())
    }
}

private class SignatureGenerator(
    val dataclassTransformer: JcDataclassTransformer,
    val cp: JcClasspath,
    val collector: JcTableInfoCollector,
    val clazz: JcClassOrInterface,
    val classTable: TableInfo.TableWithIdInfo,
    val originalMethods: List<JcMethod>
) {
    fun getFunctions(): List<JcMethod> {
        val init = getConstructor()
        val serializer = getSerializer()
        val serializerWithSkips = getSerializerWithSkips()
        val getId = getId()
        val staticGetId = getStaticGetId(getId)
        val getters = getters()
        val funs = persistentListOf(
            init,
            getStaticBlankInit(),
            getId,
            staticGetId,
            serializer,
            serializerWithSkips,
            getStaticSerializer(serializer),
            getStaticSerializerWithSkips(serializerWithSkips),
            getIdentity(),
            getSaveUpdate(getId, getters),
            getDelete(getId, getters)
        )
            .addAll(getLambdas())
            .addAll(getters)
        val fetchedConst = if (classTable.orderedRelations().isNotEmpty()) getConstructorWithFetched() else null
        fetchedConst?.also { return funs.add(it).add(getStaticConstructorWithFetched(it)) }

        return funs.add(getStaticConstructorWithFetched(init))
    }

    fun getConstructor(): JcMethod {
        val relTables = classTable.orderedRelations()
        val itableDesc = cp.findClass(ITABLE).jvmDescriptor

        val origInit = originalMethods.single { it.name == JAVA_INIT && it.parameters.isEmpty() }

        val sig =
            if (relTables.isEmpty()) null
            else "(${JAVA_OBJ_ARR.jvmName()}${
                relTables.joinToString(separator = ";") { rel ->
                    "${itableDesc.dropLast(1)}<${rel.relatedDataclass(cp).jvmDescriptor}>"
                }
            };)V"

        return JcMethodBuilder(clazz)
            .setName(JAVA_INIT)
            .addBlanckAnnot(INIT_ANNOT)
            .setRetType(JAVA_VOID)
            .setSig(sig)
            .addFreshParam(JAVA_OBJ_ARR)
            .let { relTables.fold(it) { b, _ -> b.addFreshParam(ITABLE) } }
            .addFillerFuture(JcInitTransformer(dataclassTransformer, cp, classTable, origInit))
            .buildMethod()
    }

    fun getStaticBlankInit(): JcMethod {
        val origInit = originalMethods.single { it.name == JAVA_INIT && it.parameters.isEmpty() }
        return JcMethodBuilder(clazz)
            .setName(STATIC_BLANK_INIT_NAME)
            .addBlanckAnnot(STATIC_BLANK_INIT_ANNOT)
            .setAccess(Opcodes.ACC_STATIC)
            .setRetType(clazz.typename.typeName)
            .addFillerFuture(JcStaticBlankInitTransformer(dataclassTransformer, cp, classTable, origInit))
            .buildMethod()
    }

    fun getConstructorWithFetched(): JcMethod {
        val origInit = originalMethods.single { it.name == JAVA_INIT && it.parameters.isEmpty() }
        return JcMethodBuilder(clazz)
            .setName(JAVA_INIT)
            .addBlanckAnnot(INIT_FETCH_ANNOT)
            .setRetType(JAVA_VOID)
            .addFreshParam(JAVA_OBJ_ARR)
            .addFillerFuture(JcFetchedInitTransformer(dataclassTransformer, cp, classTable, origInit))
            .buildMethod()
    }

    fun getStaticConstructorWithFetched(fetchedInit: JcMethod) =
        JcMethodBuilder(clazz)
            .setName(STATIC_INIT_NAME)
            .addBlanckAnnot(STATIC_INIT_FETCH_ANNOT)
            .setRetType(clazz.typename.typeName)
            .setAccess(Opcodes.ACC_STATIC)
            .addFreshParam(JAVA_OBJ_ARR)
            .addFillerFuture(JcStaticFetchedInitTransformer(cp, classTable, fetchedInit))
            .buildMethod()

    fun getId() =
        JcMethodBuilder(clazz)
            .setName(GET_ID_NAME)
            .addBlanckAnnot(GET_ID_ANNOT)
            .setRetType(classTable.idColumn.type.typeName)
            .addFillerFuture(JcGetIdTransformer(cp, classTable))
            .buildMethod()

    fun getStaticGetId(getId: JcMethod) =
        JcMethodBuilder(clazz)
            .setName(STATIC_GET_ID_NAME)
            .addBlanckAnnot(STATIC_GET_ID_ANNOT)
            .setRetType(classTable.idColumn.type.typeName)
            .addFreshParam(clazz.name)
            .setAccess(Opcodes.ACC_STATIC)
            .addFillerFuture(JcStaticGetIdTransformer(cp, clazz.toType(), getId))
            .buildMethod()

    fun getters(): List<JcMethod> {
        // classTable.origFieldsInOrder()
        return collector.collectFields(clazz).map { field ->
            val name = getterName(field)
            val sig = field.signature?.let { "()$it" }
            JcMethodBuilder(clazz)
                .setName(name)
                .addBlanckAnnot(DATACLASS_GETTER)
                .addBlanckAnnot(field.name)
                .setSig(sig)
                .setRetType(field.type.typeName)
                .addFillerFuture(JcGetterTransformer(cp, clazz, field, name))
                .buildMethod()
        }
    }

    fun getSerializer() =
        JcMethodBuilder(clazz)
            .setName(SERIALIZER_NAME)
            .addBlanckAnnot(SERIALIZER_ANNOT)
            .setRetType(JAVA_OBJ_ARR)
            .addFillerFuture(JcSerializerTransformer(cp, dataclassTransformer, classTable, false))
            .buildMethod()

    fun getSerializerWithSkips() =
        JcMethodBuilder(clazz)
            .setName(SERIALIZER_WITH_SKIPS_NAME)
            .addBlanckAnnot(SERIALIZER_WITH_SKIPS_ANNOT)
            .setRetType(JAVA_OBJ_ARR)
            .addFillerFuture(JcSerializerTransformer(cp, dataclassTransformer, classTable, true))
            .buildMethod()

    fun getStaticSerializer(serializer: JcMethod) =
        JcMethodBuilder(clazz)
            .setName(STATIC_SERIALIZER_NAME)
            .addBlanckAnnot(STATIC_SERIALIZER_ANNOT)
            .setRetType(JAVA_OBJ_ARR)
            .setAccess(Opcodes.ACC_STATIC)
            .addFreshParam(clazz.name)
            .addFillerFuture(JcStaticSerializerTransformer(cp, classTable, serializer, false))
            .buildMethod()

    fun getStaticSerializerWithSkips(serializer: JcMethod) =
        JcMethodBuilder(clazz)
            .setName(STATIC_SERIALIZER_WITH_SKIPS_NAME)
            .addBlanckAnnot(STATIC_SERIALIZER_WITH_SKIPS_ANNOT)
            .setRetType(JAVA_OBJ_ARR)
            .setAccess(Opcodes.ACC_STATIC)
            .addFreshParam(clazz.name)
            .addFillerFuture(JcStaticSerializerTransformer(cp, classTable, serializer, true))
            .buildMethod()

    fun getIdentity(): JcMethod {
        val classType = clazz.toType()
        return JcMethodBuilder(clazz)
            .setName(IDENTITY_NAME)
            .addBlanckAnnot(IDENTITY_ANNOT)
            .setAccess(Opcodes.ACC_STATIC)
            .setRetType(classType.typeName)
            .addFillerFuture(JcIdentityTransformer(classType))
            .addFreshParam(clazz.name)
            .buildMethod()
    }

    fun getSaveUpdate(getId: JcMethod, getters: List<JcMethod>) =
        JcMethodBuilder(clazz)
            .setName(SAVE_UPDATE_NAME)
            .addBlanckAnnot(SAVE_UPDATE_ANNOT)
            .setAccess(Opcodes.ACC_STATIC)
            .setRetType(JAVA_VOID)
            .addFillerFuture(JcSaveUpdateTransformer(dataclassTransformer, cp, classTable, clazz, getId, getters))
            .addFreshParam(clazz.name)
            .addFreshParam(SAVE_UPD_DEL_CTX)
            .buildMethod()

    fun getDelete(getId: JcMethod, getters: List<JcMethod>) =
        JcMethodBuilder(clazz)
            .setName(DELETE_NAME)
            .addBlanckAnnot(DELETE_ANNOT)
            .setAccess(Opcodes.ACC_STATIC)
            .setRetType(JAVA_VOID)
            .addFillerFuture(JcDeleteTransformer(dataclassTransformer, cp, classTable, clazz, getId, getters))
            .addFreshParam(clazz.name)
            .addFreshParam(SAVE_UPD_DEL_CTX)
            .buildMethod()

    fun getLambdas(): List<JcMethod> {
        return classTable.relations.flatMap { rel ->
            val subTable = collector.findSubTable(rel.origField)!!
            val btwTable = when (rel) {
                is Relation.RelationByTable -> rel.joinTable.name
                else -> null
            }?.let { collector.getBtwTable(it) }

            val gen = SubSignatureGenerator(dataclassTransformer, cp, classTable, subTable, btwTable, rel, clazz)
            val lambdas =
                when (rel) {
                    is Relation.RelationByTable -> gen.getByTableLambdas()
                    is Relation.ManyToOne, is Relation.OneToOne -> listOf(gen.getManyToOneFilter())
                    is Relation.OneToManyByColumn -> listOf(gen.getOneToManyFilter())
                }

            lambdas.also { dataclassTransformer.relationLambdas.set(clazz, rel.origField, it) }
        }
    }

    private class SubSignatureGenerator(
        val dataclassTransformer: JcDataclassTransformer,
        val cp: JcClasspath,
        val classTable: TableInfo.TableWithIdInfo,
        val subTable: TableInfo.TableWithIdInfo,
        val btwTable: TableInfo?,
        val rel: Relation,
        val clazz: JcClassOrInterface
    ) {

        fun getByTableLambdas(): List<JcMethod> {
            return listOf(getBtwFilter(), getBtwSelector(), getSetFilter())
        }

        fun getOneToManyFilter(): JcMethod {
            return JcMethodBuilder(clazz)
                .setName("\$${rel.origField.name}_onetomany_filter")
                .setRetType(JAVA_BOOL)
                .addBlanckAnnot(ONE_TO_MANY_FILTER_ANNOT)
                .addFreshParam(subTable.origClass.typename.typeName)
                .addFillerFuture(JcOneToManyFilterTransformer(dataclassTransformer, cp, classTable, subTable, rel))
                .buildMethod()
        }

        fun getManyToOneFilter(): JcMethod {
            return JcMethodBuilder(clazz)
                .setName("\$${rel.origField.name}_manytoone_filter")
                .setRetType(JAVA_BOOL)
                .addBlanckAnnot(MANY_TO_ONE_FILTER_ANNOT)
                .addFreshParam(rel.origField.type.typeName)
                .addFillerFuture(JcManyToOneFilterTransformer(dataclassTransformer, cp, classTable, subTable, rel))
                .buildMethod()
        }

        fun getBtwFilter(): JcMethod {
            return JcMethodBuilder(clazz)
                .setName("\$${rel.origField.name}_btw_filter")
                .setRetType(JAVA_BOOL)
                .addBlanckAnnot(FILTER_BTW_ANNOT)
                .addFreshParam(JAVA_OBJ_ARR)
                .addFillerFuture(JcBtwFilterTransformer(cp, classTable, subTable, btwTable))
                .buildMethod()
        }

        fun getBtwSelector(): JcMethod {
            return JcMethodBuilder(clazz)
                .setName("\$${rel.origField.name}_btw_selector")
                .setRetType(subTable.idColumn.type.typeName)
                .addBlanckAnnot(SELECT_BTW_ANNOT)
                .addFreshParam(JAVA_OBJ_ARR)
                .addFillerFuture(JcBtwSelectTransformer(cp, classTable, subTable, btwTable))
                .buildMethod()
        }

        fun getSetFilter(): JcMethod {
            return JcMethodBuilder(clazz)
                .setName("\$${rel.origField.name}_set_filter")
                .addBlanckAnnot(FILTER_SET_ANNOT)
                .setRetType(JAVA_BOOL)
                .addFreshParam(rel.origField.type.typeName)
                .addFillerFuture(JcSetFilterTransformer(dataclassTransformer, cp, classTable, subTable, rel))
                .buildMethod()
        }
    }
}
