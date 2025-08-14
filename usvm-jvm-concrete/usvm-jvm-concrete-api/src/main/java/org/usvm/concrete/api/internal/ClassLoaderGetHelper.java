package org.usvm.concrete.api.internal;

import java.util.function.Supplier;

public class ClassLoaderGetHelper {
    public static Supplier<ClassLoader> replaceGetClassLoaderAction;

    public static ClassLoader replaceGetClassLoader() {
        return replaceGetClassLoaderAction.get();
    }
}
