package org.usvm.test.api.spring

interface JcSpringRequest {
    fun getCookies(): List<JcSpringHttpCookie>
    fun getHeaders(): List<JcSpringHttpHeader>
    fun getMethod(): JcSpringRequestMethod
    fun getPath(): UTString
    fun getContent(): UTAny?
    fun getParameters(): List<JcSpringHttpParameter>
    fun getUriVariables(): List<UTAny>
}
