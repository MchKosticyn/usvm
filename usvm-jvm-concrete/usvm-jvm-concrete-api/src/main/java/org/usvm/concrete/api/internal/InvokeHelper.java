package org.usvm.concrete.api.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

@SuppressWarnings("unused")
public class InvokeHelper {
    @SuppressWarnings("deprecation")
    public static Object invokeMethod(Method method, Object obj, Object[] args) throws InvocationTargetException, IllegalAccessException {
        method.setAccessible(true);
        return method.invoke(obj, args);
    }

    @SuppressWarnings("deprecation")
    public static Object newInstance(Constructor<?> ctor, Object[] params) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        ctor.setAccessible(true);
        return ctor.newInstance(params);
    }
}
