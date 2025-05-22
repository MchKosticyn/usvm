package org.usvm.samples;

public class Concretization {
    private enum TestEnum {
        A, B, C
    }

    public static void dummy(int input) {
        System.out.println(input);
    }

    public static int conretizationModel(int input) {
        Strings.concretize();
        dummy(input);
        TestEnum b = TestEnum.B;
        if (input == 1 && b == TestEnum.B) {
            return 0;
        }

        return 1;
    }
}
