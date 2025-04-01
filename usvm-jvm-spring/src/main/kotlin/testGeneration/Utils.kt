package testGeneration

import machine.state.pinnedValues.JcSpringPinnedValueSource
import machine.state.pinnedValues.JcSpringPinnedValues
import machine.state.pinnedValues.JcStringPinnedKey
import org.jacodb.api.jvm.JcType
import org.usvm.api.util.JcTestStateResolver
import org.usvm.test.api.spring.UTAny
import org.usvm.test.api.spring.UTString

fun JcSpringPinnedValues.collectAndResolve(
    exprResolver: JcSpringTestExprResolver,
    resolveMode: JcTestStateResolver.ResolveMode,
    source: JcSpringPinnedValueSource,
    stringType: JcType
): Map<UTString, UTAny> {
    return exprResolver.withMode(resolveMode) {
        getValuesOfSource<JcStringPinnedKey>(source)
            .map { (key, value) ->
                val name = key.getName()
                val resolvedValue = exprResolver.resolvePinnedValue(value)
                UTString(name, stringType) to resolvedValue
            }.toMap()
    }
}
