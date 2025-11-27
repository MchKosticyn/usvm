package machine

import io.ksmt.utils.asExpr
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcStaticCallExpr
import org.jacodb.api.jvm.ext.toType
import org.jacodb.api.jvm.ext.void
import org.jacodb.impl.features.classpaths.JcUnknownMethod
import org.usvm.UConcreteHeapRef
import org.usvm.api.targets.JcTarget
import org.usvm.jvm.util.toJavaClass
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcConcreteMethodCallInst
import org.usvm.machine.JcContext
import org.usvm.machine.JcDynamicMethodCallInst
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.JcMethodApproximationResolver
import org.usvm.machine.JcMethodCall
import org.usvm.machine.JcMethodCallBaseInst
import org.usvm.machine.JcVirtualMethodCallInst
import org.usvm.machine.interpreter.JcExprResolver
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.skipMethodInvocationWithValue
import org.usvm.memory.UMemory
import org.usvm.targets.UTargetsSet
import org.usvm.util.findMethod
import org.usvm.api.allocateConcreteRef
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.mocks.mockMethod
import org.usvm.machine.state.addNewMethodCall

val mocksMap : MutableMap<UConcreteHeapRef, String> = HashMap()

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
                if (retStmt !is JcAssignInst) {throw IllegalArgumentException("state not possible, error in jacodb and mockito compatibility")}
                val mockCall = retStmt.rhv
                if (methodName == "mock" && mockCall is JcStaticCallExpr && mockCall.args.size == 1) {
//                    val entryPoint = applicationGraph.entryPoints(method).singleOrNull()
//                    if (entryPoint == null) {
//                        mockMethod(scope, stmt, applicationGraph)
//                        return
//                    }
                    scope.doWithState {
                        val classRef = stmt.arguments[0].asExpr(ctx.addressSort)
                        val classRefTypeRepresentative =
                            memory.read(UFieldLValue(ctx.addressSort, classRef, ctx.classTypeSyntheticField))
                        classRefTypeRepresentative as UConcreteHeapRef
                        val classType = memory.types.typeOf(classRefTypeRepresentative.address)
                        val ref = memory.allocConcrete(classType)
                        skipMethodInvocationWithValue(stmt, ref)
                        val lineNumber = stmt.returnSite.lineNumber
                        mocksMap[ref] = classType.typeName + ": " + lineNumber
                    }
                }
                else {super.callMethod(scope, stmt, exprResolver)}
            }
            else -> super.callMethod(scope, stmt, exprResolver)
        }
    }
}
