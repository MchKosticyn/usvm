package org.usvm.jvm.rendering.testTransformers

import org.usvm.test.api.UTest
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestArithmeticExpression
import org.usvm.test.api.UTestArrayGetExpression
import org.usvm.test.api.UTestArrayLengthExpression
import org.usvm.test.api.UTestArraySetStatement
import org.usvm.test.api.UTestBinaryConditionExpression
import org.usvm.test.api.UTestBinaryConditionStatement
import org.usvm.test.api.UTestBooleanExpression
import org.usvm.test.api.UTestByteExpression
import org.usvm.test.api.UTestCall
import org.usvm.test.api.UTestCastExpression
import org.usvm.test.api.UTestCharExpression
import org.usvm.test.api.UTestClassExpression
import org.usvm.test.api.UTestConstructorCall
import org.usvm.test.api.UTestCreateArrayExpression
import org.usvm.test.api.UTestDoubleExpression
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestFloatExpression
import org.usvm.test.api.UTestGetFieldExpression
import org.usvm.test.api.UTestGetStaticFieldExpression
import org.usvm.test.api.UTestGlobalMock
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestIntExpression
import org.usvm.test.api.UTestLongExpression
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestMockObject
import org.usvm.test.api.UTestNullExpression
import org.usvm.test.api.UTestSetFieldStatement
import org.usvm.test.api.UTestSetStaticFieldStatement
import org.usvm.test.api.UTestShortExpression
import org.usvm.test.api.UTestStatement
import org.usvm.test.api.UTestStaticMethodCall
import org.usvm.test.api.UTestStringExpression
import java.util.IdentityHashMap

// TODO: add CompositeTransformer
abstract class JcTestTransformer {

    private val cache = IdentityHashMap<UTestExpression, UTestExpression>()

    protected fun transformed(expr: UTestExpression): UTestExpression? = cache[expr]

    open fun transform(test: UTest): UTest {
        val initStatements = test.initStatements.mapNotNull { transformInst(it) }
        val callMethodExpression = transformCall(test.callMethodExpression)
            ?: error("Transformers should not delete result method call")

        return UTest(initStatements, callMethodExpression)
    }

    protected fun transformInst(inst: UTestInst): UTestInst? {
        return when (inst) {
            is UTestExpression -> transformExpr(inst)
            is UTestStatement -> transformStmt(inst)
        }
    }

    protected fun transformExpr(expr: UTestExpression): UTestExpression? {
        return cache.getOrPut(expr) { transform(expr) }
    }

    protected fun transformCall(call: UTestCall): UTestCall? {
        return cache.getOrPut(call) { transform(call) } as? UTestCall
    }

    protected fun transformStmt(stmt: UTestStatement): UTestStatement? {
        return when (stmt) {
            is UTestArraySetStatement -> transform(stmt)
            is UTestBinaryConditionStatement -> transform(stmt)
            is UTestSetFieldStatement -> transform(stmt)
            is UTestSetStaticFieldStatement -> transform(stmt)
        }
    }

    //region Expression transformers

    open fun transform(expr: UTestExpression): UTestExpression? {
        return when (expr) {
            is UTestArithmeticExpression -> transform(expr)
            is UTestArrayGetExpression -> transform(expr)
            is UTestArrayLengthExpression -> transform(expr)
            is UTestBinaryConditionExpression -> transform(expr)
            is UTestCastExpression -> transform(expr)
            is UTestCreateArrayExpression -> transform(expr)
            is UTestGetFieldExpression -> transform(expr)
            is UTestCall -> transformCall(expr)
            is UTestClassExpression -> transform(expr)
            is UTestBooleanExpression -> transform(expr)
            is UTestByteExpression -> transform(expr)
            is UTestCharExpression -> transform(expr)
            is UTestDoubleExpression -> transform(expr)
            is UTestFloatExpression -> transform(expr)
            is UTestIntExpression -> transform(expr)
            is UTestLongExpression -> transform(expr)
            is UTestNullExpression -> transform(expr)
            is UTestShortExpression -> transform(expr)
            is UTestStringExpression -> transform(expr)
            is UTestGetStaticFieldExpression -> transform(expr)
            is UTestGlobalMock -> transform(expr)
            is UTestMockObject -> transform(expr)
        }
    }

    open fun transform(expr: UTestArithmeticExpression): UTestExpression? {
        val lhv = transformExpr(expr.lhv) ?: return null
        val rhv = transformExpr(expr.rhv) ?: return null
        return UTestArithmeticExpression(expr.operationType, lhv, rhv, expr.type)
    }

    open fun transform(expr: UTestArrayGetExpression): UTestExpression? {
        val array = transformExpr(expr.arrayInstance) ?: return null
        val index = transformExpr(expr.index) ?: return null
        return UTestArrayGetExpression(array, index)
    }

    open fun transform(expr: UTestArrayLengthExpression): UTestExpression? {
        val array = transformExpr(expr.arrayInstance) ?: return null
        return UTestArrayLengthExpression(array)
    }

    open fun transform(expr: UTestBinaryConditionExpression): UTestExpression? {
        val lhv = transformExpr(expr.lhv) ?: return null
        val rhv = transformExpr(expr.rhv) ?: return null
        val trueBranch = transformExpr(expr.trueBranch) ?: return null
        val elseBranch = transformExpr(expr.elseBranch) ?: return null
        return UTestBinaryConditionExpression(expr.conditionType, lhv, rhv, trueBranch, elseBranch)
    }

    open fun transform(expr: UTestCastExpression): UTestExpression? {
        val toCastExpr = transformExpr(expr.expr) ?: return null
        return UTestCastExpression(toCastExpr, expr.type)
    }

    open fun transform(expr: UTestCreateArrayExpression): UTestExpression? {
        val size = transformExpr(expr.size) ?: return null
        return UTestCreateArrayExpression(expr.elementType, size)
    }

    open fun transform(expr: UTestGetFieldExpression): UTestExpression? {
        val instance = transformExpr(expr.instance) ?: return null
        return UTestGetFieldExpression(instance, expr.field)
    }

    open fun transform(expr: UTestGetStaticFieldExpression): UTestExpression? = expr

    open fun transform(expr: UTestClassExpression): UTestExpression? = expr

    open fun transform(expr: UTestBooleanExpression): UTestExpression? = expr
    open fun transform(expr: UTestByteExpression): UTestExpression? = expr
    open fun transform(expr: UTestCharExpression): UTestExpression? = expr
    open fun transform(expr: UTestDoubleExpression): UTestExpression? = expr
    open fun transform(expr: UTestFloatExpression): UTestExpression? = expr
    open fun transform(expr: UTestIntExpression): UTestExpression? = expr
    open fun transform(expr: UTestLongExpression): UTestExpression? = expr
    open fun transform(expr: UTestNullExpression): UTestExpression? = expr
    open fun transform(expr: UTestShortExpression): UTestExpression? = expr
    open fun transform(expr: UTestStringExpression): UTestExpression? = expr
    open fun transform(expr: UTestGlobalMock): UTestExpression? = expr
    open fun transform(expr: UTestMockObject): UTestExpression? = expr

    //endregion

    //region Call transformers

    open fun transform(call: UTestCall): UTestExpression? {
        return when (call) {
            is UTestConstructorCall -> transform(call)
            is UTestStaticMethodCall -> transform(call)
            is UTestMethodCall -> transform(call)
            is UTestAllocateMemoryCall -> transform(call)
        }
    }

    open fun transform(call: UTestConstructorCall): UTestCall? {
        val args = call.args.map { transformExpr(it) ?: return null }
        return UTestConstructorCall(call.method, args)
    }

    open fun transform(call: UTestStaticMethodCall): UTestCall? {
        val args = call.args.map { transformExpr(it) ?: return null }
        return UTestStaticMethodCall(call.method, args)
    }

    open fun transform(call: UTestMethodCall): UTestCall? {
        val instance = transformExpr(call.instance) ?: return null
        val args = call.args.map { transformExpr(it) ?: return null }
        return UTestMethodCall(instance, call.method, args)
    }

    open fun transform(call: UTestAllocateMemoryCall): UTestCall? = call

    //endregion

    //region Statement transformers

    open fun transform(stmt: UTestArraySetStatement): UTestStatement? {
        val array = transformExpr(stmt.arrayInstance) ?: return null
        val index = transformExpr(stmt.index) ?: return null
        val value = transformExpr(stmt.setValueExpression) ?: return null
        return UTestArraySetStatement(array, index, value)
    }

    open fun transform(stmt: UTestBinaryConditionStatement): UTestStatement? {
        val lhv = transformExpr(stmt.lhv) ?: return null
        val rhv = transformExpr(stmt.rhv) ?: return null
        val trueBranch = stmt.trueBranch.mapNotNull { transformStmt(it) }
        val elseBranch = stmt.elseBranch.mapNotNull { transformStmt(it) }
        return UTestBinaryConditionStatement(stmt.conditionType, lhv, rhv, trueBranch, elseBranch)
    }

    open fun transform(stmt: UTestSetFieldStatement): UTestStatement? {
        val instance = transformExpr(stmt.instance) ?: return null
        val value = transformExpr(stmt.value) ?: return null
        return UTestSetFieldStatement(instance, stmt.field, value)
    }

    open fun transform(stmt: UTestSetStaticFieldStatement): UTestStatement? {
        val value = transformExpr(stmt.value) ?: return null
        return UTestSetStaticFieldStatement(stmt.field, value)
    }

    //endregion
}
