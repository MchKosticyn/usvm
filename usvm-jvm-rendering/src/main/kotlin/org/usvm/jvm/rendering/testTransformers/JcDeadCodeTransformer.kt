package org.usvm.jvm.rendering.testTransformers

import org.usvm.jvm.rendering.testRenderer.JcTestVisitor
import java.util.*
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestArraySetStatement
import org.usvm.test.api.UTestCall
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestGlobalMock
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestMockObject
import org.usvm.test.api.UTestSetFieldStatement
import org.usvm.test.api.UTestSetStaticFieldStatement
import org.usvm.test.api.UTestStatement

class JcDeadCodeTransformer: JcTestTransformer() {
    private val roots: MutableSet<UTestExpression> = Collections.newSetFromMap(IdentityHashMap())
    private val reachable: MutableSet<UTestExpression> = Collections.newSetFromMap(IdentityHashMap())

    private val rootFetcher get() = ReachabilityRootFetcher(roots)

    private class ReachabilityRootFetcher(val roots: MutableSet<UTestExpression>): JcTestVisitor() {
        val fetched: MutableSet<UTestExpression> = Collections.newSetFromMap(IdentityHashMap())

        fun fetchFrom(expr: UTestExpression): MutableSet<UTestExpression> = fetchFrom(listOf(expr))
        fun fetchFrom(exprs: List<UTestExpression>): MutableSet<UTestExpression> {
            fetched.clear()

            for (expr in exprs) {
                cache.clear()
                visitExpr(expr)
            }

            return fetched
        }

        override fun visit(expr: UTestExpression) {
            if (expr in roots) {
                fetched.add(expr)
                return
            }
            super.visit(expr)
        }
    }

    private class ReachabilityCollector(
        val test: UTest,
        val roots: MutableSet<UTestExpression>,
        val reachable: MutableSet<UTestExpression>
    ) : JcTestVisitor() {
        private var marker = false

        fun prepareRootFetcher(){
            super.visit(test)
            propagateWhilePossible(test)
        }

        private fun propagateWhilePossible(test: UTest) {
            val clone: MutableSet<UTestExpression> = Collections.newSetFromMap(IdentityHashMap())

            do {
                clone.clear()
                cache.clear()
                clone.addAll(reachable)
                check(clone.size == reachable.size)
                (test.initStatements + test.callMethodExpression).forEach { inst ->
                    marker = false
                    visit(inst)
                }

            } while (clone.size != reachable.size)
        }

        override fun visit(stmt: UTestSetStaticFieldStatement) {
            if (stmt.value in roots) return

            roots.add(stmt.value)
            reachable.add(stmt.value)
            withReachable { super.visit(stmt) }
        }

        override fun visit(stmt: UTestSetFieldStatement) {
            if (stmt.instance in reachable || stmt.value in reachable) {
                withReachable { super.visit(stmt) }
                return
            }
            super.visit(stmt)
        }

        override fun visit(stmt: UTestArraySetStatement) {
            if (stmt.arrayInstance in reachable || stmt.setValueExpression in reachable) {
                withReachable { super.visit(stmt) }
                return
            }
            super.visit(stmt)
        }

        override fun visit(expr: UTestExpression) {
            if (expr in reachable) return

            if (marker) {
                reachable.add(expr)
                withReachable { super.visit(expr) }
                return
            }

            super.visit(expr)
        }

        override fun visit(call: UTestCall) {
            if (call is UTestAllocateMemoryCall || call in roots) return
            roots.add(call)
            reachable.add(call)
            withReachable { super.visit(call) }
        }

        override fun visit(expr: UTestMockObject) {
            if (expr in roots) return

            roots.add(expr)
            reachable.add(expr)
            withReachable { super.visit(expr) }
        }

        override fun visit(expr: UTestGlobalMock) {
            if (expr in roots) return

            roots.add(expr)
            reachable.add(expr)
            withReachable { super.visit(expr) }
        }

        private fun withReachable(block: () -> Unit) {
            val prevReachability = marker
            marker = true
            try {
                block()
            } finally {
                marker = prevReachability
            }
        }
    }

    override fun transform(test: UTest): UTest {

        ReachabilityCollector(test, roots, reachable).prepareRootFetcher()

        val filteredInitInstList = test.initStatements.flatMap {
            transformInstProxy(it)
        }.filterNotNull()

        val callMethodExpression = transformCall(test.callMethodExpression) ?: error("call must be present in UTest")

        return UTest(filteredInitInstList, callMethodExpression)
    }

    private fun transformExprs(expr: UTestExpression): List<UTestInst?>{
        if (expr in reachable)
            return listOf(expr)

        return rootFetcher.fetchFrom(expr).toList()
    }

    private fun transformInstProxy(inst: UTestInst): List<UTestInst?> {
        return when (inst) {
            is UTestArraySetStatement -> transformArraySet(inst)
            is UTestSetFieldStatement -> transformFieldSet(inst)
            is UTestExpression -> transformExprs(inst)
            else -> listOf(inst)
        }
    }

    private fun transformArraySet(stmt: UTestArraySetStatement): List<UTestInst?> {
        return keepStatementOrFetchRoots(stmt, stmt.arrayInstance, listOf(stmt.arrayInstance, stmt.index, stmt.setValueExpression))
    }

    private fun transformFieldSet(stmt: UTestSetFieldStatement): List<UTestInst?> {
        return keepStatementOrFetchRoots(stmt, stmt.instance, listOf(stmt.instance, stmt.value))
    }

    private fun keepStatementOrFetchRoots(stmt: UTestStatement, instance: UTestExpression, targets: List<UTestExpression>): List<UTestInst?> {
        if (instance in reachable) return listOf(stmt)
        return rootFetcher.fetchFrom(targets).toList()
    }
}
