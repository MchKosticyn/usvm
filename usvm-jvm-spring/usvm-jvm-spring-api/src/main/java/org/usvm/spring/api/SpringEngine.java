package org.usvm.spring.api;

import java.util.ArrayList;
import java.util.List;

public class SpringEngine {

    public static void println(String message) {
        System.out.println(message);
    }

    public static List<List<Object>> allControllerPaths() {
        return new ArrayList<>();
    }

    public static boolean isSecurityEnabled() {
        return false;
    }

    public static boolean isInsideDatabase() { return false; }

    public static void markAsGoodPath() {
    }

    public static void markAsBadPath() {
    }

    public static void markAsEdgeCasePath() {
    }
}
