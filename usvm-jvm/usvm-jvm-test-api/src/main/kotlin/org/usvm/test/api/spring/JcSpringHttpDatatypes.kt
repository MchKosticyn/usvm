package org.usvm.test.api.spring

abstract class JcSpringNamedValueHolder<T>(private val name: String, private val values: T) {
    fun getName() = name
    fun getValues() = values
}

// TODO: Add other datatypes if necessary
class JcSpringHttpHeader(name: String, values: List<String>) : JcSpringNamedValueHolder<List<String>>(name, values)

class JcSpringHttpParameter(name: String, values: List<String>) : JcSpringNamedValueHolder<List<String>>(name, values)

class JcSpringHttpCookie(name: String, values: String) : JcSpringNamedValueHolder<String>(name, values) {
    companion object {
        fun ofCookieObject(cookie: Any): JcSpringHttpCookie {
            val cookieClass = cookie.javaClass
            check(cookieClass.name.endsWith("Cookie"))
            val name = cookieClass.getMethod("getName").invoke(cookie) as String
            val value = cookieClass.getMethod("getValue").invoke(cookie) as String
            return JcSpringHttpCookie(name, value)
        }
    }
}

enum class JcSpringRequestMethod {
    GET,
    PUT,
    POST,
    PATCH,
    DELETE;
}
