package org.usvm.jvm.concrete.agent;

import org.usvm.jvm.concrete.JcConcreteClassLoader;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class JcBytecodeGetter implements ClassFileTransformer {

    @SuppressWarnings("PatternVariableCanBeUsed")
    @Override
    public byte[] transform(
        ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain,
        byte[] classfileBuffer
    ) {
        if (className == null || !(loader instanceof JcConcreteClassLoader))
            return classfileBuffer;

        JcConcreteClassLoader classLoader = (JcConcreteClassLoader) loader;
        classLoader.addTypeBytes(className, classfileBuffer);

        return classfileBuffer;
    }
}
