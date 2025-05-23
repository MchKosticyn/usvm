package util.database

import machine.interpreter.transformers.springjpa.BASE_TABLE_MANAGER
import machine.interpreter.transformers.springjpa.NO_ID_TABLE_MANAGER
import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.ext.fields
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.objectClass
import org.jacodb.api.jvm.ext.toType
import org.usvm.jvm.util.genericTypesFromSignature
import org.usvm.jvm.util.toJcClassOrInterface

class JcTableInfoCollector(
    private val cp: JcClasspath,
    private val checkParents: Boolean = true
) {

    private val tablesInfo = HashMap<String, TableInfo.TableWithIdInfo>()
    private val btwTablesInfo = HashMap<String, TableInfo>()

    fun allTables(): List<TableInfo> = tablesInfo.values + btwTablesInfo.values

    fun tables(): List<TableInfo.TableWithIdInfo> = tablesInfo.values.toList()

    fun getBtwTable(name: String): TableInfo? = btwTablesInfo[name]

    fun getTable(clazz: JcClassOrInterface): TableInfo.TableWithIdInfo? = tablesInfo[getTableName(clazz)]

    fun getTableByPartName(name: String): List<TableInfo.TableWithIdInfo> {

        fun isPartOf(clazz: JcClassOrInterface): Boolean =
            clazz.name.split(".").reduceRight { part, acc ->
                if (acc == name) return true
                "${part}.${acc}"
            }.let { false }


        return tables().filter { isPartOf(it.origClass) }
    }

    fun dropNotOrigFields() = tables().forEach { it.columns.removeIf{ !it.isOrig } }

    fun collectFields(clazz: JcClassOrInterface): List<JcField> {

        val supClass = clazz.superClass
        val columns = clazz.fields +
                if (checkParents && supClass != null && supClass != clazz.classpath.objectClass
                    && supClass.annotations.any { it.jcClass?.simpleName.equals("MappedSuperclass") }
                )
                    collectFields(supClass)
                else
                    listOf()

        return columns.sortedBy { it.name }
    }

    fun collectTable(clazz: JcClassOrInterface): TableInfo.TableWithIdInfo {

        // TODO: think about cache
        val name = getTableName(clazz)
        val fields = collectFields(clazz)
        val idField = fields
            .single { contains(it.annotations, "Id") }
        val idColumn = idField.let { TableInfo.ColumnInfo(getColumnName(it), it.type, idField, true, listOf()) }
        val idType = idField.type

        tablesInfo.getOrPut(name) { TableInfo.TableWithIdInfo(name, hashSetOf(), hashSetOf(), idColumn, clazz) }
        val classTable = tablesInfo[name]!!

        fields.forEach { field ->

            val simpleColName = getColumnName(field)

            val validators = field.annotations.mapNotNull { annot ->
                JcValidateAnnotation.entries.find { it.annotationSimpleName == annot.jcClass!!.simpleName }
                    ?.let { annot to it }
            }

            fun buildCommonColumn(name: String, typ: TypeName, isOrig: Boolean): TableInfo.ColumnInfo =
                TableInfo.ColumnInfo(name, typ, field, isOrig, validators)

            if (
                !contains(
                    field.annotations,
                    listOf("OneToOne", "OneToMany", "ManyToOne", "ManyToMany")
                )
            ) {

                val colInfo = buildCommonColumn(simpleColName, field.type, true)
                classTable.insertColumn(colInfo)
                return@forEach
            }

            val subClass = field.signature?.genericTypesFromSignature?.let { cp.findClass(it[0]) }
                ?: cp.findClass(field.type.typeName)

            val subTable = tablesInfo.get(getTableName(subClass)) ?: collectTable(subClass)
            val subIdType = subTable.idColumn.type

            val rel = Relation.fromField(classTable, subTable, field)!!
            classTable.insertRelation(rel)

            when (rel) {
                is Relation.OneToOne -> {
                    if (rel.mappedBy != null) return@forEach
                    classTable.insertColumn(
                        TableInfo.ColumnInfo(rel.join.colName, subIdType, field, false, listOf())
                    )
                }

                is Relation.OneToManyByColumn -> {
                    if (rel.mappedBy != null) return@forEach
                    subTable.insertColumn(
                        TableInfo.ColumnInfo(rel.join!!.colName, idType, field, false, listOf())
                    )
                }

                is Relation.ManyToOne -> {
                    classTable.insertColumn(
                        TableInfo.ColumnInfo(rel.join.colName, subIdType, field, false, listOf())
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

open class TableInfo(
    val name: String,
    val columns: MutableSet<ColumnInfo>,
    val relations: MutableSet<Relation>
) {

    open val approximateManagerClassName = NO_ID_TABLE_MANAGER

    data class ColumnInfo(
        val name: String,
        val type: TypeName,
        val origField: JcField,
        val isOrig: Boolean,
        val validators: List<Pair<JcAnnotation?, JcValidateAnnotation>>
    )

    class TableWithIdInfo(
        name: String,
        columns: MutableSet<ColumnInfo>,
        relations: MutableSet<Relation>,
        val idColumn: ColumnInfo,
        val origClass: JcClassOrInterface
    ) : TableInfo(name, columns, relations) {

        override val approximateManagerClassName = BASE_TABLE_MANAGER

        fun jkName() = "${name}_${idColumn.name}"

        fun jkColumnInfo(): ColumnInfo {
            val name = jkName()
            val type = idColumn.type
            val field = idColumn.origField
            return ColumnInfo(name, type, field, false, listOf())
        }

        fun idColIndex() = indexOfCol(idColumn)

        fun origFieldsInOrder(): List<JcField> {
            return JcTableInfoCollector(origClass.classpath).collectFields(origClass)
                .sortedBy { getColumnName(it) }
        }
    }

    fun columnsInOrder() = columns.sortedBy { it.name }

    fun relatedClasses(cp: JcClasspath) = relations.map { it.relatedDataclass(cp) }.distinctBy { it.name }

    fun insertColumn(col: ColumnInfo) { columns.add(col) }

    fun insertRelation(rel: Relation) { relations.add(rel) }

    fun indexOfCol(col: ColumnInfo) = columns.sortedBy { it.name }.indexOfFirst { it.name == col.name }

    fun indexOfField(field: JcField) = columns.sortedBy { it.name }.indexOfFirst { it.origField == field }

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

    data class Join(
        val colName: String
    )

    class JoinTable(
        val name: String,
        val joinCol: TableInfo.ColumnInfo,
        val inverseJoinCol: TableInfo.ColumnInfo
    ) {
        fun toTable(): TableInfo {
            return TableInfo(name, hashSetOf(joinCol, inverseJoinCol), hashSetOf())
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
        val join: Join,
        origField: JcField,
        cascadeType: List<CascadeType>
    ) : Relation(origField, cascadeType)

    class OneToOne(
        override val mappedBy: String?,
        join: Join,
        origField: JcField,
        cascadeType: List<CascadeType>
    ) : RelationByColumn(join, origField, cascadeType) {
        override fun toString(): String {
            return "\$OneToOne" + super.toString()
        }
    }

    class OneToManyByColumn(
        override val mappedBy: String?,
        val join: Join?,
        origField: JcField,
        cascadeType: List<CascadeType>
    ) : Relation(origField, cascadeType) {
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
        join: Join,
        origField: JcField,
        cascadeType: List<CascadeType>
    ) : RelationByColumn(join, origField, cascadeType) {
        override val mappedBy: String? = null

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

        private fun join(annotation: JcAnnotation) = annotation.values["name"].let { it as String? }?.let { Join(it) }
        private fun mappedBy(annotation: JcAnnotation) = annotation.values["mappedBy"] as String?
        private fun cascadeType(annotation: JcAnnotation, common: List<CascadeType> = listOf()) =
            (annotation.values["cascade"] as? List<*>)?.map { CascadeType.valueOf((it as JcField).name) } ?: common

        fun fromField(
            classTable: TableInfo.TableWithIdInfo,
            subTable: TableInfo.TableWithIdInfo,
            field: JcField
        ): Relation? {

            val simpleName = databaseName(field.name)
            val commonName = "${simpleName}_${subTable.idColumn.name}"
            val annotations = field.annotations
            val join = find(annotations, "JoinColumn")
                ?.let { join(it) }

            find(annotations, "OneToOne")
                ?.let {
                    val mappedBy = mappedBy(it)
                    val cascade = cascadeType(it, listOf(CascadeType.PERSIST, CascadeType.MERGE))
                    val j = join ?: Join(commonName)
                    return OneToOne(mappedBy, j, field, cascade)
                }

            find(annotations, "OneToMany")
                ?.let {
                    val mappedBy = mappedBy(it)
                    val cascade = cascadeType(it)
                    if (join == null && mappedBy == null) {
                        val name = getBtwTableName(classTable.origClass, field)
                        val jkClass = classTable.jkColumnInfo()
                        val jkSub = subTable.jkColumnInfo()
                        val table = JoinTable(name, jkClass, jkSub)
                        return OneToManyByTable(table, field, cascade)
                    }

                    return OneToManyByColumn(mappedBy, join, field, cascade)
                }

            find(annotations, "ManyToOne")
                ?.let {
                    val cascade = cascadeType(it, listOf(CascadeType.PERSIST, CascadeType.MERGE))
                    val j = join ?: Join(commonName)
                    return ManyToOne(j, field, cascade)
                }

            // TODO: several join columns
            find(annotations, "ManyToMany")
                ?.let {
                    val mappedBy = mappedBy(it)
                    val cascade = cascadeType(it)
                    val joinTableAnnot = find(annotations, "JoinTable")
                    val joinTableName = (joinTableAnnot?.values?.get("name") as String?)
                        ?: getBtwTableName(field.enclosingClass, field)
                    val jkClassName = (joinTableAnnot?.values?.get("JoinColumns") as List<*>?)
                        ?.first()?.let { a -> join(a as JcAnnotation) }
                        ?.colName
                        ?: classTable.jkName()
                    val jkClass = TableInfo.ColumnInfo(
                        jkClassName, classTable.idColumn.type, classTable.idColumn.origField, false, listOf()
                    )
                    val jkSubName = (joinTableAnnot?.values?.get("inverseJoinColumns") as List<*>?)
                        ?.first()?.let { a -> join(a as JcAnnotation) }
                        ?.colName
                        ?: simpleName
                    val jkSub = TableInfo.ColumnInfo(
                        jkSubName, subTable.idColumn.type, subTable.idColumn.origField, false, listOf()
                    )
                    val joinTable = JoinTable(joinTableName, jkClass, jkSub)
                    return ManyToMany(mappedBy, joinTable, field, cascade)
                }

            return null;
        }
    }
}
