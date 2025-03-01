package machine

import io.ksmt.utils.asExpr
import machine.concreteMemory.JcConcreteMemory
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.autoboxIfNeeded
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UNullRef
import org.usvm.USort
import org.usvm.api.readArrayIndex
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcConcreteMethodCallInst
import org.usvm.machine.JcContext
import org.usvm.machine.JcMethodApproximationResolver
import org.usvm.machine.JcMethodCall
import org.usvm.machine.state.JcState
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.skipMethodInvocationWithValue

open class JcConcreteMethodApproximationResolver(
    ctx: JcContext,
    applicationGraph: JcApplicationGraph,
) : JcMethodApproximationResolver(ctx, applicationGraph) {

    override fun approximate(callJcInst: JcMethodCall): Boolean {
        return approximateInternal(callJcInst) || super.approximate(callJcInst)
    }

    private fun approximateInternal(callJcInst: JcMethodCall): Boolean {
        if (!callJcInst.method.isStatic) {
            return approximateRegularMethod(callJcInst)
        }

        return false
    }

    private fun approximateRegularMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        val enclosingClass = method.enclosingClass
        val className = enclosingClass.name

        if (className == "java.lang.reflect.Method") {
            if (approximateMethodMethod(methodCall)) return true
        }

        if (className == "java.lang.reflect.Field") {
            if (approximateFieldMethod(methodCall)) return true
        }

        return false
    }

    private fun approximateMethodMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        val methodName = method.name
        if (methodName == "invoke") {
            val success = scope.calcOnState {
                val methodArg = arguments[0] as UConcreteHeapRef
                val thisArg = arguments[1]
                val argsArg = arguments[2]
                val args =
                    if (argsArg is UNullRef) null
                    else argsArg as UConcreteHeapRef
                val argsArrayType = ctx.cp.arrayTypeOf(ctx.cp.objectType)
                val descriptor = ctx.arrayDescriptorOf(argsArrayType)
                val memory = memory as JcConcreteMemory
                val method = memory.tryHeapRefToObject(methodArg) ?: return@calcOnState false
                method as java.lang.reflect.Method
                val declaringClass = ctx.cp.findTypeOrNull(method.declaringClass.name) ?: return@calcOnState false
                declaringClass as JcClassType
                val jcMethod = declaringClass.declaredMethods.find {
                    it.name == method.name
                } ?: return@calcOnState false
                val arguments: List<UExpr<out USort>>
                if (args == null) {
                    arguments = emptyList()
                } else {
                    arguments = jcMethod.parameters.mapIndexed { index, jcParameter ->
                        val idx = memory.objectToExpr(index, ctx.cp.int)
                        val value = memory.readArrayIndex(args, idx, descriptor, ctx.addressSort).asExpr(ctx.addressSort)
                        val type = jcParameter.type
                        unboxIfNeeded(value, type) ?: return@calcOnState false
                    }
                }
                val parameters =
                    if (jcMethod.isStatic) arguments
                    else listOf(thisArg) + arguments
                val postProcessInst = JcReflectionInvokeResult(methodCall, jcMethod)
                newStmt(JcConcreteMethodCallInst(methodCall.location, jcMethod.method, parameters, postProcessInst))
                return@calcOnState true
            }

            return success
        }

        return false
    }

    @Suppress("UNCHECKED_CAST")
    private fun JcState.unboxIfNeeded(value: UHeapRef, neededType: JcType): UExpr<USort>? {
        if (neededType !is JcPrimitiveType)
            return value as UExpr<USort>

        val boxedType = neededType.autoboxIfNeeded() as JcClassType
        val valueField = boxedType.declaredFields.find { it.name == "value" } ?: return null
        val sort = ctx.typeToSort(neededType)
        return memory.readField(value, valueField.field, sort)
    }

    private fun approximateFieldMethod(methodCall: JcMethodCall): Boolean = with(methodCall) {
        if (method.name == "get") {
            val fieldArg = arguments[0] as UConcreteHeapRef
            val thisArg = arguments[1].asExpr(ctx.addressSort)
            val success = scope.calcOnState {
                val memory = memory as JcConcreteMemory
                val field = memory.tryHeapRefToObject(fieldArg) ?: return@calcOnState false
                field as java.lang.reflect.Field
                val declaringClass = ctx.cp.findTypeOrNull(field.declaringClass.name) ?: return@calcOnState false
                declaringClass as JcClassType
                val fields = declaringClass.declaredFields + declaringClass.fields
                val jcField = fields.find { it.name == field.name } ?: return@calcOnState false
                val fieldType = jcField.type
                val sort = ctx.typeToSort(fieldType)
                val fieldValue = memory.readField(thisArg, jcField.field, sort)
                newStmt(JcBoxMethodCall(methodCall, fieldValue, fieldType))

                return@calcOnState true
            }

            return success
        }

        if (method.name == "set") {
            val fieldArg = arguments[0] as UConcreteHeapRef
            val thisArg = arguments[1].asExpr(ctx.addressSort)
            val success = scope.calcOnState {
                val memory = memory as JcConcreteMemory
                val field = memory.tryHeapRefToObject(fieldArg) ?: return@calcOnState false
                field as java.lang.reflect.Field
                val declaringClass = ctx.cp.findTypeOrNull(field.declaringClass.name) ?: return@calcOnState false
                declaringClass as JcClassType
                val fields = declaringClass.declaredFields + declaringClass.fields
                val jcField = fields.find { it.name == field.name } ?: return@calcOnState false
                val fieldType = jcField.type
                val sort = ctx.typeToSort(fieldType)
                val value = arguments[2].asExpr(ctx.addressSort)
                val unboxed = unboxIfNeeded(value, fieldType) ?: return@calcOnState false
                memory.writeField(thisArg, jcField.field, sort, unboxed, ctx.trueExpr)
                skipMethodInvocationWithValue(methodCall, ctx.voidValue)

                return@calcOnState true
            }

            return success
        }

        return false
    }

}
