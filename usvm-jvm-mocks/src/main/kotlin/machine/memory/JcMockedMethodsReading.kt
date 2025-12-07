package machine.memory

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.USymbol
import machine.JcMocksTransformer

class JcMockedMethodsReading<Sort : USort> internal constructor(
    ctx: UContext<*>,
    val regionId: JcMockedMethodsRegionId<Sort>,
    val mockedMethod: JcMockedMethod,
    override val sort: Sort,
): USymbol<Sort>(ctx) {
    override fun accept(transformer: KTransformerBase): UExpr<Sort> {
        require(transformer is JcMocksTransformer) { "Expected a JcMocksTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(
        other,
        { regionId },
        { mockedMethod },
        { sort }
    )

    override fun internHashCode(): Int = hash(regionId, mockedMethod, sort)

    override fun print(printer: ExpressionPrinter) {
        printer.append(regionId.toString())
        printer.append("[")
        printer.append(mockedMethod.toString())
        printer.append("]")
    }
}