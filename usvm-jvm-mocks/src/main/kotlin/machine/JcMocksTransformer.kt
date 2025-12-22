package machine

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import io.ksmt.utils.mkConst
import machine.memory.JcMockedMethod
import machine.memory.JcMockedMethodsReading
import machine.memory.JcMockedMethodsRegionId
import machine.memory.JcMockedMethodsValue
import org.jacodb.api.jvm.JcType
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.JcComposer
import org.usvm.machine.JcExprTranslator
import org.usvm.machine.JcSoftConstraintsProvider
import org.usvm.machine.JcTransformer
import org.usvm.machine.USizeSort
import org.usvm.machine.interpreter.statics.JcStaticFieldReading
import org.usvm.machine.jctx
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelEvaluator
import org.usvm.solver.UExprTranslator
import org.usvm.solver.URegionDecoder
import org.usvm.solver.USoftConstraintsProvider

interface JcMocksTransformer : JcTransformer {
    fun <Sort : USort> transform(expr: JcMockedMethodsReading<Sort>): UExpr<Sort>
}

class JcMocksComposer(
    ctx: UContext<USizeSort>,
    memory: UReadOnlyMemory<JcType>,
    ownership: MutabilityOwnership
) : UComposer<JcType, USizeSort>(ctx, memory, ownership), JcMocksTransformer {
    override fun <Sort : USort> transform(expr: JcMockedMethodsReading<Sort>): UExpr<Sort> {
        val ret = memory.read(JcMockedMethodsValue(expr.mockedMethod, expr.sort, expr.type, expr.method))
        for (key in mockedMethods) {
            val newValue = memory.read(key.key)
            mockedMethodsValues[key] = newValue
        }
        return ret
    }

    private val jcComposer = JcComposer(ctx, memory, ownership)
    override fun <Sort : USort> transform(expr: JcStaticFieldReading<Sort>): UExpr<Sort> {
        return jcComposer.transform(expr)
    }
}

class JcMocksExprTranslator(ctx: UContext<USizeSort>) : UExprTranslator<JcType, USizeSort>(ctx), JcMocksTransformer {
    override fun <Sort : USort> transform(expr: JcMockedMethodsReading<Sort>): UExpr<Sort> =
        getOrPutRegionDecoder(expr.regionId) {
            JcMockedMethodsDecoder(expr.regionId, this)
        }.translate(expr)

    private val jcExprTranslator = JcExprTranslator(ctx)
    override fun <Sort : USort> transform(expr: JcStaticFieldReading<Sort>): UExpr<Sort> {
        return jcExprTranslator.transform(expr)
    }
}

class JcMockedMethodsDecoder<Sort : USort>(
    private val regionId: JcMockedMethodsRegionId<Sort>,
    private val translator: UExprTranslator<*, *>
) : URegionDecoder<JcMockedMethodsValue<Sort>, Sort> {
    private val translated = mutableMapOf<JcMockedMethod, UExpr<Sort>>()

    fun translate(expr: JcMockedMethodsReading<Sort>): UExpr<Sort> =
        translated.getOrPut(expr.mockedMethod) {
            expr.sort.mkConst("${expr.mockedMethod.enclosingClass}_${regionId.sort}_${expr.mockedMethod.method}")
        }

    override fun decodeLazyRegion(
        model: UModelEvaluator<*>,
        assertions: List<KExpr<KBoolSort>>
    ): UReadOnlyMemoryRegion<JcMockedMethodsValue<Sort>, Sort> =
        JcMockedMethodsModel(model, translated, translator)
}

class JcMockedMethodsModel<Sort : USort>(
    private val model: UModelEvaluator<*>,
    private val translatedMockedMethods: Map<JcMockedMethod, UExpr<Sort>>,
    private val translator: UExprTranslator<*, *>
) : UReadOnlyMemoryRegion<JcMockedMethodsValue<Sort>, Sort> {
    override fun read(key: JcMockedMethodsValue<Sort>): UExpr<Sort> {
        val t = translatedMockedMethods[key.mockedMethod]
        val translated = t
            ?: translator.translate(
                JcMockedMethodsReading(key.sort.jctx, key.memoryRegionId as JcMockedMethodsRegionId, key.mockedMethod, key.type, key.method, key.sort)
            )
        return model.evalAndComplete(translated)
    }
}

class JcMocksSoftConstraintsProvider(
    ctx: UContext<USizeSort>
) : USoftConstraintsProvider<JcType, USizeSort>(ctx), JcMocksTransformer {
    override fun <Sort : USort> transform(
        expr: JcMockedMethodsReading<Sort>
    ): UExpr<Sort> = transformExpr(expr)

    private val jcSoftConstraintsProvider = JcSoftConstraintsProvider(ctx)
    override fun <Sort : USort> transform(expr: JcStaticFieldReading<Sort>): UExpr<Sort> {
        return jcSoftConstraintsProvider.transform(expr)
    }
}
