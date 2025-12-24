package org.usvm.samples.testOutput;

import org.mockito.Mockito;

interface A {
    int foo(int x);
}
interface B {
    int bar(int x, int y);
}
interface C {
    int baz(int x);
}

public class TestABC {
    void compute(int a, int b, int c) {
        A mA = Mockito.mock(A.class);
        B mB = Mockito.mock(B.class);
        C mC = Mockito.mock(C.class);

        Mockito.when(mA.foo(0)).thenReturn(3);
        
        int r1 = mA.foo(a);
        Mockito.when(mA.foo(0)).thenReturn(5);
        
        int r2 = mA.foo(r1 + b);

        Mockito.when(mB.bar(0, 0)).thenReturn(15);
        
        int r3 = mB.bar(r1, r2);
        Mockito.when(mC.baz(0)).thenReturn(10);
        
        int r4 = mC.baz(r3 + c);


        assert(r1 == 3);
        assert(r2 == r1 + b + 2);
        assert(r3 == r1 * r2);
        assert(r4 == r3 - 5);
    }
}
