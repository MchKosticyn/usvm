package testGeneration

import machine.state.pinnedValues.JcPinnedKey.Companion.responseContent
import machine.state.pinnedValues.JcPinnedKey.Companion.responseStatus
import machine.state.pinnedValues.JcSpringPinnedValueSource
import machine.state.pinnedValues.JcSpringPinnedValues
import org.jacodb.api.jvm.ext.int
import org.usvm.api.util.JcTestStateResolver
import org.usvm.test.api.spring.*

class JcSpringPinnedValuesResponse(
    private val pinnedValues: JcSpringPinnedValues,
    private val exprResolver: JcSpringTestExprResolver
) : JcSpringResponse {
    private val stringType = exprResolver.ctx.stringType
    private val intType = exprResolver.ctx.cp.int

    @Suppress("SameParameterValue")
    private fun collectAndConcretize(pinnedValueSource: JcSpringPinnedValueSource): Map<UTString, UTAny> {
        return pinnedValues.collectAndConcretize(
            exprResolver,
            JcTestStateResolver.ResolveMode.CURRENT,
            pinnedValueSource,
            stringType
        )
    }

    private fun handleMultiValue(value: UTAny) : UTStringArray {
        return handleStringMultiValue(
            exprResolver,
            value,
            stringType,
            intType
        )
    }
    
    override fun getStatus(): UTInt {
        val status = pinnedValues.getValue(responseStatus())?.let { exprResolver.resolvePinnedValue(it) }
        check(status != null && status is UTInt)
        return status
    }
    
    override fun getCookies(): List<JcSpringHttpCookie> {
        val cookies = collectAndConcretize(JcSpringPinnedValueSource.RESPONSE_COOKIE)
        return cookies.mapNotNull { (key, value) -> JcSpringHttpCookie(key, value as UTString) }
    }

    override fun getContent(): UTString {
        val content = pinnedValues.getValue(responseContent())?.let { exprResolver.resolvePinnedValue(it) }
        check(content != null && content is UTString)
        return content
    } 
    
    override fun getHeaders(): List<JcSpringHttpHeader> {
        val headers = collectAndConcretize(JcSpringPinnedValueSource.RESPONSE_COOKIE)
        return headers.mapNotNull { (key, value) -> JcSpringHttpHeader(key, handleMultiValue(value as UTString)) }
    }
}