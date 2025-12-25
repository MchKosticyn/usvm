package org.usvm.samples;

import org.mockito.Mockito;

interface Cache {
    Data load(String key);
    boolean exists(String key);
}

class Data {
    public final int value;
    public final String meta;
    public int  getValue() {
        return value;
    }
    public String  getMeta() {
        return meta;
    }
    public Data(int value, String meta) {
        this.value = value;
        this.meta = meta;
    }
}

public class TestData {
    public void compute(String k1, String k2) {
        Cache c1 = Mockito.mock(Cache.class);
        Cache c2 = Mockito.mock(Cache.class);

        Data d1 = c1.load(k1);
        Data d2 = c2.load(k2);

        boolean e1 = c1.exists(k1);
        boolean e2 = c2.exists(k2);

        assert d1.getValue() + d2.getValue() == 10;
        assert e1 != e2;
        assert d1.getMeta().equals("x");
        assert d2.getMeta().equals("y");
}

}

