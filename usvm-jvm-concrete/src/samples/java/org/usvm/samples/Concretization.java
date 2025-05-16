package org.usvm.samples;

import java.lang.reflect.Constructor;

public class Concretization {
    private enum TestEnum {
        A, B, C
    }

    public static int conretizationModel(int input) {
        Strings.concretize();
        TestEnum b = TestEnum.B;
        if (input == 1 && b == TestEnum.B) {
            return 0;
        }

        return 1;
    }
}
