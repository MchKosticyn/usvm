package machine.interpreter.transformers.springjpa.query.visitors

import jpa.InstanceWrapper
import machine.JcConcreteMemoryClassLoader
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.util.toJavaClass

private const val PART_TREE = "org.springframework.data.repository.query.parser.PartTree"
private const val OR_PART = "org.springframework.data.repository.query.parser.PartTree\$OrPart"
private const val PART = "org.springframework.data.repository.query.parser.Part"
private const val PROPERTY_PATH = "org.springframework.data.mapping.PropertyPath"
private const val TYPE = "org.springframework.data.repository.query.parser.Part\$Type"

private fun StringBuilder.appendWithSpaces(str: String): StringBuilder {
    append(" ")
    append(str)
    append(" ")
    return this
}

private class PartTree : InstanceWrapper {
    constructor(cp: JcClasspath, instance: Any) : super(cp, PART_TREE, instance)
    constructor(cp: JcClasspath, args: List<Any>) : super(cp, PART_TREE, args)

    fun isEmpty() = callMethod("isEmpty", emptyList()) as Boolean
    fun isDistinct() = callMethod("isDistinct", emptyList()) as Boolean
    fun toList() = (callMethod("toList", emptyList())!! as List<*>).map { OrPart(cp, it!!) }

    class OrPart: InstanceWrapper {
        constructor(cp: JcClasspath, instance: Any) : super(cp, OR_PART, instance)
        constructor(cp: JcClasspath, vararg args: Any) : super(cp, OR_PART, args)

        fun toList() = (callMethod("toList", emptyList())!! as List<*>).map { Part(cp, it!!) }
    }

    class Part : InstanceWrapper {
        constructor(cp: JcClasspath, instance: Any) : super(cp, PART, instance)
        constructor(cp: JcClasspath, args: List<Any>) : super(cp, PART, args)

        fun getProperty() = PropertyPath(cp, getFieldValue("propertyPath")!!)
        fun getType() = Type(cp, getFieldValue("type")!!)
    }

    class PropertyPath : InstanceWrapper {
        constructor(cp: JcClasspath, instance: Any) : super(cp, PROPERTY_PATH, instance)
        constructor(cp: JcClasspath, args: List<Any>) : super(cp, PROPERTY_PATH, args)

        fun getSegment() = callMethod("getSegment", emptyList())!! as String
    }

    class Type : InstanceWrapper {
        constructor(cp: JcClasspath, instance: Any) : super(cp, TYPE, instance)
        constructor(cp: JcClasspath, args: List<Any>) : super(cp, TYPE, args)

        fun getOrdinal() = getFieldValue("ordinal")!! as Int
        fun getName() = getFieldValue("name")!! as String
        fun getNumberOfArguments() = getFieldValue("numberOfArguments")!! as Int
    }
}

// https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html
class JPANameTranslator(
    val methodName: String,
    targetClass: JcClassOrInterface
) {

    val cp = targetClass.classpath

    val targetClass = targetClass.toJavaClass(JcConcreteMemoryClassLoader)
    val queryClassName = targetClass.simpleName
    val classAlias = queryClassName.lowercase()

    private var lastArgumentNum = 0

    fun freshArgumentNum() = lastArgumentNum++

    fun buildQuery() = buildString {
        val partTree = PartTree(cp, listOf(methodName, targetClass))

        val subj = buildSubject(partTree)
        if (partTree.isEmpty()) {
            append(subj(""))
            return@buildString
        }

        val query = { s: String -> buildOrParts(partTree)(s).let(subj) }
        appendWithSpaces(query(""))

        // TODO: sorting
    }.replace("\\s+".toRegex(), " ").trim()

    private fun buildSubject(partTree: PartTree) = { s: String ->
        buildString {
            // TODO: other statements
            appendWithSpaces("SELECT")
            if (partTree.isDistinct()) appendWithSpaces("DISTINCT")
            appendWithSpaces(classAlias)
            appendWithSpaces("FROM")
            appendWithSpaces(queryClassName)
            appendWithSpaces(classAlias)

            if (s.isNotBlank()) {
                appendWithSpaces("WHERE")
                appendWithSpaces(s)
            }
        }
    }


    private fun <T> buildWithSep(values: List<T>, sep: String, builder: (T) -> (String) -> String) =
        values.first().let(builder).let {
            values.drop(1).fold(it) { acc, part ->
                { s: String -> "${acc("")} $sep ${builder(part)(s)}" }
            }
        }

    private fun buildOrParts(tree: PartTree) = buildWithSep(tree.toList(), "OR", ::buildOrPart)

    private fun buildOrPart(part: PartTree.OrPart) = buildWithSep(part.toList(), "AND", ::buildAndPart)

    private fun buildAndPart(part: PartTree.Part) = { s: String ->
        buildString {
            val prop = buildProp(part.getProperty())
            val type = buildType(part.getType())

            appendWithSpaces(prop)
            appendWithSpaces(type)
            appendWithSpaces(s)
        }
    }

    private fun buildProp(prop: PartTree.PropertyPath) = buildString {
        // TODO: isCollection, ignoreCase?

        append(classAlias)
        append(".")

        val classProp = prop.getSegment()
        check(classProp.isNotBlank())
        append(classProp)
    }

    private fun buildType(type: PartTree.Type) = buildString {
        appendWithSpaces(buildOp(type))
        appendWithSpaces(buildOpArgs(type))
    }

    private fun buildOp(type: PartTree.Type) =
        when (type.getName()) {
            "BETWEEN" -> "BETWEEN"
            "IS_NOT_NULL" -> "IS NOT NULL"
            "IS_NULL" -> "IS NULL"
            "LESS_THAN" -> "<"
            "LESS_THAN_EQUAL" -> "<="
            "GREATER_THAN" -> ">"
            "GREATER_THAN_EQUAL" -> ">="

            // date
            "BEFORE" -> "<"
            "AFTER" -> ">"

            "NOT_LIKE" -> "NOT LIKE"
            "LIKE" -> "LIKE"
            "STARTING_WITH" -> "LIKE"
            "ENDING_WITH" -> "LIKE"

            // TODO: think about HQL collections functions
            "IS_NOT_EMPTY" -> TODO()
            "IS_EMPTY" -> TODO()

            "NOT_CONTAINING" -> "NOT LIKE"
            "CONTAINING" -> "LIKE"

            "NOT_IN" -> "NOT IN"
            "IN" -> "IN"

            // TODO:
            "NEAR" -> TODO()
            "WITHIN" -> TODO()
            "REGEX" -> TODO()
            "EXISTS" -> TODO()

            "TRUE" -> " = TRUE"
            "FALSE" -> " = FALSE"
            "NEGATING_SIMPLE_PROPERTY" -> "!="
            "SIMPLE_PROPERTY" -> "="
            else -> error("Not expected type in PartTree")
        }

    private fun buildOpArgs(type: PartTree.Type) = buildString {
        val numberOfArguments = type.getNumberOfArguments()
        check(numberOfArguments < 3)
        when (numberOfArguments) {
            // only BETWEEN
            2 -> {
                appendWithSpaces("?${freshArgumentNum()}")
                appendWithSpaces("AND")
                appendWithSpaces("?${freshArgumentNum()}")
            }

            1 -> {
                val typeName = type.getName()
                append(" ")
                if (listOf("ENDING_WITH", "CONTAINING", "NOT_CONTAINING").contains(typeName)) append("%")
                append("?${freshArgumentNum()}")
                if (listOf("STARTING_WITH", "CONTAINING", "NOT_CONTAINING").contains(typeName)) append("%")
                append(" ")
            }

            0 -> ""
        }
    }
}
