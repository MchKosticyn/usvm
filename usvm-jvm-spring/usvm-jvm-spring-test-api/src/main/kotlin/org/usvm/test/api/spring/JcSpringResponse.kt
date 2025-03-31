package org.usvm.test.api.spring

interface JcSpringResponse {
    fun getStatus(): UTInt
    fun getContent(): UTString?
    fun getHeaders(): List<JcSpringHttpHeader>
    fun getCookies(): List<JcSpringHttpCookie>
}