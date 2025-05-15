package testGeneration

import machine.state.pinnedValues.JcPinnedKey
import machine.state.pinnedValues.JcSimplePinnedKey
import machine.state.pinnedValues.JcSpringPinnedValueSource
import machine.state.pinnedValues.JcSpringPinnedValues
import org.jacodb.api.jvm.ext.constructors
import org.jacodb.api.jvm.ext.findClass
import org.usvm.test.api.UTestConstructorCall
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestNullExpression
import org.usvm.test.api.UTestStringExpression
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
        // TODO: check it + sort in approximations #AA
        val uriVariableNames = Regex("\\{([^}]*)}").findAll(path.value)
            .map { UTString(it.groupValues[1], stringType) }
            .toList()
        val placeholder = UTestStringExpression("0", stringType)

        val completedUriVariables = uriVariableNames.map {
            uriVariables[it] ?: placeholder
        }
        return completedUriVariables
    }

    private fun renderMatrixVariables(matrixVariables: Map<UTString, UTString>): String {
        // TODO: URL Encode this and path #AA
        return matrixVariables.entries.joinToString("") { ";${it.key.value}=${it.value.value}" }
    }

    private fun collectAndResolve(pinnedValueSource: JcSpringPinnedValueSource): Map<UTString, UTAny> {
        return pinnedValues.collectAndResolve(
            exprResolver,
            pinnedValueSource,
            exprResolver.ctx
        ).filter { it.value !is UTestNullExpression }
    }

    private fun getStringPinnedValue(key: JcSimplePinnedKey): UTString {
        val methodExpr = pinnedValues.getValue(key)
        check(methodExpr != null)
        val method = exprResolver.resolvePinnedValue(methodExpr)
        check(method is UTString)
        return method
    }

    private fun withJsonSerialize(target: UTAny): UTAny {
        if (target is UTString) return target
        val objectMapperClass = exprResolver.ctx.cp.findClass("com.fasterxml.jackson.databind.ObjectMapper")
        val objectMapperConstructor = objectMapperClass.constructors.find { it.parameters.isEmpty() }
        val writeValueAsString = objectMapperClass.declaredMethods.find { it.name == "writeValueAsString" }
        check(objectMapperConstructor != null) { "Unable to find ObjectMapper constructor" }
        check(writeValueAsString != null) { "Unable to find writeValueAsString method" }
        return UTestMethodCall(
            UTestConstructorCall(objectMapperConstructor, listOf()),
            writeValueAsString,
            listOf(target)
        )
    }

    override fun getCookies(): List<JcSpringHttpCookie> {
        val cookies = collectAndResolve(JcSpringPinnedValueSource.REQUEST_COOKIE)
        return cookies.mapNotNull { (key, value) -> JcSpringHttpCookie(key, value as UTString) }
    }

    override fun getHeaders(): List<JcSpringHttpHeader> {
        val headersRaw = collectAndResolve(JcSpringPinnedValueSource.REQUEST_HEADER)
        return headersRaw.mapNotNull { (key, value) -> JcSpringHttpHeader(key, value as UTStringArray) }
    }

    override fun getMethod(): JcSpringRequestMethod {
        val method = getStringPinnedValue(JcPinnedKey.requestMethod())
        return JcSpringRequestMethod.valueOf(method.value.uppercase())
    }

    @Suppress("UNCHECKED_CAST")
    override fun getPath(): UTString {
        val path = getStringPinnedValue(JcPinnedKey.requestPath())
        val matrixVariables = collectAndResolve(JcSpringPinnedValueSource.REQUEST_MATRIX)
        val matrixSuffix = renderMatrixVariables(matrixVariables as Map<UTString, UTString>)
        return UTString(path.value + matrixSuffix, stringType)
    }

    override fun getContent(): UTAny? {
        val body = pinnedValues.getValue(JcPinnedKey.requestBody()) ?: return null
        return withJsonSerialize(exprResolver.resolvePinnedValue(body))
    }

    override fun getContentTypeName(): UTString? {
        val contentType = pinnedValues.getValue(JcPinnedKey.requestMediaTypeName()) ?: return null
        val name = exprResolver.resolvePinnedValue(contentType)
        check(name is UTString)
        return name
    }

    override fun getParameters(): List<JcSpringHttpParameter> {
        val parametersRaw = collectAndResolve(JcSpringPinnedValueSource.REQUEST_PARAM)
        return parametersRaw.mapNotNull { (key, value) -> JcSpringHttpParameter(key, value as UTStringArray) }
    }

    override fun getUriVariables(): List<UTAny> {
        val pathVariables = collectAndResolve(JcSpringPinnedValueSource.REQUEST_PATH_VARIABLE)
        return sortRequestUriVariables(getPath(), pathVariables)
    }
}
