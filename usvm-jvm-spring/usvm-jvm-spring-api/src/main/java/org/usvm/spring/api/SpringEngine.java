package org.usvm.spring.api;

import java.util.ArrayList;
import java.util.List;

public class SpringEngine {

    public static void println(String message) {
        System.out.println(message);
    }

    public static void startAnalysis() { }

    public static void endAnalysis() { }

    public static List<List<Object>> allControllerPaths() {
        return new ArrayList<>();
    }
}
