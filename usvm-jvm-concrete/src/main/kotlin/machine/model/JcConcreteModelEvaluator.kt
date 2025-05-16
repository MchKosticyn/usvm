package machine.model

import io.ksmt.decl.KDecl
import io.ksmt.solver.KModel
import io.ksmt.sort.KArray2Sort
import io.ksmt.sort.KArraySort
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.machine.USizeSort
import org.usvm.model.AddressesMapping
import org.usvm.model.UMemory1DArray
import org.usvm.model.UMemory2DArray
import org.usvm.model.UModelEvaluator

class JcConcreteModelEvaluator(
    ctx: UContext<USizeSort>,
    model: KModel,
    addressesMapping: AddressesMapping,
) : UModelEvaluator<USizeSort>(ctx, model, addressesMapping) {

    companion object {
        var shouldComplete = true
    }

    override fun <Sort : USort> complete(expr: UExpr<Sort>): UExpr<Sort>? {
        if (shouldComplete)
            return super.complete(expr)

        return null
    }

    override fun <Idx : USort, Value : USort> complete(
        translated: KDecl<KArraySort<Idx, Value>>,
        stores: UPersistentHashMap<UExpr<Idx>, UExpr<Value>>
    ): UMemory1DArray<Idx, Value>? {
        if (shouldComplete)
            return super.complete(translated, stores)

        return null
    }

    override fun <Idx1 : USort, Idx2 : USort, Value : USort> complete(
        translated: KDecl<KArray2Sort<Idx1, Idx2, Value>>,
        stores: UPersistentHashMap<Pair<UExpr<Idx1>, UExpr<Idx2>>, UExpr<Value>>
    ): UMemory2DArray<Idx1, Idx2, Value>? {
        if (shouldComplete)
            return super.complete(translated, stores)

        return null
    }
}
