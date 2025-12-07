package machine

import io.ksmt.utils.asExpr
import machine.memory.JcMockedMethod
import machine.memory.JcMockedMethodsRegion
import machine.memory.JcMockedMethodsRegionId
import machine.memory.JcMockedMethodsValue
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcStaticCallExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
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

val mocksMap : MutableMap<UConcreteHeapRef, String> = HashMap()
val mockedMethods : MutableSet<JcMockedMethodsValue<USort>> = mutableSetOf()
val mockedMethodsValues : MutableMap<JcMockedMethodsValue<USort>, UExpr<USort>> = HashMap()


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
                if (retStmt !is JcAssignInst) { throw IllegalArgumentException("state unreachable") }

                if (stmt.arguments.isNotEmpty()) {
                    val refToMock = stmt.arguments[0]
                    mocksMap[refToMock]?.let {value ->
                        val retType = retStmt.lhv.type
                        val newSymbolicRef : UExpr<out USort>
                        val mockedMethod = JcMockedMethod(methodName, value)

                        scope.doWithState {
                            val retSort = ctx.typeToSort(retType)
                            val memoryRegion = memory.getRegion(JcMockedMethodsRegionId(retSort)) as JcMockedMethodsRegion<USort>
                            val mockedMethodValue = JcMockedMethodsValue(mockedMethod, retSort)
                            newSymbolicRef = memoryRegion.read(mockedMethodValue.key)
                            skipMethodInvocationWithValue(stmt, newSymbolicRef)
                            mockedMethods.add(mockedMethodValue)
                        }
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
