package machine.model

import io.ksmt.solver.KModel
import org.jacodb.api.jvm.JcType
import org.usvm.machine.JcContext
import org.usvm.machine.JcExprTranslator
import org.usvm.model.AddressesMapping
import org.usvm.model.ULazyModelDecoder
import org.usvm.model.UModelEvaluator

class JcConcreteModelDecoder(
    translator: JcExprTranslator
) : ULazyModelDecoder<JcType>(translator) {

    override fun buildModelEvaluator(model: KModel, addressesMapping: AddressesMapping): UModelEvaluator<*> {
        return JcConcreteModelEvaluator(translator.ctx as JcContext, model, addressesMapping)
    }
}
