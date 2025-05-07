package org.usvm.test.api.spring

interface JcSpringResponse {
    fun getStatus(): UTAny
    fun getContent(): UTString?
    fun getHeaders(): List<JcSpringHttpHeader>
    fun getCookies(): List<JcSpringHttpCookie>
    fun getViewName(): UTString?
}
