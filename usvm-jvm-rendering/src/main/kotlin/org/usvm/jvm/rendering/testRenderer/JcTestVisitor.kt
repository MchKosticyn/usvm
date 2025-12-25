package org.usvm.jvm.rendering.testRenderer

import org.usvm.test.api.*
import java.util.Collections
import java.util.IdentityHashMap

open class JcTestVisitor {

    private val cache: MutableSet<UTestInst> = Collections.newSetFromMap(IdentityHashMap())

    protected fun isVisited(inst: UTestInst) = cache.contains(inst)

    protected fun clearVisited() = cache.clear()

    fun visit(test: UTest) {
        for (inst in test.initStatements)
            visit(inst)

        visit(test.callMethodExpression as UTestInst)
    }

    open fun visit(inst: UTestInst) {
        when (inst) {
            is UTestExpression -> visitExpr(inst)
            is UTestStatement -> visitStmt(inst)
        }
    }

    protected open fun visitExpr(expr: UTestExpression) {
        if (!cache.add(expr))
            return

        visit(expr)
    }

    open fun visit(expr: UTestExpression) {
        when (expr) {
            is UTestArithmeticExpression -> visit(expr)
            is UTestArrayGetExpression -> visit(expr)
            is UTestArrayLengthExpression -> visit(expr)
            is UTestBinaryConditionExpression -> visit(expr)
            is UTestCastExpression -> visit(expr)
            is UTestCreateArrayExpression -> visit(expr)
            is UTestGetFieldExpression -> visit(expr)
            is UTestCall -> visit(expr)
            is UTestClassExpression -> visit(expr)
            is UTestBooleanExpression -> visit(expr)
            is UTestByteExpression -> visit(expr)
            is UTestCharExpression -> visit(expr)
            is UTestDoubleExpression -> visit(expr)
            is UTestFloatExpression -> visit(expr)
            is UTestIntExpression -> visit(expr)
            is UTestLongExpression -> visit(expr)
            is UTestNullExpression -> visit(expr)
            is UTestShortExpression -> visit(expr)
            is UTestStringExpression -> visit(expr)
            is UTestGetStaticFieldExpression -> visit(expr)
            is UTestGlobalMock -> visit(expr)
            is UTestMockObject -> visit(expr)
            is UTestInstList -> visit(expr)
            is UTestMockInst -> visit(expr)
        }
    }

    protected open fun visitStmt(stmt: UTestStatement) {
        if (!cache.add(stmt))
            return

        visit(stmt)
    }

    open fun visit(stmt: UTestStatement) {
        when (stmt) {
            is UTestArraySetStatement -> visit(stmt)
            is UTestBinaryConditionStatement -> visit(stmt)
            is UTestSetFieldStatement -> visit(stmt)
            is UTestSetStaticFieldStatement -> visit(stmt)
        }
    }

    //region Expressions

    open fun visit(expr: UTestArithmeticExpression) {
        visitExpr(expr.lhv)
        visitExpr(expr.rhv)
    }

    open fun visit(expr: UTestArrayGetExpression) {
        visitExpr(expr.arrayInstance)
        visitExpr(expr.index)
    }

    open fun visit(expr: UTestArrayLengthExpression) {
        visitExpr(expr.arrayInstance)
    }

    open fun visit(expr: UTestBinaryConditionExpression) {
        visitExpr(expr.lhv)
        visitExpr(expr.rhv)
        visitExpr(expr.trueBranch)
        visitExpr(expr.elseBranch)
    }

    open fun visit(expr: UTestCastExpression) {
        visitExpr(expr.expr)
    }

    open fun visit(expr: UTestCreateArrayExpression) {
        visitExpr(expr.size)
    }

    open fun visit(expr: UTestGetFieldExpression) {
        visitExpr(expr.instance)
    }

    open fun visit(expr: UTestGetStaticFieldExpression) { }

    open fun visit(expr: UTestClassExpression) { }

    open fun visit(expr: UTestBooleanExpression) { }
    open fun visit(expr: UTestByteExpression) { }
    open fun visit(expr: UTestCharExpression) { }
    open fun visit(expr: UTestDoubleExpression) { }
    open fun visit(expr: UTestFloatExpression) { }
    open fun visit(expr: UTestIntExpression) { }
    open fun visit(expr: UTestLongExpression) { }
    open fun visit(expr: UTestNullExpression) { }
    open fun visit(expr: UTestShortExpression) { }
    open fun visit(expr: UTestStringExpression) { }
    open fun visit(expr: UTestMockInst) { }

    //endregion

    //region Mocks

    open fun visit(expr: UTestGlobalMock) {
        for (fieldValue in expr.fields.values) {
            visitExpr(fieldValue)
        }

        for (methodValues in expr.methods.values) {
            for (value in methodValues) {
                visitExpr(value)
            }
        }
    }

    open fun visit(expr: UTestMockObject) {
        for (fieldValue in expr.fields.values) {
            visitExpr(fieldValue)
        }

        for (methodValues in expr.methods.values) {
            for (value in methodValues) {
                visitExpr(value)
            }
        }
    }

    open fun visit(expr: UTestInstList) {
        for (inst in expr.instList) {
            visit(inst)
        }
    }

    //endregion

    //region Calls

    open fun visit(call: UTestCall) {
        when (call) {
            is UTestConstructorCall -> visit(call)
            is UTestStaticMethodCall -> visit(call)
            is UTestMethodCall -> visit(call)
            is UTestAllocateMemoryCall -> visit(call)
            is UTestAssertThrowsCall -> visit(call)
            is UTestAssertEqualsCall -> visit(call)
        }
    }

    open fun visit(call: UTestConstructorCall) {
        for (arg in call.args)
            visitExpr(arg)
    }

    open fun visit(call: UTestStaticMethodCall) {
        for (arg in call.args)
            visitExpr(arg)
    }

    open fun visit(call: UTestMethodCall) {
        visitExpr(call.instance)
        for (arg in call.args)
            visitExpr(arg)
    }

    open fun visit(call: UTestAllocateMemoryCall) { }

    open fun visit(call: UTestAssertThrowsCall) {
        for (inst in call.instList) {
            visit(inst)
        }
    }

    open fun visit(call: UTestAssertEqualsCall) {
        visit(call.expected)
        visit(call.actual)
    }

    //endregion

    //region Statements

    open fun visit(stmt: UTestArraySetStatement) {
        visitExpr(stmt.arrayInstance)
        visitExpr(stmt.index)
        visitExpr(stmt.setValueExpression)
    }

    open fun visit(stmt: UTestBinaryConditionStatement) {
        visitExpr(stmt.lhv)
        visitExpr(stmt.rhv)

        for (thenStatement in stmt.trueBranch)
            visitStmt(thenStatement)

        for (elseStatement in stmt.elseBranch)
            visitStmt(elseStatement)
    }

    open fun visit(stmt: UTestSetFieldStatement) {
        visitExpr(stmt.instance)
        visitExpr(stmt.value)
    }

    open fun visit(stmt: UTestSetStaticFieldStatement) {
        visitExpr(stmt.value)
    }

    //endregion
}
