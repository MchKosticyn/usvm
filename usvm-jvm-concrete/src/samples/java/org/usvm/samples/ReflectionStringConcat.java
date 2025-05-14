package org.usvm.samples;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionStringConcat {
    public static int concatStrings(String x, String y, String z) {
        if (x == null || y == null || z == null)
            return 0;
        String w = x + y + z;
        Strings.concretize();
        if (!w.substring(0, x.length()).equals(x) || !w.substring(x.length(), x.length() + y.length()).equals(y) || !w.substring(x.length() + y.length(), x.length() + y.length() + z.length()).equals(z))
            return 1;
        return 0;
    }

    public static Object test(String x, String y, String z) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method concatStrings = ReflectionStringConcat.class.getMethod("concatStrings", String.class, String.class, String.class);
        return concatStrings.invoke(null, new Object[] {x, y, z});
    }
}
