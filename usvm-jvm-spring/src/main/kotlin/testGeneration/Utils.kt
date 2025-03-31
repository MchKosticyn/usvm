package testGeneration

import machine.state.pinnedValues.JcSpringPinnedValueSource
import machine.state.pinnedValues.JcSpringPinnedValues
import machine.state.pinnedValues.JcStringPinnedKey
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.autoboxIfNeeded
import org.jacodb.api.jvm.ext.toType
import org.jacodb.api.jvm.ext.unboxIfNeeded
import org.usvm.api.util.JcTestStateResolver
import org.usvm.test.api.UTestArraySetStatement
import org.usvm.test.api.UTestConstExpression
import org.usvm.test.api.UTestConstructorCall
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

private fun tryBoxedPrimitiveToString(target: UTAny, stringType: JcType): UTAny {
    if (target !is UTestConstructorCall)
        return target
    val targetType = target.method.enclosingClass.toType()
    if (targetType.unboxIfNeeded() == targetType)
        return target
    if (!(target.args.isNotEmpty() && target.args[0] is UTestConstExpression<*>))
        return target

    return UTString((target.args[0] as UTestConstExpression<*>).value.toString(), stringType)
}

fun handleStringMultiValue(
    exprResolver: JcSpringTestExprResolver,
    possibleMultiValue: UTAny,
    stringType: JcType,
    intType: JcType
): UTStringArray {
    val value = tryBoxedPrimitiveToString(possibleMultiValue, stringType)

    if (value is UTStringArray)
        return value

    // TODO: Check return types and adjust this accordingly #AA
    check(value is UTString) { "Value was not unboxed" }
    val singletonArray = UTStringArray(stringType, UTestIntExpression(1, intType))
    exprResolver.appendStatement(UTestArraySetStatement(
        singletonArray,
        UTestIntExpression(0, intType),
        value
    ))

    return singletonArray
}