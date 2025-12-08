package org.usvm.samples;

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

        int r1 = mA.foo(a);              // mock #1
        int r2 = mA.foo(r1 + b);         // mock #2 (same mock, different args)

        int r3 = mB.bar(r1, r2);         // mock #3
        int r4 = mC.baz(r3 + c);         // mock #4


        assert(r1 == 3);
        assert(r2 == r1 + b + 2);
        assert(r3 == r1 * r2);
        assert(r4 == r3 - 5);
    }
}
