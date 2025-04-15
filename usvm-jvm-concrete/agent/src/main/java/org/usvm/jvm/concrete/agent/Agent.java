package org.usvm.jvm.concrete.agent;

import java.lang.instrument.Instrumentation;

public class Agent {
    private static Instrumentation instrumentation = null;

    public static void premain(String arguments, Instrumentation inst) {
        instrumentation = inst;
        instrumentation.addTransformer(new JcBytecodeGetter());
    }

    public static long getSize(Object o) {
        return instrumentation.getObjectSize(o);
    }
}
