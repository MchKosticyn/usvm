package org.usvm.samples.collections;

import org.usvm.api.Engine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

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

    public static boolean arrayListSublistIteration(ArrayList<Integer> intList) {
        Engine.assume(intList != null);
        Engine.assume(intList.size() == 3);

        for (Object element : intList)
            Engine.assume(element instanceof Integer);

        List<Integer> subList = intList.subList(1, intList.size());
        int index = 0;

        for (Integer entry : subList) {
            index++;
            if (!Objects.equals(entry, intList.get(index)))
                return false;
        }

        return true;
    }
}
