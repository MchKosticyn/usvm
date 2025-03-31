package testGeneration

import machine.state.pinnedValues.JcPinnedKey
import machine.state.pinnedValues.JcSimplePinnedKey
import machine.state.pinnedValues.JcSpringPinnedValueSource
import machine.state.pinnedValues.JcSpringPinnedValues
import org.usvm.api.util.JcTestStateResolver
import org.usvm.test.api.UTestNullExpression
import org.usvm.test.api.spring.JcSpringHttpCookie
import org.usvm.test.api.spring.JcSpringHttpHeader
import org.usvm.test.api.spring.JcSpringHttpParameter
import org.usvm.test.api.spring.JcSpringRequest
import org.usvm.test.api.spring.JcSpringRequestMethod
import org.usvm.test.api.spring.UTAny
import org.usvm.test.api.spring.UTString
import org.usvm.test.api.spring.UTStringArray


class JcSpringPinnedValuesRequest(
    private val pinnedValues: JcSpringPinnedValues,
    private val exprResolver: JcSpringTestExprResolver,
) : JcSpringRequest {
    private val stringType = exprResolver.ctx.stringType

    private fun sortRequestUriVariables(path: UTString, uriVariables: Map<UTString, UTAny>): List<UTAny> {
        // TODO: check it
        val uriVariableNames = Regex("\\{([^}]*)}").findAll(path.value)
            .map { UTString(it.groupValues[1], stringType) }
            .toList()

        return uriVariableNames.map {
            uriVariables.getValue(it)
        }.also { assert(it.size == uriVariables.size) }
    }

    private fun collectAndConcretize(pinnedValueSource: JcSpringPinnedValueSource): Map<UTString, UTAny> {
        return pinnedValues.collectAndConcretize(
            exprResolver,
            JcTestStateResolver.ResolveMode.MODEL,
            pinnedValueSource,
            stringType
        ).filter { it.value !is UTestNullExpression }
    }

    private fun getStringPinnedValue(key: JcSimplePinnedKey): UTString {
        val methodExpr = pinnedValues.getValue(key)
        check(methodExpr != null)
        return exprResolver.withMode(JcTestStateResolver.ResolveMode.MODEL) {
            val method = exprResolver.resolvePinnedValue(methodExpr)
            check(method is UTString)
            method
        }
    }

    private fun withJsonSerialize(target: UTAny): UTAny {
        if (target is UTString) return target
        TODO("Create ObjectMapper and make a write call with constructed object #AA")
    }

    override fun getCookies(): List<JcSpringHttpCookie> {
        val cookies = collectAndConcretize(JcSpringPinnedValueSource.REQUEST_COOKIE)
        return cookies.mapNotNull { (key, value) -> JcSpringHttpCookie(key, value as UTString) }
    }

    override fun getHeaders(): List<JcSpringHttpHeader> {
        val headersRaw = collectAndConcretize(JcSpringPinnedValueSource.REQUEST_HEADER)
        return headersRaw.mapNotNull { (key, value) -> JcSpringHttpHeader(key, value as UTStringArray) }
    }

    override fun getMethod(): JcSpringRequestMethod {
        val method = getStringPinnedValue(JcPinnedKey.requestMethod())
        return JcSpringRequestMethod.valueOf(method.value.uppercase())
    }

    override fun getPath(): UTString {
        val method = getStringPinnedValue(JcPinnedKey.requestPath())
        return method
    }

    override fun getContent(): UTAny? {
        val body = pinnedValues.getValue(JcPinnedKey.requestBody()) ?: return null
        return withJsonSerialize(exprResolver.resolvePinnedValue(body))
    }

    override fun getParameters(): List<JcSpringHttpParameter> {
        val parametersRaw = collectAndConcretize(JcSpringPinnedValueSource.REQUEST_PARAM)
        return parametersRaw.mapNotNull { (key, value) -> JcSpringHttpParameter(key, value as UTStringArray) }
    }

    override fun getUriVariables(): List<UTAny> {
        val pathVariables = collectAndConcretize(JcSpringPinnedValueSource.REQUEST_PATH_VARIABLE)
        return sortRequestUriVariables(getPath(), pathVariables)
    }
}
