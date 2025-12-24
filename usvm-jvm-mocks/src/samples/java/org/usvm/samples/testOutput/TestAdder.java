package org.usvm.samples.testOutput;

import org.mockito.Mockito;

interface Adder {
    int add(int a, int b);
}
public class TestAdder {
    public void compute(int a, int b) {
        Adder adder = Mockito.mock(Adder.class);
        Adder adder2 = Mockito.mock(Adder.class);

        Mockito.when(adder.add(0, 0)).thenReturn(3);
        
        int res = adder.add(a, b);
        Mockito.when(adder2.add(0, 0)).thenReturn(4);
        
        int res2 = adder2.add(a, b);
        assert (res == 3);
        assert (res2 == 4);
    }
}
