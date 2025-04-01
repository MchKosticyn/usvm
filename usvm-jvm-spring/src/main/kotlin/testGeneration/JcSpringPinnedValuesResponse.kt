package testGeneration

import machine.state.pinnedValues.JcPinnedKey.Companion.responseContent
import machine.state.pinnedValues.JcPinnedKey.Companion.responseStatus
import machine.state.pinnedValues.JcSpringPinnedValueSource
import machine.state.pinnedValues.JcSpringPinnedValues
import org.usvm.api.util.JcTestStateResolver
import org.usvm.test.api.spring.JcSpringHttpCookie
import org.usvm.test.api.spring.JcSpringHttpHeader
import org.usvm.test.api.spring.JcSpringResponse
import org.usvm.test.api.spring.UTAny
import org.usvm.test.api.spring.UTString
import org.usvm.test.api.spring.UTStringArray

class JcSpringPinnedValuesResponse(
    private val pinnedValues: JcSpringPinnedValues,
    private val exprResolver: JcSpringTestExprResolver
) : JcSpringResponse {
    private val stringType = exprResolver.ctx.stringType

    @Suppress("SameParameterValue")
    private fun collectAndResolve(pinnedValueSource: JcSpringPinnedValueSource): Map<UTString, UTAny> {
        return pinnedValues.collectAndResolve(
            exprResolver,
            JcTestStateResolver.ResolveMode.CURRENT,
            pinnedValueSource,
            stringType
        )
    }

    override fun getStatus(): UTAny {
        val status = pinnedValues.getValue(responseStatus())
            ?.let { exprResolver.resolvePinnedValue(it) }
        check(status != null)
        return status
    }
    
    override fun getCookies(): List<JcSpringHttpCookie> {
        val cookies = collectAndResolve(JcSpringPinnedValueSource.RESPONSE_COOKIE)
        return cookies.mapNotNull { (key, value) -> JcSpringHttpCookie(key, value as UTString) }
    }

    override fun getContent(): UTString {
        val content = pinnedValues.getValue(responseContent())?.let { exprResolver.resolvePinnedValue(it) }
        check(content != null && content is UTString)
        return content
    } 
    
    override fun getHeaders(): List<JcSpringHttpHeader> {
        val headers = collectAndResolve(JcSpringPinnedValueSource.RESPONSE_COOKIE)
        return headers.mapNotNull { (key, value) -> JcSpringHttpHeader(key, value as UTStringArray) }
    }
}
