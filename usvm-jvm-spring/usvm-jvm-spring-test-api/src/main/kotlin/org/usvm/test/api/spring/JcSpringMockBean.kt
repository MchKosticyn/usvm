package org.usvm.test.api.spring

import org.usvm.test.api.UTestMockObject

open class JcMockBean(
    private val mock: UTestMockObject
) {
    val fields get() = mock.fields
    val methods get() = mock.methods
    val type get() = mock.type
}
