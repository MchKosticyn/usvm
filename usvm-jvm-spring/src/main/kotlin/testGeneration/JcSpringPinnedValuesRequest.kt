package testGeneration

import machine.state.pinnedValues.JcPinnedKey
import machine.state.pinnedValues.JcSpringPinnedValue
import machine.state.pinnedValues.JcSpringPinnedValueSource
import machine.state.pinnedValues.JcSpringPinnedValues
import machine.state.pinnedValues.JcSpringRawPinnedValues
import machine.state.pinnedValues.JcStringPinnedKey
import org.usvm.test.api.spring.JcSpringHttpCookie
import org.usvm.test.api.spring.JcSpringHttpHeader
import org.usvm.test.api.spring.JcSpringHttpParameter
import org.usvm.test.api.spring.JcSpringRequest
import org.usvm.test.api.spring.JcSpringRequestMethod

class JcSpringPinnedValuesRequest(
    pinnedValues: JcSpringPinnedValues,
    concretize: (value: JcSpringPinnedValue) -> Any?
) : JcSpringRequest {

    private val calculatedValues = JcSpringRawPinnedValues(pinnedValues.getMap().map { (k, v) -> k to concretize(v) }.toMap())

    private fun collectAndConcretize(source: JcSpringPinnedValueSource): Map<String, Any?> {
        return calculatedValues.getValuesOfSource<JcStringPinnedKey>(source)
            .map { (key, value) ->
                val name = key.getName()
                name to value
            }.toMap()
    }

    private fun sortRequestUriVariables(path: String, uriVariables: Map<String, Any?>): List<Any?> {
        // TODO: check it
        val uriVariableNames = Regex("\\{([^}]*)}").findAll(path)
            .map { it.groupValues[1] }
            .toList()

        return uriVariableNames.map {
            uriVariables.getValue(it)
        }.also { assert(it.size == uriVariables.size) }
    }

    private fun handleStringMultiValue(possibleMultiValue: Any?): List<String>? {
        if (possibleMultiValue == null) return null
        // TODO: Check return types and adjust this accordingly #AA
        return listOf(possibleMultiValue.toString())
    }

    override fun getCookies(): List<JcSpringHttpCookie> {
        val cookies = collectAndConcretize(JcSpringPinnedValueSource.REQUEST_COOKIE)
        return cookies.mapNotNull { (key, value) -> JcSpringHttpCookie(key, value as String) }
    }

    override fun getHeaders(): List<JcSpringHttpHeader> {
        val headersRaw = collectAndConcretize(JcSpringPinnedValueSource.REQUEST_HEADER)
        return headersRaw.mapNotNull { (key, value) -> handleStringMultiValue(value)?.let { JcSpringHttpHeader(key, it)} }
    }

    override fun getMethod(): JcSpringRequestMethod {
        val method = calculatedValues.getValue(JcPinnedKey.requestMethod())
        check(method != null && method is String)
        return JcSpringRequestMethod.valueOf(method.uppercase())
    }

    override fun getPath(): String {
        val path = calculatedValues.getValue(JcPinnedKey.requestPath())
        check(path != null && path is String)
        return path
    }

    override fun getContentAsString(): String {
        val body = calculatedValues.getValue(JcPinnedKey.requestBody())
        return body as String
    }

    override fun getParameters(): List<JcSpringHttpParameter> {
        val paramsRaw = collectAndConcretize(JcSpringPinnedValueSource.REQUEST_PARAM)
        return paramsRaw.mapNotNull { (key, value) -> handleStringMultiValue(value)?.let { JcSpringHttpParameter(key, it)} }
    }

    override fun getUriVariables(): List<Any?> {
        val pathVariables = collectAndConcretize(JcSpringPinnedValueSource.REQUEST_PATH_VARIABLE)
        return sortRequestUriVariables(getPath(), pathVariables)
    }
}
