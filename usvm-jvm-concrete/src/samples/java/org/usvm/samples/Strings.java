package org.usvm.samples;

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
        if ("Aaa".equals(input))
            return true;
        return false;
    }
}
