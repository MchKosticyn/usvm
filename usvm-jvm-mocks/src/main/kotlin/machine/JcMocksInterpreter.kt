package machine

import com.microsoft.z3.Sort
import io.ksmt.sort.KSort
import io.ksmt.utils.asExpr
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcStaticCallExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.api.makeSymbolicRef
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcConcreteMethodCallInst
import org.usvm.machine.JcContext
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.JcMethodCallBaseInst
import org.usvm.machine.interpreter.JcExprResolver
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.state.skipMethodInvocationWithValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.mocks.mockMethod
import org.usvm.machine.state.returnValue

val mocksMap : MutableMap<UConcreteHeapRef, String> = HashMap()

val mocksMethodsMap : MutableMap<Any, String> = HashMap()

open class JcMocksInterpreter(
    ctx: JcContext,
    applicationGraph: JcApplicationGraph,
    options: JcMachineOptions,
    observer: JcInterpreterObserver? = null,
): JcInterpreter(ctx, applicationGraph, options, observer) {

    override fun callMethod(
        scope: JcStepScope,
        stmt: JcMethodCallBaseInst,
        exprResolver: JcExprResolver
    ) {
        when (stmt) {
            is JcConcreteMethodCallInst -> {
                val method = stmt.method
                val methodName = method.name
                val retStmt = stmt.returnSite
                if (retStmt !is JcAssignInst) { throw IllegalArgumentException("state not possible, error in jacodb and mockito compatibility") }

                if (stmt.arguments.isNotEmpty()) {
                    val refToMock = stmt.arguments[0]
                    if (refToMock in mocksMap) {
                        val retType = retStmt.lhv.type
//                        val newSymbolicRef = scope.makeSymbolicRef(retType) ?: throw IllegalArgumentException( "a")
                        val newSymbolicRef : UExpr<KSort>
                        scope.doWithState {
                            val retSort = ctx.typeToSort(retType)
                            newSymbolicRef = memory.mocker.createMockSymbol(null,retSort, ownership)
                            skipMethodInvocationWithValue(stmt, newSymbolicRef)
                        }
                        val mocksMethodInfo = mocksMap[refToMock] + "::" + methodName
                        mocksMethodsMap[newSymbolicRef] = mocksMethodInfo
                        return
                    }
                }

                val mockCall = retStmt.rhv
                if (methodName == "mock" && mockCall is JcStaticCallExpr && mockCall.args.size == 1) {
                    scope.doWithState {
                        val classRef = stmt.arguments[0].asExpr(ctx.addressSort)
                        val classRefTypeRepresentative =
                            memory.read(UFieldLValue(ctx.addressSort, classRef, ctx.classTypeSyntheticField))
                        classRefTypeRepresentative as UConcreteHeapRef
                        val classType = memory.types.typeOf(classRefTypeRepresentative.address)
                        val ref = memory.allocConcrete(classType)
                        skipMethodInvocationWithValue(stmt, ref)
                        val lineNumber = stmt.returnSite.lineNumber
                        mocksMap[ref] = "mock:" + classType.typeName + "(line:" + lineNumber + ")"
                    }
                    return
                }
                super.callMethod(scope, stmt, exprResolver)
            }
            else -> super.callMethod(scope, stmt, exprResolver)
        }
    }
}
