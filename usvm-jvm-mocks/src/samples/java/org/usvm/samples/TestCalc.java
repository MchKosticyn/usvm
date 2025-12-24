package org.usvm.samples;

import org.mockito.Mockito;

interface Count {
    public int f(int x);
    public int g(int x);
}

class TestCalc {
    void compute(int a, int b) {
        Count s1 = Mockito.mock(Count.class); // g -> 16, f -> 5
        Count s2 = Mockito.mock(Count.class); // f -> 0, g -> 7

        int r1 = s1.f(a);
        int r2 = s2.g(b);

        int sum = r1 + r2;

        if (a > 0) {
            int r3 = s1.g(sum);
            assert(r3 == sum + 10);
        } else {
            int r4 = s2.g(sum);
            assert(r4 == sum - 5);
        }

        assert(sum == 12);
        assert(r1 == 5);
        assert(r2 == 7);
    }
}


