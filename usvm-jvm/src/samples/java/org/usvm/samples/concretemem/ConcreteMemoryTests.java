package org.usvm.samples.concretemem;

public class ConcreteMemoryTests {

    public static boolean test1(int i) {
        DoublyLinkedList<Integer> list = new DoublyLinkedList<>();
        list.add(i);
        if (list.isEmpty())
            return false;

        if (i == 12 && list.getFirst() != 12)
            return false;

        return true;
    }
}
