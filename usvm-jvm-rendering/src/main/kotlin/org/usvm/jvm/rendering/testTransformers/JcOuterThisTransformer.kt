package org.usvm.jvm.rendering.testTransformers

import org.jacodb.api.jvm.JcField
import org.usvm.test.api.UTestNullExpression
import org.usvm.test.api.UTestSetFieldStatement
import org.usvm.test.api.UTestStatement

class JcOuterThisTransformer: JcTestTransformer() {

    private val JcField.isOuterThisField: Boolean
        get() = name == "this$0"

    override fun transform(stmt: UTestSetFieldStatement): UTestStatement? {
        if (stmt.field.isOuterThisField && stmt.value is UTestNullExpression)
            return null

        return super.transform(stmt)
    }
}
