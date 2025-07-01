package org.usvm.samples;

import org.usvm.api.Engine;

public class Strings {

    public static void concretize() { }
    public static boolean concatTest(Integer x) {
        String s = "kek " + x;
        concretize();
        if ("".equals(s) || s.isEmpty() || s.isBlank())
            return false;
        return true;
    }

    public static boolean isEqualToAaa(String input) {
        Engine.assume(input != null);
        // To force _assumeInvariants
        Engine.assume(!input.isEmpty());
        if (Engine.fastStringEquals(input, "Aaa"))
            return true;

        return false;
    }
}
