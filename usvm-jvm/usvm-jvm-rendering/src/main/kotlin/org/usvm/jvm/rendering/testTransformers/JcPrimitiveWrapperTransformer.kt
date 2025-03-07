package org.usvm.jvm.rendering.testTransformers

import org.jacodb.api.jvm.JcField
import org.usvm.test.api.UTestConstExpression
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestSetFieldStatement
import org.usvm.test.api.UTestStatement
import java.util.IdentityHashMap

class JcPrimitiveWrapperTransformer: JcTestTransformer() {

    private val toReplace = IdentityHashMap<UTestExpression, UTestConstExpression<*>>()

    private val primitiveWrappers = setOf(
        "java.lang.Boolean",
        "java.lang.Short",
        "java.lang.Integer",
        "java.lang.Long",
        "java.lang.Float",
        "java.lang.Double",
        "java.lang.Byte",
        "java.lang.Character"
    )

    private val JcField.isWrapperValueField: Boolean
        get() = name == "value" && primitiveWrappers.contains(enclosingClass.name)

    override fun transform(stmt: UTestSetFieldStatement): UTestStatement? {
        if (stmt.field.isWrapperValueField) {
            toReplace.put(stmt.instance, stmt.value)
        }

        return super.transform(stmt)
    }
}
