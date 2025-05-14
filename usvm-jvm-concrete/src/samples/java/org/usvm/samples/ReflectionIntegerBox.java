package org.usvm.samples;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionIntegerBox {
    public static int inner(int i) {
        if (i == 3)
            return 3;

        return i + 1;
    }

    public static Object test(int i) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method inner = ReflectionIntegerBox.class.getMethod("inner", int.class);
        return inner.invoke(null, i);
    }
}
