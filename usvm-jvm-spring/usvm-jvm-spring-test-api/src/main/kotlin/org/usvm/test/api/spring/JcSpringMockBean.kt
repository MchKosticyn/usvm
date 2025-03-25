package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestMockObject

interface JcSpringMockBean {
    fun getFields(): Map<JcField, UTestExpression>
    fun getMethods(): Map<JcMethod, List<UTestExpression>>
    fun getType(): JcType
}

class JcSpringRealMockBean(private val origin: UTestMockObject) : JcSpringMockBean {
    override fun getFields() = origin.fields
    override fun getMethods() = origin.methods
    override fun getType() = origin.type
}
