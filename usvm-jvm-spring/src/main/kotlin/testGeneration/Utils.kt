package testGeneration

import machine.state.pinnedValues.JcSpringPinnedValueSource
import machine.state.pinnedValues.JcSpringPinnedValues
import machine.state.pinnedValues.JcStringPinnedKey
import org.usvm.machine.JcContext
import org.usvm.test.api.spring.UTAny
import org.usvm.test.api.spring.UTString

fun JcSpringPinnedValues.collectAndResolve(
    exprResolver: JcSpringTestExprResolver,
    source: JcSpringPinnedValueSource,
    ctx: JcContext
): Map<UTString, UTAny> {
    return getValuesOfSource<JcStringPinnedKey>(source)
        .map { (key, value) ->
            val name = key.getName()
            val resolvedValue = exprResolver.resolvePinnedValue(value)
            UTString(name, ctx.stringType) to resolvedValue
        }.toMap()
}
