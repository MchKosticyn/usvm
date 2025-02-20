package org.usvm.api.spring

import org.usvm.machine.state.pinnedValues.JcSpringPinnedValue
import org.usvm.machine.state.pinnedValues.JcSpringPinnedValueKey.Companion.requestBody
import org.usvm.machine.state.pinnedValues.JcSpringPinnedValueKey.Companion.requestMethod
import org.usvm.machine.state.pinnedValues.JcSpringPinnedValueKey.Companion.requestPath
import org.usvm.machine.state.pinnedValues.JcSpringPinnedValueSource
import org.usvm.machine.state.pinnedValues.JcSpringPinnedValues
import java.util.Enumeration

interface JcSpringRequest {
    fun getCookies(): List<JcSpringHttpCookie>
    fun getHeaders(): List<JcSpringHttpHeader>
    fun getMethod(): JcSpringRequestMethod
    fun getPath(): String
    fun getContentAsString(): String
    fun getParameters(): List<JcSpringHttpParameter>
    fun getUriVariables(): List<Any?>
}

class JcSpringRealRequest(private val request: Any) : JcSpringRequest {
    val requestClass = request.javaClass
    init {
        check(requestClass.name.endsWith("MockHttpServletRequest"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getFromMethod(methodName: String, parameterTypes: Array<Class<*>> = arrayOf(), arguments: Array<Any> = arrayOf()): T {
        return requestClass.getMethod(methodName, *parameterTypes).invoke(request, *arguments) as T
    }

    override fun getCookies(): List<JcSpringHttpCookie> {
        val rawCookies = getFromMethod("getCookies") as Array<Any>? ?: arrayOf()
        return rawCookies.map { JcSpringHttpCookie.ofCookieObject(it) }
    }

    private fun getHeader(name: String): Enumeration<String> {
        return getFromMethod("getHeaders", arrayOf(String::class.java), arrayOf(name))
    }

    override fun getHeaders(): List<JcSpringHttpHeader> {
        val headersNames = getFromMethod("getHeaderNames") as Enumeration<String>
        return headersNames.toList().map { JcSpringHttpHeader(it, getHeader(it).toList()) }
    }

    override fun getMethod(): JcSpringRequestMethod = JcSpringRequestMethod.valueOf(getFromMethod("getMethod"))

    override fun getPath(): String = getFromMethod("getPathInfo")

    override fun getContentAsString(): String = getFromMethod("getContentAsString")

    override fun getParameters(): List<JcSpringHttpParameter> {
        val parameterMap = getFromMethod("getParameterMap") as Map<String, Array<String>>
        return parameterMap.map { JcSpringHttpParameter(it.key, it.value.toList())}
    }

    override fun getUriVariables(): List<Any?> {
        // MockHttpServletRequest contains built URL
        return listOf()
    }
}

class JcSpringPinnedValuesRequest(
    private val pinnedValues: JcSpringPinnedValues,
    private val concretize: (value: JcSpringPinnedValue) -> Any?
) : JcSpringRequest {

    private fun collectAndConcretize(source: JcSpringPinnedValueSource): Map<String, Any?> {
        return pinnedValues.getValuesOfSource(source)
            .map { (key, value) ->
                val name = key.getName()
                check(name != null) { "Only named pinned values here!" }
                name to concretize(value)
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
        val method = pinnedValues.getValue(requestMethod())?.let { concretize(it) }
        check(method != null && method is String)
        return JcSpringRequestMethod.valueOf(method.uppercase())
    }

    override fun getPath(): String {
        val path = pinnedValues.getValue(requestPath())?.let { concretize(it) }
        check(path != null && path is String)
        return path
    }

    override fun getContentAsString(): String {
        val body = pinnedValues.getValue(requestBody())?.let(concretize) ?: ""
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