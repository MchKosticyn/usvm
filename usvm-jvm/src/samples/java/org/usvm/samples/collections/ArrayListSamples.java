package org.usvm.samples.collections;

import java.util.ArrayList;

public class ArrayListSamples {

    public static boolean arrayListCorrectCopy(ArrayList<String> intList) {
        if (intList.size() < 4)
            return true;
        if (!"123".equals(intList.get(3)))
            return true;
        ArrayList<String> copied = new ArrayList<>(intList);
        if ("123".equals(copied.get(3)))
            return true;
        return false;
    }
}
