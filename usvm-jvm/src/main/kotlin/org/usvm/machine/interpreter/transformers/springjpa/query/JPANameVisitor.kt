package org.usvm.machine.interpreter.transformers.springjpa.query

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcType
import org.usvm.machine.interpreter.transformers.springjpa.getNextType
import java.beans.Introspector
import java.util.Objects
import java.util.Stack
import java.util.regex.Pattern

private fun split(text: String, keyword: String): Array<String> {
    val pattern = Pattern.compile(String.format("(%s)(?=(\\p{Lu}|\\P{InBASIC_LATIN}))", keyword))
    return pattern.split(text)
}

private fun hasText(str: String) = str.isNotEmpty() && str.any { it != ' ' };

class JPANameVisitor {

    val QUERY_PATTERN = "find|read|get|query|search|stream"
    val COUNT_PATTERN = "count"
    val EXISTS_PATTERN = "exists"
    val DELETE_PATTERN = "delete|remove"
    val PREFIX_TEMPLATE = Pattern.compile(
        "^($QUERY_PATTERN|$COUNT_PATTERN|$EXISTS_PATTERN|$DELETE_PATTERN)((\\p{Lu}.*?))??By"
    )

    val methodName: String
    val subj: Subject
    val pred: Predicate

    constructor(name: String, type: JcType) {
        val dash = name.indexOf(Char(45))

        methodName = if (dash > -1) name.substring(0, dash) else name

        val matcher = PREFIX_TEMPLATE.matcher(methodName)
        if (!matcher.find()) {
            subj = Subject(null)
            pred = Predicate(methodName, type)
        } else {
            subj = Subject(matcher.group(0))
            pred = Predicate(methodName.substring(matcher.group().length), type);
        }
    }
}

class Subject(val subj: String?) {
    val DISTINCT = "Distinct"
    val COUNT_BY_TEMPLATE = Pattern.compile("^count(\\p{Lu}.*?)??By")
    val EXISTS_BY_TEMPLATE = Pattern.compile("^(exists)(\\p{Lu}.*?)??By")
    val DELETE_BY_TEMPLATE = Pattern.compile("^(delete|remove)(\\p{Lu}.*?)??By")
    val LIMITING_QUERY_PATTERN = "(First|Top)(\\d*)?"
    val LIMITED_QUERY_TEMPLATE =
        Pattern.compile("^(find|read|get|query|search|stream)(Distinct)?(First|Top)(\\d*)?(\\p{Lu}.*?)??By")


    val distinct: Boolean
    val count: Boolean
    val exists: Boolean
    val delete: Boolean
    val maxResults: Int?

    init {
        distinct = subj?.contains(DISTINCT) ?: false
        count = match(COUNT_BY_TEMPLATE)
        exists = match(EXISTS_BY_TEMPLATE)
        delete = match(DELETE_BY_TEMPLATE)
        maxResults = returnMaxRes()
    }

    private fun returnMaxRes(): Int? {
        val grp = subj?.let { LIMITED_QUERY_TEMPLATE.matcher(it) }
        if (grp == null || !grp.find()) return null
        if (hasText(grp.group(4))) return grp.group(4).toInt()
        return 1
    }

    private fun match(pattern: Pattern) =
        subj?.let { pattern.matcher(it).find() } ?: false
}

class Predicate(val pred: String, type: JcType) {
    val ALL_IGNORE_CASE = Pattern.compile("AllIgnor(ing|e)Case")
    val ORDER_BY = "OrderBy"

    val nodes: List<OrPart>
    val orderBySource: OrderBySource
    val alwaysIgnoreCase: Boolean

    init {
        val (ignoreCase, p) = detectAllIgnoreCase()
        alwaysIgnoreCase = ignoreCase

        val parts = split(p, ORDER_BY)
        assert(parts.size < 3)

        nodes = split(parts[0], "Or").filter { hasText(it) }
            .map { OrPart(it, alwaysIgnoreCase, type) }
        orderBySource = OrderBySource(if (parts.size == 2) parts[1] else "", type)
    }

    private fun detectAllIgnoreCase(): Pair<Boolean, String> {
        val matcher = ALL_IGNORE_CASE.matcher(pred)
        if (matcher.find()) {
            val p = pred.substring(0, matcher.start()) + pred.substring(matcher.end(), pred.length)
            return true to p
        }

        return false to pred
    }
}

class OrPart(
    source: String,
    val ignoreCase: Boolean,
    type: JcType
) : Iterable<Part> {

    val parts = split(source, "And").filter { hasText(it) }
        .map { Part(it, ignoreCase, type) }

    override fun iterator() = parts.iterator()
}

class Part(source: String, ignoreCase: Boolean, type: JcType) {

    val IGNORE_CASE = Pattern.compile("Ignor(ing|e)Case")

    val case: IgnoreCase
    val propertyPath: PropPath
    val ptype: PartType

    init {
        val matcher = IGNORE_CASE.matcher(source);

        val mainPart = if (matcher.find()) {
            case = IgnoreCase.Always
            source.substring(0, matcher.start()) + source.substring(matcher.end(), source.length);
        } else {
            case = if (ignoreCase) IgnoreCase.WhenPossible else IgnoreCase.Never
            source
        }

        ptype = PartType.fromProp(mainPart)
        propertyPath = PropPath.from(ptype.extractProp(mainPart), type)
    }

    enum class IgnoreCase {
        Always, Never, WhenPossible
    }

    enum class PartType(val keywords: List<String>, val numOfArgs: Int) {

        Btw(2, "IsBetween", "Between"),
        IsNotNull(0, "IsNotNull", "NotNull"),
        IsNull(0, "IsNull", "Null"),
        Less(1, "IsLessThan", "LessThan"),
        LessEq(1, "IsLessThanEqual", "LessThanEqual"),
        Greater(1, "IsGreaterThan", "GreaterThan"),
        GreaterEq(1, "IsGreaterThanEqual", "GreaterThanEqual"),
        Before(1, "IsBefore", "Before"),
        After(1, "IsAfter", "After"),
        NotLike(1, "IsNotLike", "NotLike"),
        Like(1, "IsLike", "Like"),
        StartWith(1, "IsStartingWith", "StartingWith", "StartsWith"),
        EndWith(1, "IsEndingWith", "EndingWith", "EndsWith"),
        IsNotEmpty(0, "IsNotEmpty", "NotEmpty"),
        IsEmpty(0, "IsEmpty", "Empty"),
        NotContain(1, "IsNotContaining", "NotContaining", "NotContains"),
        Contain(1, "IsContaining", "Containing", "Contains"),
        NotIn(1, "IsNotIn", "NotIn"),
        In(1, "IsIn", "In"),
        Near(1, "IsNear", "Near"),
        Within(1, "IsWithin", "Within"),
        Regex(1, "MatchesRegex", "Matches", "Regex"),
        Exist(0, "Exists"),
        True(0, "IsTrue", "True"),
        False(0, "IfFalse", "False"),
        NegSimpleProp(1, "IsNot", "Not"),
        SimpleProp(1, "Is", "Equals");

        constructor(numOfArgs: Int, vararg keywords: String) : this(keywords.toList(), numOfArgs)

        companion object {

            val ALL: List<PartType> by lazy { initAll() }

            fun initAll(): List<PartType> {
                return listOf(
                    IsNotNull, IsNull, Btw, Less, LessEq, Greater, GreaterEq, Before, After, NotLike, Like,
                    StartWith, EndWith, IsNotEmpty, IsEmpty, NotContain, Contain, NotIn, In, Near, Within, Regex,
                    True, False, NegSimpleProp, SimpleProp
                )
            }

            fun fromProp(prop: String): PartType {
                for (t in ALL) {
                    if (t.support(prop)) {
                        return t
                    }
                }
                return SimpleProp
            }
        }

        fun support(prop: String): Boolean {
            for (kw in keywords) {
                if (prop.endsWith(kw)) return true
            }
            return false;
        }

        fun extractProp(prop: String): String {
            val candidate = Introspector.decapitalize(prop)
            for (kw in keywords) {
                if (candidate.endsWith(kw)) {
                    return candidate.substring(0, candidate.length - kw.length)
                }
            }
            return candidate
        }
    }

    class PropPath(val type: JcType) {

        private lateinit var next: PropPath
        private lateinit var name: String
        private lateinit var propType: JcType

        constructor(name: String, type: JcType, base: List<PropPath>) : this(type) {
            val decap = Introspector.decapitalize(name)
            val prop = Prop.lookupProperty(type, decap) ?: Prop.lookupProperty(type, name.lowercase())!!

            this.name = prop.path
            this.propType = prop.type
        }

        companion object {

            val PARSE_DEPTH_EXCEEDED =
                "Trying to parse a path with depth greater than 1000; This has been disabled for security reasons to prevent parsing overflows"
            val DELIMITERS = "_\\."
            val SPLITTER = Pattern.compile("(?:[%s]?([%s]*?[^%s]+))".replace("%s", DELIMITERS))
            val SPLITTER_FOR_QUOTED = Pattern.compile("(?:[%s]?([%s]*?[^%s]+))".replace("%s", "\\."))
            val NESTED_PROPERTY_PATTERN = Pattern.compile("\\p{Lu}[\\p{Ll}\\p{Nd}]*$")

            val cache: MutableMap<Prop, PropPath> = mutableMapOf()

            fun from(path: String, type: JcType): PropPath {
                val prop = Prop(path, type)
                cache.get(prop)?.also { return it }

                val iterSource = mutableListOf<String>()
                val matcher = if (isQuoted(path)) {
                    SPLITTER_FOR_QUOTED.matcher(path.replace("\\Q", "").replace("\\E", ""))
                } else {
                    SPLITTER.matcher("_$path")
                }

                while (matcher.find()) {
                    iterSource.add(matcher.group(1))
                }

                var res: PropPath? = null
                val curr = Stack<PropPath>()

                for (part in iterSource) {
                    if (res == null) {
                        res = create(part, type, curr)
                        curr.push(res)
                    } else {
                        curr.push(create(part, curr))
                    }
                }

                if (res == null) {
                    throw IllegalStateException(
                        String.format(
                            "Expected parsing to yield a PropertyPath from %s but got null",
                            path
                        )
                    )
                }

                cache.set(prop, res)
                return res
            }

            fun isQuoted(source: String): Boolean {
                return source.matches("^\\\\Q.*\\\\E$".toRegex())
            }

            fun create(source: String, base: Stack<PropPath>): PropPath {
                val prev = base.peek()
                val prop = create(source, prev.type.getNextType, base)
                prev.next = prop
                return prop
            }

            fun create(source: String, type: JcType, base: Stack<PropPath>) =
                create(source, type, "", base);

            fun create(source: String, type: JcType, tail: String, base: Stack<PropPath>): PropPath {
                if (base.size > 1000) {
                    throw IllegalArgumentException(PARSE_DEPTH_EXCEEDED)
                }

                val curr = PropPath(source, type, base)
                if (base.isNotEmpty()) {
                    base.get(base.size - 1).next = curr
                }

                val newBase = Stack<PropPath>()
                base.toList().reversed().forEach { newBase.add(it) }
                newBase.add(curr)

                if (hasText(tail)) {
                    curr.next = create(tail, curr.propType, newBase)
                }

                return curr
            }
        }

    }

    class Prop(val path: String, val type: JcType) {

        companion object {
            fun lookupProperty(type: JcType, name: String): Prop? {
                val propType = getProp(type, name.split("."))
                return propType?.let { Prop(name, it) }
            }

            private fun getProp(type: JcType, name: List<String>): JcType? {
                if (name.size == 1) {
                    return type
                }

                val t = (type as JcClassType).declaredFields.find { it.name == name[1] }
                return t?.let { getProp(it.type, name.drop(1)) }
            }
        }

        override fun hashCode() = Objects.hash(path, type)
    }
}

class OrderBySource(
    clause: String,
    val type: JcType
) {

    val BLOCK_SPLIT = "(?<=Asc|Desc)(?=\\p{Lu})"
    val DIRECTION_SPLIT = Pattern.compile("(.+?)(Asc|Desc)?$")
    val INVALID_ORDER_SYNTAX = "Invalid order syntax for part %s"
    val DIRECTION_KEYWORDS = listOf("Asc", "Desc")

    val orders: List<Order>

    init {
        orders = if (clause == "") listOf()
        else clause.split(BLOCK_SPLIT).map { part ->
            val matcher = DIRECTION_SPLIT.matcher(part)

            if (matcher.find()) {
                throw IllegalArgumentException(String.format(INVALID_ORDER_SYNTAX, part))
            }

            val prop = matcher.group(1)
            val dir = matcher.group(2)

            if (DIRECTION_KEYWORDS.contains(prop) && dir == null) {
                throw IllegalArgumentException(String.format(INVALID_ORDER_SYNTAX, part))
            }

            Order(prop, dir, type)
        }
    }

    class Order(val prop: String, d: String?, val type: JcType) {
        val actualProp = Part.PropPath.from(prop, type)
        val dir = d == null || d == "" || d == "Asc"
    }
}
