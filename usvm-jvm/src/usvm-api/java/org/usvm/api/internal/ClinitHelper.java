package org.usvm.api.internal;

import java.util.function.Function;

public class ClinitHelper {
    public static Function<String, Void> afterClinit;

    public static void afterClinit(String className) {
        afterClinit.apply(className);
    }
}
