package org.usvm.test.api.spring

class JcSpringResponse(private val response: Any) {
    private val responseClass = response.javaClass

    @Suppress("UNCHECKED_CAST")
    private fun <T> getFromMethod(
        methodName: String,
        parameterTypes: Array<Class<*>> = arrayOf(),
        arguments: Array<Any> = arrayOf()
    ): T {
        return responseClass.getMethod(methodName, *parameterTypes).invoke(response, *arguments) as T
    }

    fun getStatusCode(): Int = getFromMethod("getStatus")

    // TODO: Will not work on HttpServletResponse interface #AA
    fun getErrorMessage(): String = getFromMethod("getErrorMessage")

    // TODO: Will not work on HttpServletResponse interface #AA
    fun getContentLength(): Int = getFromMethod("getContentLength")

    // TODO: Will not work on HttpServletResponse interface #AA
    fun getCookies(): List<JcSpringHttpCookie> {
        val rawCookies = getFromMethod("getCookies") as Array<Any>? ?: arrayOf()
        return rawCookies.map { JcSpringHttpCookie.ofCookieObject(it) }
    }

    fun getContentAsString(): String = getFromMethod("getContentAsString")

    private fun getHeader(name: String): Collection<String> {
        return getFromMethod("getHeaders", arrayOf(String::class.java), arrayOf(name))
    }

    fun getHeaders(): List<JcSpringHttpHeader> {
        val headersNames = getFromMethod("getHeaderNames") as Collection<String>
        return headersNames.toList().map { JcSpringHttpHeader(it, getHeader(it).toList()) }
    }
}
