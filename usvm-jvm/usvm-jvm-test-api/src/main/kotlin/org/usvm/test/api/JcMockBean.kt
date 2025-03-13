package org.usvm.test.api

class JcMockBean(
    private val mock: UTestMockObject,
    val initStatements: List<UTestInst>
) {
    val fields get() = mock.fields
    val methods get() = mock.methods
    val type get() = mock.type
}
