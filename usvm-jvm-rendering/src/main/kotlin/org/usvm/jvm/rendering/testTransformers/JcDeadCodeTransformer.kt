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
import org.usvm.test.api.UTestStatement

class JcDeadCodeTransformer: JcTestTransformer() {
    private val roots: MutableSet<UTestExpression> = Collections.newSetFromMap(IdentityHashMap())
    private val reachable: MutableSet<UTestExpression> = Collections.newSetFromMap(IdentityHashMap())
    private lateinit var rootFetcher: ReachabilityRootFetcher
    private class ReachabilityRootFetcher(val roots: MutableSet<UTestExpression>): JcTestVisitor() {
        val fetched: MutableSet<UTestExpression> = Collections.newSetFromMap(IdentityHashMap())

        fun fetchFrom(expr: UTestExpression): MutableSet<UTestExpression> = fetchFrom(listOf(expr))
        fun fetchFrom(exprs: List<UTestExpression>): MutableSet<UTestExpression> {
            fetched.clear()
            exprs.forEach { expr ->
                visit(expr)
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

        fun prepareRootFetcher(): ReachabilityRootFetcher {
            super.visit(test)
            propagateWhilePossible(test)
            return ReachabilityRootFetcher(roots)
        }

        private fun propagateWhilePossible(test: UTest) {
            val clone: MutableSet<UTestExpression> = Collections.newSetFromMap(IdentityHashMap())

            do {
                clone.clear()
                clone.addAll(reachable)
                check(clone.size == reachable.size)
                (test.initStatements + test.callMethodExpression).forEach { inst ->
                    marker = false
                    visit(inst)
                }

            } while (clone.size != reachable.size)
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

        rootFetcher = ReachabilityCollector(test, roots, reachable).prepareRootFetcher()

        val callMethodExpression = transformCall(test.callMethodExpression) ?: error("call must be present in UTest")

        val filteredInitInstList = test.initStatements.flatMap {
            transformInstProxy(it)
        }.filterNotNull()

        return UTest(filteredInitInstList, callMethodExpression)
    }

    private fun transformExprs(expr: UTestExpression): List<UTestInst?>{
        if (expr in reachable)
            return listOf(super.transform(expr))

        return rootFetcher.fetchFrom(expr).map { super.transform(it) }
    }

    private fun transformInstProxy(inst: UTestInst): List<UTestInst?> {
        return when (inst) {
            is UTestArraySetStatement -> transformArraySet(inst)
            is UTestSetFieldStatement -> transformFieldSet(inst)
            is UTestExpression -> transformExprs(inst)
            else -> listOf(transformInst(inst))
        }
    }

    private fun transformArraySet(stmt: UTestArraySetStatement): List<UTestInst?> {
        return keepStatementOrFetchRoots(stmt, stmt.arrayInstance, listOf(stmt.index, stmt.setValueExpression))
    }

    private fun transformFieldSet(stmt: UTestSetFieldStatement): List<UTestInst?> {
        return keepStatementOrFetchRoots(stmt, stmt.instance, listOf(stmt.value))
    }

    private fun keepStatementOrFetchRoots(stmt: UTestStatement, instance: UTestExpression, targets: List<UTestExpression>): List<UTestInst?> {
        if (instance in reachable) return listOf(super.transform(stmt))
        return rootFetcher.fetchFrom(targets).map { super.transform(it) }
    }
    // TODO: handle static field set
}
