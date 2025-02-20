package org.usvm.api.spring

import org.usvm.machine.state.concreteMemory.getFieldValue

@Suppress("UNCHECKED_CAST")
class JcSpringResponse(private val response: Any) {
    private val responseClass = response.javaClass

    init {
        check(response.javaClass.name.endsWith("MockHttpServletResponse"))
    }

    private fun <T> getFromMethod(methodName: String, parameterTypes: Array<Class<*>> = arrayOf(), arguments: Array<Any> = arrayOf()): T {
        return responseClass.getMethod(methodName, *parameterTypes).invoke(response, *arguments) as T
    }

    private fun <T> getFromField(fieldName: String): T {
        return responseClass.getDeclaredField(fieldName).getFieldValue(response) as T
    }

    fun getStatusCode(): Int = getFromMethod("getStatus")

    fun getErrorMessage(): String = getFromMethod("getErrorMessage")

    fun getContentLength(): Int = getFromMethod("getContentLength")

    fun getCookies(): List<JcSpringHttpCookie> {
        val rawCookies = getFromMethod("getCookies") as Array<Any>? ?: arrayOf()
        return rawCookies.map { JcSpringHttpCookie.ofCookieObject(it) }
    }

    fun getContentAsString(): String = getFromMethod("getContentAsString")

    fun getHeaders(): List<JcSpringHttpHeader> {
        val headers = getFromField("headers") as Any
        val keys = headers.javaClass.getDeclaredMethod("keySet").invoke(headers) as Set<String>
        return keys.map { JcSpringHttpHeader(it, listOf(getFromMethod("getHeader", arrayOf(String::class.java), arrayOf(it)))) }
    }
}
