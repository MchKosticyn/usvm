package org.usvm.jvm.rendering

import kotlin.math.max
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.void
import org.usvm.jvm.rendering.UTestInstTraverser.traverseInst
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst

data class JcTestVarRepr(val type: JcType, val name: String)
data class JcTestVarAssignStmt(val lhs: JcTestVarRepr, val rhs: UTestExpression)

class JcTestVarManager(val test: UTest, val declStrategy: DeclStrategy = DeclStrategy.FORCED) {
    enum class DeclStrategy {
        MINIMAL,
        FORCED
    }

    class NameManager {
        companion object {
            var cnt = 0
        }

        fun nameFor(expr: UTestExpression): String = "var$cnt".also { cnt++ }
    }

    private val nameManager: NameManager = NameManager()
    private val assnMapping: Map<UTestInst, List<JcTestVarAssignStmt>>
    private val varMapping: Map<UTestExpression, JcTestVarRepr>

    init {
        val instList = instsOf(test)
        var (cnt, usg) = preprocess(instList)
        val nonDeclPool = cnt.filter { entry -> entry.value == 1 }.keys//setOf<UTestExpression>()
        val varMappingCollector: MutableMap<UTestExpression, JcTestVarRepr> = mutableMapOf()
        val assnMappingCollector: MutableMap<UTestInst, MutableList<JcTestVarAssignStmt>> = mutableMapOf()
        usg.map { curInstUses ->
            curInstUses.filter { (expr, _) -> !nonDeclPool.contains(expr) && expr.type != expr.type?.classpath?.void }
                .toList().distinctBy { (expr, _) -> expr }
                .sortedByDescending { (_, depth) ->
                    depth
                }
        }.forEachIndexed { idx, inst ->
            val curInst = instList[idx]
            assnMappingCollector.put(curInst, mutableListOf())
            inst.forEach { (expr, _) ->
                if (!varMappingCollector.contains(expr)) {
                    val decl = JcTestVarRepr(expr.type!!, nameManager.nameFor(expr))
                    varMappingCollector.put(expr, decl)
                    assnMappingCollector.getOrPut(curInst) { mutableListOf() }.add(JcTestVarAssignStmt(decl, expr))
                }
            }
        }
        assnMapping = assnMappingCollector
        varMapping = varMappingCollector
    }

    fun asVar(expr: UTestExpression): JcTestVarRepr? = varMapping[expr]

    fun requiredDeclarationsBefore(inst: UTestInst): List<JcTestVarAssignStmt> =
        assnMapping.getOrElse(inst) { listOf() }

    companion object {
        private fun instsOf(test: UTest) = test.initStatements + test.callMethodExpression

        private fun preprocess(insts: List<UTestInst>): Pair<MutableMap<UTestInst, Int>, List<MutableMap<UTestExpression, Int>>> {
            val cnt: MutableMap<UTestInst, Int> = HashMap()
            val mapping: MutableList<HashMap<UTestExpression, Int>> = mutableListOf()
            insts.forEach { inst ->
                val curInstDecl = HashMap<UTestExpression, Int>()
                traverseInst(inst) { c, d ->
                    cnt.compute(c) { _, u -> 1 + (u ?: 0) }
                    if (c is UTestExpression)
                        curInstDecl.compute(c) { _, u ->
                            max(d, u ?: 0)
                        }
                }
                mapping.add(curInstDecl)
            }
            return cnt to mapping
        }
    }
}