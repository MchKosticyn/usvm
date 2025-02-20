package org.usvm.samples.concretemem;

public class SampleA {
    public void SomeMethod(int a, double b) {
        if (b * a > 0) {
            var x = b - a;
        } else {
            var y = b + a;
        }
    }

    public static int add2(int x) {
        return x + 2;
    }

    public Integer genericUsage(Wg<Wg<Integer>> wg1, Wg<Wg<Integer>> wg2) {
        wg1.value.value = 1;
        wg2.value.value = 2;
        return (wg1.value.value + wg2.value.value);
    }
}
