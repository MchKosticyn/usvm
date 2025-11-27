package org.usvm.samples;

import org.mockito.Mockito;
public class Calculator {
    interface Adder {
        int add(int a, int b);
    }
    public int compute(int a, int b) {
        Adder adder = Mockito.mock(Adder.class);

        return adder.add(a, b);
    }
}


