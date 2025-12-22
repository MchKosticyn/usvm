package machine

import org.jacodb.api.jvm.JcType
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UMachineOptions
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.JcComponents
import org.usvm.machine.JcTypeSystem
import org.usvm.machine.USizeSort
import org.usvm.memory.UReadOnlyMemory
import org.usvm.model.ULazyModelDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.USoftConstraintsProvider

class JcMocksComponents(
    typeSystem: JcTypeSystem,
    options: UMachineOptions
) : JcComponents(typeSystem, options) {
    override fun <Context : UContext<USizeSort>> mkComposer(
        ctx: Context
    ): (UReadOnlyMemory<JcType>, MutabilityOwnership) -> UComposer<JcType, USizeSort> =
        { memory: UReadOnlyMemory<JcType>, ownership: MutabilityOwnership -> JcMocksComposer(ctx, memory, ownership) }

    override fun <Context : UContext<USizeSort>> buildTranslatorAndLazyDecoder(
        ctx: Context
    ): Pair<UExprTranslator<JcType, USizeSort>, ULazyModelDecoder<JcType>> {
        val translator = JcMocksExprTranslator(ctx)
        val decoder: ULazyModelDecoder<JcType> = ULazyModelDecoder(translator)
        return translator to decoder
    }
    override fun <Context : UContext<USizeSort>> mkSoftConstraintsProvider(
        ctx: Context
    ): USoftConstraintsProvider<JcType, USizeSort> = JcMocksSoftConstraintsProvider(ctx)
}
