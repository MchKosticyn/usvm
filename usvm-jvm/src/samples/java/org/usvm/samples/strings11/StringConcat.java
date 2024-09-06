package org.usvm.samples.strings11;


import static org.usvm.api.mock.UMockKt.assume;

public class StringConcat {
    public static class Test {
        public int x;

        @Override
        public String toString() {
            if (x == 42) {
                throw new IllegalArgumentException();
            }
            return "x = " + x;
        }
    }

    String str;
    public String concatArguments(String a, String b, String c) {
        return a + b + c;
    }

    public int concatWithConstants(String a) {
        String res = '<' + a + '>';

        if (res.equals("<head>")) {
            return 1;
        }

        if (res.equals("<body>")) {
            return 2;
        }

        if (a == null) {
            return 3;
        }

        return 4;
    }

    public String concatWithPrimitives(String a) {
        return a + '#' + 42 + 53.0;
    }

    public String exceptionInToString(Test t) {
        return "Test: " + t + "!";
    }

    public String concatWithField(String a) {
        return a + str + '#';
    }

    public int concatWithPrimitiveWrappers(Integer b, char c) {
        String res = "" + b + c;

        if (res.endsWith("42")) {
            return 1;
        }
        return 2;
    }

    public int sameConcat(String a, String b) {
        assume(a != null && b != null);

        String res1 = '!' + a + '#';
        String res2 = '!' + b + '#';

        if (res1.equals(res2)) {
            return 0;
        } else {
            return 1;
        }
    }

    public String concatStrangeSymbols() {
        return "\u0000" + '#' + '\u0001' + "!\u0002" + "@\u0012\t";
    }

    public static boolean checkStringBuilder(String s, char c, int i) {
        StringBuilder sb = new StringBuilder();
        sb.append(s);
        String a = "str" + c + i;
        sb.append(a);
        String res = sb.toString();
        if (res.charAt("str".length()) != c)
            return false;

//        if (i > 0 && i < 128 && res.charAt(s.length() + "str".length() + 1) != i)
//            return false;

        return true;
    }
}
