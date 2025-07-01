package machine.interpreter.transformers.springjpa.query

import machine.JcConcreteMemoryClassLoader
import org.jacodb.api.jvm.JcClassOrInterface
import org.springframework.data.mapping.PropertyPath
import org.springframework.data.repository.query.parser.Part
import org.springframework.data.repository.query.parser.PartTree
import org.usvm.jvm.util.toJavaClass

private fun StringBuilder.appendWithSpaces(str: String): StringBuilder {
    append(" ")
    append(str)
    append(" ")
    return this
}

// https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html
class JPANameTranslator(
    val methodName: String,
    targetClass: JcClassOrInterface
) {

    val classJava = targetClass.toJavaClass(JcConcreteMemoryClassLoader)
    val queryClassName = targetClass.name
    val classAlias = targetClass.simpleName.lowercase()

    private var lastArgumentNum = 0

    fun freshArgumentNum() = ++lastArgumentNum

    fun buildQuery() = buildString {
        val partTree = PartTree(methodName, classJava)

        val subj = buildSubject(partTree)
        if (partTree.isEmpty) {
            append(subj(""))
            return@buildString
        }

        val query = { s: String -> buildOrParts(partTree)(s).let(subj) }
        appendWithSpaces("WHERE")
        appendWithSpaces(query(""))


        // TODO: sorting
        //val order = partTree.sort
    }

    fun buildSubject(partTree: PartTree) = { s: String ->
        buildString {
            // TODO: other statments
            appendWithSpaces("SELECT")
            if (partTree.isDistinct) appendWithSpaces("DISTINCT")
            appendWithSpaces(classAlias)
            appendWithSpaces("FROM")
            appendWithSpaces(queryClassName)
            appendWithSpaces(classAlias)
            appendWithSpaces(s)
        }
    }

    private fun <T> buildWithSep(values: Iterable<T>, sep: String, builder: (T) -> (String) -> String) =
        if (values.count() == 1) builder(values.single())
        else { s: String ->
            val last = values.reversed().first().let { builder(it) }(s)
            values.reversed().drop(1).fold(last) { acc, part -> builder(part)(" $sep $acc") }
        }

    fun buildOrParts(tree: PartTree) = buildWithSep(tree, "OR", ::buildOrPart)

    fun buildOrPart(part: PartTree.OrPart) = buildWithSep(part, "AND", ::buildAndPart)

    fun buildAndPart(part: Part) = { s: String ->
        buildString {
            val prop = buildProp(part.property)
            val type = buildType(part.type)

            appendWithSpaces(prop)
            appendWithSpaces(type)
            appendWithSpaces(s)
        }
    }

    fun buildProp(prop: PropertyPath) = buildString {
        // TODO: isCollection, ignoreCase?
        append(classAlias)

        // getSegment returns Book.id or Author.firstName
        val classProp = prop.segment.dropWhile { it != '.' }
        check(classProp.isNotBlank())
        append(classProp)

    }

    fun buildType(type: Part.Type) = buildString {
        appendWithSpaces(buildOp(type))
        appendWithSpaces(buildOpArgs(type))
    }

    fun buildOp(type: Part.Type) =
        when (type) {
            Part.Type.BETWEEN -> "BETWEEN"
            Part.Type.IS_NOT_NULL -> "IS NOT NULL"
            Part.Type.IS_NULL -> "IS NULL"
            Part.Type.LESS_THAN -> "<"
            Part.Type.LESS_THAN_EQUAL -> "<="
            Part.Type.GREATER_THAN -> ">"
            Part.Type.GREATER_THAN_EQUAL -> ">="

            // date
            Part.Type.BEFORE -> "<"
            Part.Type.AFTER -> ">"

            Part.Type.NOT_LIKE -> "NOT LIKE"
            Part.Type.LIKE -> "LIKE"
            Part.Type.STARTING_WITH -> "LIKE"
            Part.Type.ENDING_WITH -> "LIKE"

            // TODO: think about HQL collections functions
            Part.Type.IS_NOT_EMPTY -> TODO()
            Part.Type.IS_EMPTY -> TODO()

            Part.Type.NOT_CONTAINING -> "NOT LIKE"
            Part.Type.CONTAINING -> "LIKE"

            Part.Type.NOT_IN -> "NOT IN"
            Part.Type.IN -> "IN"

            // TODO:
            Part.Type.NEAR -> TODO()
            Part.Type.WITHIN -> TODO()
            Part.Type.REGEX -> TODO()
            Part.Type.EXISTS -> TODO()

            Part.Type.TRUE -> " = TRUE"
            Part.Type.FALSE -> " = FALSE"
            Part.Type.NEGATING_SIMPLE_PROPERTY -> "IS NOT"
            Part.Type.SIMPLE_PROPERTY -> "IS"
        }

    fun buildOpArgs(type: Part.Type) = buildString {
        check(type.numberOfArguments < 3)
        when (type.numberOfArguments) {
            // only BETWEEN
            2 -> {
                appendWithSpaces("?${freshArgumentNum()}")
                appendWithSpaces("AND")
                appendWithSpaces("?${freshArgumentNum()}")
            }

            1 -> {
                append(" ")
                if (listOf(
                        Part.Type.ENDING_WITH,
                        Part.Type.CONTAINING,
                        Part.Type.NOT_CONTAINING
                    ).contains(type)
                ) append("%")
                append("?${freshArgumentNum()}")
                if (listOf(
                        Part.Type.STARTING_WITH,
                        Part.Type.CONTAINING,
                        Part.Type.NOT_CONTAINING
                    ).contains(type)
                ) append("%")
                append(" ")
            }

            0 -> ""
        }
    }
}
