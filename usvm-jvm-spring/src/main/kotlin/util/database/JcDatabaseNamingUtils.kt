package util.database

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcField

fun databaseName(name: String): String {
    val newName = name
        .replace(".", "_")
        .replace(Regex("[a-z][A-Z]"))
        { mr -> mr.value[0] + "_" + mr.value[1] }
        .lowercase()

    return newName
}

fun getTableName(clazz: JcClassOrInterface): String {
    val name = clazz.annotations
        .find { nameEquals(it, "Table") }
        ?.values?.get("name")
        ?: databaseName(clazz.simpleName)

    return name as String
}

fun getColumnName(field: JcField): String {
    val name = field.annotations
        .find { nameEquals(it, "Column") }
        ?.values?.get("name")
        ?: databaseName(field.name)

    return name as String
}

// name of manyToMany table (it is between two tables)
fun getBtwTableName(clazz: JcClassOrInterface, field: JcField) = "${getTableName(clazz)}_${getColumnName(field)}"

fun getBtwTableName(field: JcField) = getBtwTableName(field.enclosingClass, field)

fun getSetFieldName(rel: Relation) = "\$${rel.origField.name}_id_set"
