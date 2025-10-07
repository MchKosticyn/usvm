package org.usvm.samples;

import org.usvm.api.Engine;

import java.util.TreeSet;

public class TreeSets {

    public static boolean symbolicAdd(Integer i) {
        TreeSet<Integer> set = new TreeSet<>();
        set.add(i);

        if (set.contains(i))
            return true;
        return false;
    }

    public static boolean addToSymbolic(TreeSet<Integer> set) {
        Engine.assume(set != null);

        Integer i = 1;
        set.add(i);

        if (set.contains(i))
            return true;
        return false;
    }

    public static boolean symbolicRemove(Integer i) {
        TreeSet<Integer> set = new TreeSet<>();
        set.add(i);
        set.remove(i);

        if (!set.contains(i))
            return true;
        return false;
    }

    public static boolean removeFromSymbolic(TreeSet<Integer> set) {
        Engine.assume(set != null);

        Integer i = 1;
        set.add(i);
        set.remove(i);

        if (!set.contains(i))
            return true;
        return false;
    }
}
