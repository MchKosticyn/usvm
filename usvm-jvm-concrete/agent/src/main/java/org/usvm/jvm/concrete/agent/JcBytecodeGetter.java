package org.usvm.jvm.concrete.agent;

import org.usvm.jvm.concrete.JcConcreteClassLoader;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public class JcBytecodeGetter implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className == null || !(loader instanceof JcConcreteClassLoader cl)) {
            return classfileBuffer;
        }

        cl.addTypeBytes(className, classfileBuffer);

        return classfileBuffer;
    }
}
