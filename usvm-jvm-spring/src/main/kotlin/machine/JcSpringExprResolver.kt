package machine

import isInjectedViaValue
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedField
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcImmediate
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.JcContext
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.interpreter.JcExprResolver
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.state.JcState

class JcSpringExprResolver(
    ctx: JcContext,
    scope: JcStepScope,
    options: JcMachineOptions,
    localToIdx: (JcMethod, JcImmediate) -> Int,
    mkTypeRef: (JcState, JcType) -> Pair<UConcreteHeapRef, Boolean>,
    mkStringConstRef: (JcState, String, Boolean) -> Pair<UConcreteHeapRef, Boolean>,
    shouldClinit: (JcRefType) -> Boolean,
    private val injectSymbolicValue: (JcStepScope, JcTypedField, UFieldLValue<JcField, UAddressSort>) -> Unit
) : JcExprResolver(ctx, scope, options, localToIdx, mkTypeRef, mkStringConstRef, shouldClinit) {

    @Suppress("UNCHECKED_CAST")
    override fun visitJcFieldRef(value: JcFieldRef): UExpr<out USort>? {
        val field = value.field
        val lValue = resolveFieldRef(value.instance, field) ?: return null

        if (field.field.isInjectedViaValue)
            injectSymbolicValue(scope, field, lValue as UFieldLValue<JcField, UAddressSort>)

        val expr = scope.calcOnState { memory.read(lValue) }

        if (assertIsSubtype(expr, value.type)) return expr

        return null
    }
}
