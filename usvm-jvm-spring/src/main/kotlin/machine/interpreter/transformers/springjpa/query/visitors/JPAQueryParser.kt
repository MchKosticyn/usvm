package machine.interpreter.transformers.springjpa.query.visitors

import jpa.InstanceWrapper
import jpa.callStaticMethod
import jpa.reloadJpaTerm
import machine.interpreter.transformers.springjpa.query.visitors.JPAVisitor.Companion.nameOfApproximation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.approximation.Approximations
import org.jacodb.approximation.OriginalClassName
import org.usvm.jvm.util.transformers.JcSingleInstructionTransformer
import org.usvm.spring.query.Select

// antlr
private const val CHAR_STREAMS = "org.antlr.v4.runtime.CharStreams"
private const val COMMON_TOKEN_STREAM = "org.antlr.v4.runtime.CommonTokenStream"

// hql
private const val HQL_LEXER = "org.hibernate.grammars.hql.HqlLexer"
private const val HQL_PARSER = "org.hibernate.grammars.hql.HqlParser"

private const val QUERY_VISITOR = "generated.org.springframework.boot.databases.query.visitors.CommonHQLVisitor"

private class HqlLexer : InstanceWrapper {
    constructor(cp: JcClasspath, instance: Any) : super(cp, HQL_LEXER, instance)
    constructor(cp: JcClasspath, args: List<Any>) : super(cp, HQL_LEXER, args)
}

private class CommonTokenStream : InstanceWrapper {
    constructor(cp: JcClasspath, instance: Any) : super(cp, COMMON_TOKEN_STREAM, instance)
    constructor(cp: JcClasspath, args: List<Any>) : super(cp, COMMON_TOKEN_STREAM, args)
}

private class HqlParser : InstanceWrapper {
    constructor(cp: JcClasspath, instance: Any) : super(cp, HQL_PARSER, instance)
    constructor(cp: JcClasspath, args: List<Any>) : super(cp, HQL_PARSER, args)

    fun getStatement() = callMethod("statement", emptyList())!!
}

private class JPAVisitor : InstanceWrapper {
    constructor(cp: JcClasspath, instance: Any) : super(cp, nameOfApproximation(cp, QUERY_VISITOR), instance)
    constructor(cp: JcClasspath, args: List<Any>) : super(cp, nameOfApproximation(cp, QUERY_VISITOR), args)

    fun visit(ctxNode: Any) = callMethod("visit", listOf(ctxNode)) { !it.isAbstract }

    companion object {
        fun nameOfApproximation(cp: JcClasspath, className: String) =
            cp.features!!.filterIsInstance<Approximations>().single()
                .findApproximationByOriginOrNull(OriginalClassName(className))!!
    }
}

class JPAQueryBuilder(
    val cp: JcClasspath,
    val query: String
) {
    fun buildTerms(): Select {
        val input = callStaticMethod(cp, "fromString", CHAR_STREAMS, listOf(query))!!

        val lexer = HqlLexer(cp, listOf(input))
        val tokenStream = CommonTokenStream(cp, listOf(lexer.getInstance()))
        val parser = HqlParser(cp, listOf(tokenStream.getInstance()))
        val statement = parser.getStatement()

        val visitor = JPAVisitor(cp, emptyList())
        val select = reloadJpaTerm(visitor.visit(statement)) as Select
        return select
    }
}
