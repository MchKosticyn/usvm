package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClasspath
import org.usvm.test.api.UTestCreateArrayExpression
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestStringExpression

typealias UTString = UTestStringExpression
typealias UTStringArray = UTestCreateArrayExpression
typealias UTAny = UTestExpression

abstract class JcSpringNamedValueHolder<T>(
    private val name: UTString,
    private val values: T
) {
    fun getName() = name
    fun getValues() = values
}

// TODO: Add other datatypes if necessary
class JcSpringHttpHeader(
    name: UTString,
    values: UTStringArray
) : JcSpringNamedValueHolder<UTStringArray>(name, values)

class JcSpringHttpParameter(
    name: UTString,
    values: UTStringArray
) : JcSpringNamedValueHolder<UTStringArray>(name, values)

class JcSpringHttpCookie(
    name: UTString,
    value: UTString
): JcSpringNamedValueHolder<UTString>(name, value) {
    companion object {
        fun ofCookieObject(cookie: Any, cp: JcClasspath): JcSpringHttpCookie {
            val cookieClass = cookie.javaClass
            check(cookieClass.name.endsWith("Cookie"))
            val name = cookieClass.getMethod("getName").invoke(cookie) as String
            val value = cookieClass.getMethod("getValue").invoke(cookie) as String
            return JcSpringHttpCookie(UTString(name, cp.stringType), UTString(value, cp.stringType))
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
