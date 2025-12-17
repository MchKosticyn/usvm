package machine

import io.ksmt.utils.asExpr
import machine.memory.JcMockedMethod
import machine.memory.JcMockedMethodsReading
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

fun printMockedMethodsValues() {
    for (key in mockedMethodsValues.keys)     {
        key.print()
        print(" = ")
        print(mockedMethodsValues[key])
        println()
    }
}

open class JcMocksInterpreter2(
    ctx: JcContext,
    applicationGraph: JcApplicationGraph,
    options: JcMachineOptions,
    observer: JcInterpreterObserver? = null,
): JcInterpreter(ctx, applicationGraph, options, observer) {
    private fun handleConcreteMethodCall(
        scope: JcStepScope,
        stmt: JcConcreteMethodCallInst,
        exprResolver: JcExprResolver,
        retStmt: JcAssignInst
    ){
        val method = stmt.method
        val methodName = method.name
//        val isJavaMethod = method.enclosingClass.name.startsWith("java.")
        val isAppCode = method.declaration.relativePath.startsWith("org.usvm.samples.")
        if (stmt.arguments.isNotEmpty() && isAppCode) {
            val refToMock = stmt.arguments[0]
            val value = mocksMap[refToMock]
            if (value != null || refToMock is JcMockedMethodsReading) {
                val retType = retStmt.lhv.type
                val newSymbolicRef : UExpr<out USort>
                val lineNumber = retStmt.lineNumber
                val enclosingClass = if (refToMock is JcMockedMethodsReading) {refToMock.mockedMethod.enclosingClass} else {value!!} // maybe rewrite
                val mockedMethod = JcMockedMethod("$methodName(line:$lineNumber)", enclosingClass)

                scope.doWithState {
                    val retSort = ctx.typeToSort(retType)
                    val memoryRegion = memory.getRegion(JcMockedMethodsRegionId(retSort, retType)) as JcMockedMethodsRegion<USort>
                    val mockedMethodValue = JcMockedMethodsValue(mockedMethod, retSort, retType)
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
                mocksMap[ref] = "mock:" + classType.typeName + "(line:" + lineNumber + ")::"
            }
            return
        }
        super.callMethod(scope, stmt, exprResolver)
    }

    override fun callMethod(
        scope: JcStepScope,
        stmt: JcMethodCallBaseInst,
        exprResolver: JcExprResolver
    ) {
        when (stmt) {
            is JcConcreteMethodCallInst -> {
                val retStmt = stmt.returnSite
                if (retStmt !is JcAssignInst) {
                    super.callMethod(scope, stmt, exprResolver)
                } else {
                    handleConcreteMethodCall(scope, stmt, exprResolver, retStmt)
                }
            }
            else -> super.callMethod(scope, stmt, exprResolver)
        }
    }
}

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
                val isAppCode = method.declaration.relativePath.startsWith("org.usvm.samples.")
                if (stmt.arguments.isNotEmpty() && isAppCode) {
                    val refToMock = stmt.arguments[0]
                    val value = mocksMap[refToMock]
                     if (value != null || refToMock is JcMockedMethodsReading) {
                        val retType = retStmt.lhv.type
                        val newSymbolicRef : UExpr<out USort>
                        val lineNumber = retStmt.lineNumber
                        val enclosingClass = if (refToMock is JcMockedMethodsReading) {refToMock.mockedMethod.enclosingClass} else {value!!}
                         val mockedMethod = JcMockedMethod("$methodName(line:$lineNumber)", enclosingClass)

                        scope.doWithState {
                            val retSort = ctx.typeToSort(retType)
                            val memoryRegion = memory.getRegion(JcMockedMethodsRegionId(retSort, retType)) as JcMockedMethodsRegion<USort>
                            val mockedMethodValue = JcMockedMethodsValue(mockedMethod, retSort, retType)
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
                        mocksMap[ref] = "mock:" + classType.typeName + "(line:" + lineNumber + ")::"
                    }
                    return
                }
                super.callMethod(scope, stmt, exprResolver)
            }
            else -> super.callMethod(scope, stmt, exprResolver)
        }
    }
}
