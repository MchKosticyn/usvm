package org.usvm.samples;

import java.util.function.Function;

public class Lambdas {
    private static Function<Integer, Integer> createLambda() {
        return (x) -> x + 1;
    }

    public static boolean test(int x) {
        Function<Integer, Integer> lambda = createLambda();

        if (lambda.apply(x).equals(x + 1)) {
            return true;
        }

        return false;
    }
}
