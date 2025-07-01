package util.database

import machine.interpreter.transformers.springjpa.BASE_TABLE_MANAGER
import machine.interpreter.transformers.springjpa.NO_ID_TABLE_MANAGER
import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.ext.fields
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.objectClass
import org.jacodb.api.jvm.ext.toType
import org.usvm.jvm.util.genericTypesFromSignature
import org.usvm.jvm.util.toJcClassOrInterface
import org.usvm.jvm.util.toJcType
import java.lang.reflect.Type

class JcTableInfoCollector(
    private val cp: JcClasspath,
    private val checkParents: Boolean = true
) {

    private val tablesInfo = HashMap<String, TableInfo.TableWithIdInfo>()
    private val btwTablesInfo = HashMap<String, TableInfo>()

    fun getEmbeddedIds() = tablesInfo.values.mapNotNull(TableInfo.TableWithIdInfo::getEmbeddedIds)

    fun getIdClasses() = tablesInfo.values.mapNotNull(TableInfo.TableWithIdInfo::getIdClasses)

    fun allTables(): List<TableInfo> = tablesInfo.values + btwTablesInfo.values

    fun tables(): List<TableInfo.TableWithIdInfo> = tablesInfo.values.toList()

    fun getBtwTable(name: String): TableInfo? = btwTablesInfo[name]

    fun getTable(clazz: JcClassOrInterface): TableInfo.TableWithIdInfo? = tablesInfo[getTableName(clazz)]

    fun getTable(type: JcClassType): TableInfo.TableWithIdInfo? = tablesInfo[getTableName(type.jcClass)]

    fun getTableByPartName(name: String): List<TableInfo.TableWithIdInfo> {

        fun isPartOf(clazzName: String): Boolean =
            clazzName.split(".").reduceRight { part, acc ->
                if (acc == name) return true
                "${part}.${acc}"
            }.let { false }


        return tables().filter { isPartOf(it.origClassName) }
    }

    fun dropNotOrigFields() = tables().forEach(TableInfo::dropNotOriginFields)

    fun collectFields(clazz: JcClassOrInterface, filter: (JcField) -> Boolean = { true }): List<JcField> {

        val supClass = clazz.superClass
        val columns = clazz.fields +
                if (checkParents && supClass != null && supClass != clazz.classpath.objectClass
                    && supClass.annotations.any { it.jcClass?.simpleName.equals("MappedSuperclass") }
                )
                    collectFields(supClass)
                else
                    listOf()

        return columns.sortedBy { it.name }.filter(filter)
    }

    fun collectTable(clazz: JcClassOrInterface): TableInfo.TableWithIdInfo {

        // TODO: think about cache
        val name = getTableName(clazz)
        val fields = collectFields(clazz) { !it.isStatic }

        val idColumn = IdColumnInfo.fromFields(cp, fields)

        tablesInfo.getOrPut(name) {
            TableInfo.TableWithIdInfo(name, hashSetOf(), hashSetOf(), idColumn, clazz.name)
        }
        val classTable = tablesInfo[name]!!

        fields.filter { !contains(it.annotations, "Id") }.forEach { field ->
            val simpleColName = getColumnName(field)

//            val validators = field.annotations.mapNotNull { annot ->
//                // TODO: adds other enums validators!!
//                JcValidateAnnotation.entries.find { it.annotationSimpleName == annot.jcClass!!.simpleName }
//                    ?.let { annot to it }
//            }
            val validators = emptyList<Pair<JcAnnotation?, JcValidateAnnotation>>()

            if (
                !contains(
                    field.annotations,
                    listOf("OneToOne", "OneToMany", "ManyToOne", "ManyToMany")
                )
            ) {

                val colInfo = ColumnInfo(simpleColName, field.type, field, true, validators)
                classTable.insertColumn(colInfo)
                return@forEach
            }

            val subClass = field.signature?.genericTypesFromSignature?.let { cp.findClass(it[0]) }
                ?: cp.findClass(field.type.typeName)

            val subTable = tablesInfo.get(getTableName(subClass)) ?: collectTable(subClass)
            val rel = Relation.fromField(classTable, subTable, field)!!
            classTable.insertRelation(rel)

            when (rel) {
                is Relation.OneToOne -> {
                    if (rel.mappedBy != null) return@forEach
                    classTable.insertColumns(
                        rel.buildColumnsForRelation(classTable, subTable, field)
                    )
                }

                is Relation.OneToManyByColumn -> {
                    if (rel.mappedBy != null) return@forEach
                    subTable.insertColumns(
                        rel.buildColumnsForRelation(classTable, subTable, field)
                    )
                }

                is Relation.ManyToOne -> {
                    classTable.insertColumns(
                        rel.buildColumnsForRelation(classTable, subTable, field)
                    )
                }

                is Relation.RelationByTable -> {
                    val newTable = rel.joinTable.toTable()
                    btwTablesInfo[newTable.name] = newTable
                }
            }
        }

        return classTable
    }

    fun findSubTable(field: JcField): TableInfo.TableWithIdInfo? {
        val subClass = field.signature?.genericTypesFromSignature?.get(0)?.let { cp.findClass(it) }
            ?: field.type.toJcClassOrInterface(cp)!!
        return tablesInfo.get(getTableName(subClass))
    }
}

data class ColumnInfo(
    val name: String,
    val type: TypeName,
    val origField: JcField,
    val isOrig: Boolean,
    val validators: List<Pair<JcAnnotation?, JcValidateAnnotation>>
)

sealed class IdColumnInfo {

    protected abstract fun getSimpleIds(): List<SingleId>

    abstract fun getClassName(): String?

    abstract fun getType(cp: JcClasspath): JcType

    fun toColumnInfos() = getSimpleIds().map(SingleId::toColumnInfo)

    fun orderedSimpleIds() = getSimpleIds().sortedBy { it.name }

    data class SingleId(
        val name: String,
        val type: TypeName,
        val validators: List<Pair<JcAnnotation?, JcValidateAnnotation>>,
        val isAutoGenerateId: Boolean,
        val origField: JcField
    ) : IdColumnInfo() {
        override fun getSimpleIds() = listOf(this)
        override fun getClassName() = null
        override fun getType(cp: JcClasspath) = type.toJcType(cp)!!

        fun toColumnInfo() = ColumnInfo(name, type, origField, true, validators)
    }

    data class EmbeddedId(
        val name: String,
        val embeddedClassName: String,
        val idFields: List<SingleId> // fields from Embedded class
    ) : IdColumnInfo() {
        override fun getSimpleIds() = idFields
        override fun getClassName() = embeddedClassName
        override fun getType(cp: JcClasspath) = cp.findType(embeddedClassName)
    }

    data class ClassId(
        val idFields: List<SingleId>, // same in DTO class and ClassId class
        val classIdName: String
    ) : IdColumnInfo() {
        override fun getSimpleIds() = idFields
        override fun getClassName() = classIdName
        override fun getType(cp: JcClasspath) = cp.findType(classIdName)
    }

    companion object {
        fun fromFields(cp: JcClasspath, fields: List<JcField>): IdColumnInfo {
            val idFields = fields.filter { contains(it.annotations, "Id") }

            return if (idFields.isEmpty()) {
                val embeddedIdField = fields.single { contains(it.annotations, "EmbeddedId") }
                val embeddedClassName = embeddedIdField.type.typeName
                val embeddedClass = cp.findClass(embeddedClassName)
                val embeddedFields = embeddedClass.fields.map {
                    SingleId(getColumnName(it), it.type, emptyList(), false, it)
                }
                EmbeddedId(getColumnName(embeddedIdField), embeddedClassName, embeddedFields)
            } else if (idFields.size > 1) {
                val fields = idFields.map {
                    SingleId(getColumnName(it), it.type, emptyList(), false, it)
                }
                val idClass = (find(idFields.first().enclosingClass.annotations, "IdClass")!!
                    .values["value"] as Type).typeName
                ClassId(fields, idClass)
            } else {
                val idField = idFields.single()
                val isAutoGenerateId = contains(idField.annotations, "GeneratedValue")
                val validators = idField.getValidators()
                SingleId(getColumnName(idField), idField.type, validators, isAutoGenerateId, idField)
            }
        }
    }
}

open class TableInfo(
    val name: String,
    protected val columns: MutableSet<ColumnInfo>,
    val relations: MutableSet<Relation>
) {

    open val approximateManagerClassName = NO_ID_TABLE_MANAGER

    class TableWithIdInfo(
        name: String,
        columns: MutableSet<ColumnInfo>,
        relations: MutableSet<Relation>,
        val idColumn: IdColumnInfo,
        val origClassName: String
    ) : TableInfo(name, columns, relations) {

        override val approximateManagerClassName = BASE_TABLE_MANAGER

        override fun columnsInOrder() = (columns.toList() + idColumn.toColumnInfos()).sortedBy { it.name }

        fun isAutoGenerateId() = idColumn is IdColumnInfo.SingleId && idColumn.isAutoGenerateId

        fun idColumnsIxs(): List<Int> {
            val columns = columnsInOrder()
            return idColumn.toColumnInfos().map { idCol -> columns.indexOfFirst { it.name.equals(idCol.name) } }
        }

        fun joinColumnsInfo() = idColumn.orderedSimpleIds().map {
            val name = "${name}_${it.name}"
            ColumnInfo(name, it.type, it.origField, false, emptyList())
        }

        fun getIdColumns() = idColumn.orderedSimpleIds()

        fun getEmbeddedIds() = idColumn as? IdColumnInfo.EmbeddedId

        fun getIdClasses() = idColumn as? IdColumnInfo.ClassId

        fun getComplexIdClassName() = idColumn.getClassName()

        fun origFieldsInOrder(cp: JcClasspath): List<JcField> {
            val origClass = cp.findClass(origClassName)
            return JcTableInfoCollector(cp).collectFields(origClass)
                .sortedBy { getColumnName(it) }
        }
    }

    open fun columnsInOrder() = columns.sortedBy { it.name }

    fun dropNotOriginFields() {
        columns.removeIf { !it.isOrig }
    }

    fun relatedClasses(cp: JcClasspath) = relations.map { it.relatedDataclass(cp) }.distinctBy { it.name }

    fun insertColumn(col: ColumnInfo) {
        columns.add(col)
    }

    fun insertColumns(cols: List<ColumnInfo>) {
        columns.addAll(cols)
    }

    fun insertRelation(rel: Relation) {
        relations.add(rel)
    }

    fun indexOfCol(col: ColumnInfo) = columnsInOrder().indexOfFirst { it.name == col.name }

    fun indexesOfColumns(columns: List<ColumnInfo>) = columnsInOrder().mapIndexedNotNull { ix, col ->
        val targetNames = columns.map { it.name }
        if (targetNames.contains(col.name)) ix
        else null
    }

    fun indexOfField(field: JcField) = columnsInOrder().indexOfFirst { it.origField.name == field.name }

    fun indexesOfField(field: JcField) = columnsInOrder().mapIndexedNotNull { ix, col ->
        if (col.origField.name == field.name) ix
        else null
    }

    fun columnsOfField(field: JcField) = columnsInOrder().filter {
        it.origField.name == field.name
    }

    fun orderedRelations() = relations.sortedBy { it.toString() }
}

sealed class Relation(
    val origField: JcField,
    cascadeType: List<CascadeType>
) {

    enum class CascadeType {
        ALL, // repeat all operations on children
        PERSIST, // save children too
        MERGE, // updates children too
        REMOVE, // remove children too
        REFRESH, // TODO: session
        DETACH, // TODO: session
        REPLICATE, // TODO: session
        SAVE_UPDATE, // PERSIST + MERGE from Hibernate
        DELETE, // REMOVE from Hibernate
        LOCK // TODO:
    }

    val isAllowSave = cascadeType.any { saveTypes.contains(it) }
    val isAllowUpdate = cascadeType.any { updateTypes.contains(it) }
    val isAllowDelete = cascadeType.any { deleteTypes.contains(it) }

    abstract val mappedBy: String?

    open fun toTableName(cp: JcClasspath): String {
        return getTableName(relatedDataclass(cp))
    }

    fun relatedDataclass(cp: JcClasspath): JcClassOrInterface {
        return origField.signature?.let { cp.findClass(it.genericTypesFromSignature[0]) }
            ?: origField.type.toJcClassOrInterface(cp)!!
    }

    fun relatedDataclassType(cp: JcClasspath): JcClassType {
        return relatedDataclass(cp).toType()
    }

    override fun toString(): String {
        return "\$r${origField.enclosingClass.name}.${origField.name}"
    }

    // @JoinColumn(name = "id_part1", referencedColumnName = "idPart1")
    data class JoinColumn(
        val name: String,
        val referencedColName: String?
    )

    // @JoinColumns( { @JoinColumn(...), ... } )
    class JoinColumns(
        val joinColumns: List<JoinColumn>
    ) {
        fun toColumns(table: TableInfo.TableWithIdInfo, field: JcField): List<ColumnInfo> {
            val idColumns = table.getIdColumns().associate { it.name to it }
            return if (idColumns.size == 1) {
                val idColumn = idColumns.values.single()
                val join = joinColumns.single()
                listOf(ColumnInfo(join.name, idColumn.type, field, false, emptyList()))
            } else {
                joinColumns.map {
                    val idColumn = idColumns[it.referencedColName!!]!!
                    ColumnInfo(it.name, idColumn.type, field, false, emptyList())
                }
            }
        }
    }

    class JoinTable(
        val name: String,
        val mainClassName: String,
        val subClassName: String,
        val joinCols: List<ColumnInfo>,
        val inverseJoinCols: List<ColumnInfo>
    ) {
        fun allColumns() = joinCols + inverseJoinCols

        fun indexesOf(className: String) = toTable().indexesOfColumns(
            if (className == mainClassName) joinCols else inverseJoinCols
        )

        fun indexesOfOtherClass(className: String) =
            if (className == mainClassName) indexesOf(subClassName) else indexesOf(mainClassName)

        private var table: TableInfo? = null
        fun toTable(): TableInfo {
            table?.also { return it }
            table = TableInfo(name, allColumns().toMutableSet(), hashSetOf())
            return table!!
        }
    }

    sealed class RelationByTable(
        val joinTable: JoinTable,
        origField: JcField,
        cascadeType: List<CascadeType>
    ) : Relation(origField, cascadeType) {
        override fun toTableName(cp: JcClasspath): String {
            return joinTable.name
        }
    }

    sealed class RelationByColumn(
        val join: JoinColumns,
        origField: JcField,
        cascadeType: List<CascadeType>
    ) : Relation(origField, cascadeType) {
        abstract fun buildColumnsForRelation(
            parentTable: TableInfo.TableWithIdInfo,
            childTable: TableInfo.TableWithIdInfo,
            field: JcField
        ): List<ColumnInfo>
    }

    class OneToOne(
        override val mappedBy: String?,
        join: JoinColumns,
        origField: JcField,
        cascadeType: List<CascadeType>
    ) : RelationByColumn(join, origField, cascadeType) {
        // Old code: TableInfo.ColumnInfo(rel.join.colName, subIdType, field, false, listOf())
        override fun buildColumnsForRelation(
            parentTable: TableInfo.TableWithIdInfo,
            childTable: TableInfo.TableWithIdInfo,
            field: JcField
        ) = join.toColumns(childTable, field)

        override fun toString(): String {
            return "\$OneToOne" + super.toString()
        }
    }

    class OneToManyByColumn(
        override val mappedBy: String?,
        val join: JoinColumns?,
        origField: JcField,
        cascadeType: List<CascadeType>
    ) : Relation(origField, cascadeType) {
        // Old code: TableInfo.ColumnInfo(rel.join!!.colName, idType, field, false, listOf())
        fun buildColumnsForRelation(
            parentTable: TableInfo.TableWithIdInfo,
            childTable: TableInfo.TableWithIdInfo,
            field: JcField
        ) = join!!.toColumns(parentTable, field)

        override fun toString(): String {
            return "\$OneToManyCol" + super.toString()
        }
    }

    class OneToManyByTable(
        joinTable: JoinTable,
        origField: JcField,
        cascadeType: List<CascadeType>
    ) : RelationByTable(joinTable, origField, cascadeType) {
        override val mappedBy: String? = null

        override fun toString(): String {
            return "\$OneToManyTable" + super.toString()
        }
    }

    class ManyToOne(
        join: JoinColumns,
        origField: JcField,
        cascadeType: List<CascadeType>
    ) : RelationByColumn(join, origField, cascadeType) {
        override val mappedBy: String? = null

        // Old code: TableInfo.ColumnInfo(rel.join.colName, subIdType, field, false, listOf())
        override fun buildColumnsForRelation(
            parentTable: TableInfo.TableWithIdInfo,
            childTable: TableInfo.TableWithIdInfo,
            field: JcField
        ) = join.toColumns(childTable, field)

        override fun toString(): String {
            return "\$ManyToOne" + super.toString()
        }
    }

    class ManyToMany(
        override val mappedBy: String?,
        joinTable: JoinTable,
        origField: JcField,
        cascadeType: List<CascadeType>
    ) : RelationByTable(joinTable, origField, cascadeType) {
        override fun toString(): String {
            return "\$ManyToMany" + super.toString()
        }
    }

    companion object {

        val saveTypes = listOf(
            CascadeType.ALL,
            CascadeType.SAVE_UPDATE,
            CascadeType.PERSIST
        )

        val updateTypes = listOf(
            CascadeType.ALL,
            CascadeType.SAVE_UPDATE,
            CascadeType.MERGE
        )

        val deleteTypes = listOf(
            CascadeType.ALL,
            CascadeType.DELETE,
            CascadeType.REMOVE
        )

        private fun join(annotation: JcAnnotation): JoinColumn {
            val name = annotation.values["name"] as String
            val referencedColName = annotation.values["referencedColName"] as String?
            return JoinColumn(name, referencedColName)
        }

        private fun singleJoin(annotation: JcAnnotation): JoinColumns = JoinColumns(listOf(join(annotation)))
        private fun joins(annotations: List<JcAnnotation>) =
            find(annotations, "JoinColumns")?.let { it.values["value"] as? List<*> }
                ?.filter { (it as JcAnnotation).jcClass!!.simpleName == "JoinColumn" }
                ?.map { join(it as JcAnnotation) }
                ?.let(::JoinColumns)
                ?: find(annotations, "JoinColumn")?.let(::singleJoin)

        private fun mappedBy(annotation: JcAnnotation) = annotation.values["mappedBy"] as String?
        private fun cascadeType(annotation: JcAnnotation, common: List<CascadeType> = listOf()) =
            (annotation.values["cascade"] as? List<*>)?.map { CascadeType.valueOf((it as JcField).name) } ?: common

        fun fromField(
            classTable: TableInfo.TableWithIdInfo,
            subTable: TableInfo.TableWithIdInfo,
            field: JcField
        ): Relation? {
            val simpleName = databaseName(field.name)
            val commonJoins = subTable.getIdColumns().map {
                val fieldName = it.origField.name
                val joinName = "${simpleName}_${fieldName}"
                JoinColumn(joinName, fieldName)
            }.let(::JoinColumns)

            val annotations = field.annotations
            val joins = joins(annotations)

            find(annotations, "OneToOne")
                ?.let {
                    val mappedBy = mappedBy(it)
                    val cascade = cascadeType(it, listOf(CascadeType.PERSIST, CascadeType.MERGE))
                    return OneToOne(mappedBy, joins ?: commonJoins, field, cascade)
                }

            find(annotations, "OneToMany")
                ?.let {
                    val mappedBy = mappedBy(it)
                    val cascade = cascadeType(it)
                    if (joins == null && mappedBy == null) {
                        val name = getBtwTableName(field)
                        val classJoinColumns = classTable.joinColumnsInfo()
                        val subJoinColumns = subTable.joinColumnsInfo()
                        val table = JoinTable(
                            name,
                            classTable.origClassName,
                            subTable.origClassName,
                            classJoinColumns,
                            subJoinColumns
                        )
                        return OneToManyByTable(table, field, cascade)
                    }

                    return OneToManyByColumn(mappedBy, joins, field, cascade)
                }

            find(annotations, "ManyToOne")
                ?.let {
                    val cascade = cascadeType(it, listOf(CascadeType.PERSIST, CascadeType.MERGE))
                    return ManyToOne(joins ?: commonJoins, field, cascade)
                }

            find(annotations, "ManyToMany")
                ?.let {
                    val mappedBy = mappedBy(it)
                    val cascade = cascadeType(it)
                    val joinTableAnnot = find(annotations, "JoinTable")
                    val joinTableName = (joinTableAnnot?.values?.get("name") as? String)
                        ?: getBtwTableName(field)
                    val classJoinColumns = (joinTableAnnot?.values?.get("JoinColumns") as? List<*>)
                        ?.filter { (it as JcAnnotation).jcClass!!.simpleName == "JoinColumn" }
                        ?.map { join(it as JcAnnotation) }
                        ?.let(::JoinColumns)
                        ?.toColumns(classTable, field)
                        ?: classTable.joinColumnsInfo()
                    val subJoinColumns = (joinTableAnnot?.values?.get("inverseJoinColumns") as List<*>?)
                        ?.filter { (it as JcAnnotation).jcClass!!.simpleName == "JoinColumn" }
                        ?.map { join(it as JcAnnotation) }
                        ?.let(::JoinColumns)
                        ?.toColumns(subTable, field)
                        ?: subTable.joinColumnsInfo()
                    val joinTable = JoinTable(
                        joinTableName,
                        classTable.origClassName,
                        subTable.origClassName,
                        classJoinColumns,
                        subJoinColumns
                    )
                    return ManyToMany(mappedBy, joinTable, field, cascade)
                }

            return null;
        }
    }
}
