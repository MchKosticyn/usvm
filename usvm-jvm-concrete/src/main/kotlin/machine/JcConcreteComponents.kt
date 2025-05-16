package machine

import machine.model.JcConcreteModelDecoder
import org.usvm.UMachineOptions
import org.usvm.machine.JcComponents
import org.usvm.machine.JcExprTranslator
import org.usvm.machine.JcTypeSystem

class JcConcreteComponents(
    typeSystem: JcTypeSystem,
    options: UMachineOptions,
) : JcComponents(typeSystem, options) {

    override fun createLazyModelDecoder(translator: JcExprTranslator): JcConcreteModelDecoder {
        return JcConcreteModelDecoder(translator)
    }
}
