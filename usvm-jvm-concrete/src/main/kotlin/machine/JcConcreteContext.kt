package machine

import org.jacodb.api.jvm.JcClasspath
import org.usvm.machine.JcComponents
import org.usvm.machine.JcContext

class JcConcreteContext(
    cp: JcClasspath,
    components: JcComponents,
) : JcContext(cp, components) {

    override val statesForkProvider by lazy { JcConcreteStateForker(components.mkStatesForkProvider()) }
}
