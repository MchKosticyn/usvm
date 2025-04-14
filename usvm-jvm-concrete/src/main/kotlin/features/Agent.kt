package features

import java.lang.instrument.Instrumentation

@Suppress("UNUSED")
class Agent {
    companion object {
        private var instrumentation: Instrumentation? = null

        @JvmStatic
        fun premain(arguments: String?, instrumentation: Instrumentation) {
            this.instrumentation = instrumentation
            instrumentation.addTransformer(JcBytecodeGetter())
        }

        // TODO: use for concrete memory #CM
        fun getSize(obj: Any): Long =
            instrumentation?.getObjectSize(obj)
                ?: error("Agent not initialized")
    }
}
