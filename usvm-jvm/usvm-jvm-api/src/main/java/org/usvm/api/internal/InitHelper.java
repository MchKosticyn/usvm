package org.usvm.api.internal;

import java.util.function.Function;

public class InitHelper {
    public static Function<String, Void> afterClinitAction;
    public static Function<Object, Void> afterInitAction;

    public static void afterClinit(String className) {
        afterClinitAction.apply(className);
    }

    public static void afterInit(Object newObj) {
        afterInitAction.apply(newObj);
    }
}
