package org.usvm.samples;

import java.util.HashMap;

public class Encoding {

    private static void concretize() { }

    public static int test(int a) {
        HashMap<Integer, Integer> map = new HashMap<>();
        map.put(1, 2);
        map.put(2, 3);
        map.put(3, 4);
        map.put(a, 5);
        concretize();
        Integer value = map.get(a);
        if (value != null && value == 5)
            return 0;

        return 0;
    }
}
