package org.usvm.samples;

import org.mockito.Mockito;

interface Adder {
    int add(int a, int b);
}
public class TestAdder {
    public void compute(int a, int b) {
        Adder adder = Mockito.mock(Adder.class);
        Adder adder2 = Mockito.mock(Adder.class);

        int res = adder.add(a, b);
        int res2 = adder2.add(a, b);
        assert (res == 3);
        assert (res2 == 4);
    }
}
