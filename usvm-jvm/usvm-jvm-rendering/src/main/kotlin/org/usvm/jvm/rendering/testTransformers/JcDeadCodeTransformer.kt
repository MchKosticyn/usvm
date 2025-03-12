package org.usvm.jvm.rendering.testTransformers

import org.usvm.jvm.rendering.testRenderer.JcTestVisitor
import org.usvm.jvm.rendering.testTransformers.JcTestTransformer
import java.util.*
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestArraySetStatement
import org.usvm.test.api.UTestCall
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestSetFieldStatement

class JcDeadCodeTransformer: JcTestTransformer() {
    private val reachable: MutableSet<UTestExpression> = Collections.newSetFromMap(IdentityHashMap())
    private var markReachable = false

    private class CallWithReachableExpressionLookup(
        private val reachable: MutableSet<UTestExpression>
    ) : JcTestVisitor() {
        var call: UTestCall? = null
        private var reachableFound = false

        fun reset() {
            call = null
        }

        override fun visit(expr: UTestExpression) {
            if (expr in reachable) {
                reachableFound = true
            }
            super.visit(expr)
        }

        override fun visit(call: UTestCall) {
            super.visit(call)
            if (reachableFound) {
                this.call = call
            }
        }
    }

    override fun transform(test: UTest): UTest {
        val callMethodExpression = withReachable { transformCall(test.callMethodExpression) } ?: error("")
        val filteredInitInstList = test.initStatements.reversed().flatMap {
            transformInstProxy(it)
        }.filterNotNull().reversed()
        return UTest(filteredInitInstList, callMethodExpression)
    }

    override fun transform(expr: UTestExpression): UTestExpression? {
        if (markReachable) {
            reachable.add(expr)
        }
        return super.transform(expr)
    }

    private fun transformInstProxy(inst: UTestInst): List<UTestInst?> {
        return when (inst) {
            is UTestArraySetStatement -> transformArraySet(inst)
            is UTestSetFieldStatement -> transformFieldSet(inst)
            else -> listOf(transformInst(inst))
        }
    }

    private fun transformArraySet(stmt: UTestArraySetStatement): List<UTestInst?> {
        if (stmt.arrayInstance in reachable) {
            return listOf(withReachable { super.transform(stmt) })
        }
        val callLookup = CallWithReachableExpressionLookup(reachable)

        callLookup.visit(stmt.index)
        val indexCall = callLookup.call?.let { call ->
            withReachable { super.transform(call) }
        }

        callLookup.reset()
        callLookup.visit(stmt.setValueExpression)
        val valueCall = callLookup.call?.let { call ->
            withReachable { super.transform(call) }
        }

        return listOf(indexCall, valueCall)
    }

    private fun transformFieldSet(stmt: UTestSetFieldStatement): List<UTestInst?> {
        if (stmt.instance in reachable) {
            return listOf(withReachable { super.transform(stmt) })
        }

        val callLookup = CallWithReachableExpressionLookup(reachable)
        callLookup.visit(stmt.value)
        val valueCall = callLookup.call?.let { call ->
            withReachable { super.transform(call) }
        }
        return listOf(valueCall)
    }

    private fun <T : UTestInst> withReachable(block: () -> T?): T? {
        markReachable = true
        val res = block()
        markReachable = false
        return res
    }
}
