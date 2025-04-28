package org.usvm.samples;

import java.lang.reflect.Constructor;

public class Constructors {
    public static SampleClass reflectionConstructorCall(int input) throws Exception {
        Constructor<SampleClass> sampleClassConstructor = SampleClass.class
                .getConstructor(int.class, int.class);
        return sampleClassConstructor.newInstance(input, input);
    }
}
