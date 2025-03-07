package org.usvm.jvm.rendering

import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.stmt.Statement
import java.util.*
import kotlin.math.max
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.ext.void
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestConstExpression
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestNullExpression
import org.usvm.test.api.UTestSetFieldStatement

class JcTestInstCacheImpl(
    override val renderer: JcTestRendererOld,
) : JcTestInstCache {
    private val cache: IdentityHashMap<UTestExpression, Expression> = IdentityHashMap()
    private val instToRequiredDeclarations: IdentityHashMap<UTestInst, List<Statement>> = IdentityHashMap()

    private val varNameManager = renderer.varNameManager

    override fun initialize(test: UTest): UTest {
        val wrappersCollector: IdentityHashMap<UTestAllocateMemoryCall, MutableSet<UTestExpression>> = IdentityHashMap()
        val filteredInitStatements = test.initStatements.filter { inst ->
            if (inst !is UTestSetFieldStatement) return@filter true
            if (inst.field.name == "this$0" && inst.value is UTestNullExpression) return@filter false
            val lhs = inst.instance
            if (lhs is UTestAllocateMemoryCall && lhs.clazz.isPrimitiveWrapper()) {
                wrappersCollector.getOrPut(lhs) { mutableSetOf() }.add(inst.value)
                check(inst.value is UTestConstExpression<*>)
                return@filter false
            }
            true
        }
        check(wrappersCollector.values.all { it.size == 1 })
        val wrappersMapping = wrappersCollector.mapValues { entry -> renderer.renderConstExpression(entry.value.single() as UTestConstExpression<*>) }

        val exprCounter: IdentityHashMap<UTestExpression, Int> = IdentityHashMap()
        val declCollector: IdentityHashMap<UTestInst, IdentityHashMap<UTestExpression, Int>> = IdentityHashMap()
        (filteredInitStatements + test.callMethodExpression).forEachIndexed { index, inst ->
            declCollector[inst] = IdentityHashMap<UTestExpression, Int>()
            UTestInstTraverser.traverseInst(inst) { i, depth ->
                if (i is UTestExpression) {
                    exprCounter.compute(i) { _, v -> (v ?: 0) + 1 }
                    declCollector[inst]!!.compute(i) { _, u -> max(u ?: 0, depth) }
                }
            }
        }
        val exprNonDeclared =
            exprCounter.filter { (k, v) -> !wrappersMapping.contains(k) && (v > 1 || renderer.requireDeclarationOf(k))}.keys
        val exprCache: MutableMap<UTestExpression, String> = mutableMapOf()
        val declared = mutableSetOf<UTestExpression>()
        val declMapping = (filteredInitStatements + test.callMethodExpression).associateWith { inst ->
            val requireDecl = declCollector[inst]!!.entries.filter { (k, _) ->
                exprNonDeclared.contains(k) && !declared.contains(k) && k !is UTestConstExpression<*>
            }.sortedByDescending { e -> e.value }
            declared.addAll(requireDecl.map { entry -> entry.key })
            requireDecl.mapNotNull { entry ->
                if (entry.key.type == null || entry.key.type == entry.key.type!!.classpath.void) return@mapNotNull null
                renderer.renderVarDeclaration(
                    entry.key.type!!,
                    exprCache.getOrPut(entry.key) {
                        varNameManager.chooseNameFor(
                            entry.key
                        )
                    }, entry.key
                )
            }
        }

        cache.clear()
        cache.putAll(wrappersMapping + exprCache.mapValues { (_, name) -> NameExpr(name) })

        instToRequiredDeclarations.clear()
        instToRequiredDeclarations.putAll(declMapping)
        return UTest(filteredInitStatements, test.callMethodExpression)
    }

    private val wrappers = setOf(
        "java.lang.Boolean",
        "java.lang.Short",
        "java.lang.Integer",
        "java.lang.Long",
        "java.lang.Float",
        "java.lang.Double",
        "java.lang.Byte",
        "java.lang.Character"
    )

    private fun JcClassOrInterface.isPrimitiveWrapper(): Boolean = wrappers.contains(name)

    override fun getRequiredDeclarations(inst: UTestInst): List<Statement> =
        instToRequiredDeclarations[inst] ?: emptyList()

    override fun put(expr: UTestExpression): Expression =
        cache.computeIfAbsent(expr) { e -> NameExpr(varNameManager.chooseNameFor(e)) }

    override fun getOrElse(
        expr: UTestExpression,
        block: () -> Expression
    ): Expression = cache.getOrElse(expr) {
        block()
    }
}
