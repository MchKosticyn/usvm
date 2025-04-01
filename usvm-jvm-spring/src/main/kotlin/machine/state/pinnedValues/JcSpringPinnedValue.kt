package machine.state.pinnedValues

import org.jacodb.api.jvm.JcType
import org.usvm.UExpr
import org.usvm.USort

class JcPinnedValue(
    private val expr: UExpr<out USort>,
    private val type: JcType,
) {
    fun getExpr() = expr
    fun getType() = type
}