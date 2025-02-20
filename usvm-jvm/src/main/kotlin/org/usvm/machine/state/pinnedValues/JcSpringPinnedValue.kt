package org.usvm.machine.state.pinnedValues

import org.jacodb.api.jvm.JcType
import org.usvm.UExpr
import org.usvm.USort

class JcSpringPinnedValue (
    private val expr: UExpr<out USort>,
    private val type: JcType,
){
    fun getExpr(): UExpr<out USort> {
        return expr
    }
    
    fun getType(): JcType {
        return type
    }
}