package testGeneration

import machine.state.pinnedValues.JcSpringPinnedValueSource
import machine.state.pinnedValues.JcSpringPinnedValues
import machine.state.pinnedValues.JcStringPinnedKey
import org.jacodb.api.jvm.JcType
import org.usvm.api.util.JcTestStateResolver
import org.usvm.test.api.UTestArraySetStatement
import org.usvm.test.api.UTestIntExpression
import org.usvm.test.api.spring.UTAny
import org.usvm.test.api.spring.UTString
import org.usvm.test.api.spring.UTStringArray

fun JcSpringPinnedValues.collectAndConcretize(
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

fun handleStringMultiValue(
    exprResolver: JcSpringTestExprResolver,
    possibleMultiValue: UTAny,
    stringType: JcType,
    intType: JcType
): UTStringArray {
    if (possibleMultiValue is UTStringArray)
        return possibleMultiValue

    // TODO: Check return types and adjust this accordingly #AA
    check(possibleMultiValue is UTString) { "Multi-value request headers/parameters are not supported yet" }
    val singletonArray = UTStringArray(stringType, UTestIntExpression(1, intType))
    exprResolver.appendStatement(UTestArraySetStatement(
        singletonArray,
        UTestIntExpression(0, intType),
        possibleMultiValue
    ))

    return singletonArray
}