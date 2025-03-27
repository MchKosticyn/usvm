package org.usvm.test.api.spring

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
    private val requestClass = request.javaClass

    @Suppress("UNCHECKED_CAST")
    private fun <T> getFromMethod(
        methodName: String,
        parameterTypes: Array<Class<*>> = arrayOf(),
        arguments: Array<Any> = arrayOf()
    ): T {
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
        return headersNames.toList()
            .filter { it != "Cookie" } // Cookies are in getCookies()
            .map { JcSpringHttpHeader(it, getHeader(it).toList()) }
    }

    override fun getMethod(): JcSpringRequestMethod = JcSpringRequestMethod.valueOf(getFromMethod("getMethod"))

    override fun getPath(): String = getFromMethod("getPathInfo")

    override fun getContentAsString(): String {
        // TODO: If read once will fail after! Needs copying request #AA
        val inputStream = getFromMethod("getInputStream") as Any
        val content = inputStream.javaClass.getMethod("readAllBytes").invoke(inputStream) as ByteArray
        return String(content)
    }

    override fun getParameters(): List<JcSpringHttpParameter> {
        val parameterMap = getFromMethod("getParameterMap") as Map<String, Array<String>>
        return parameterMap.map { JcSpringHttpParameter(it.key, it.value.toList())}
    }

    override fun getUriVariables(): List<Any?> {
        // HttpServletRequest contains built URL
        return listOf()
    }
}
