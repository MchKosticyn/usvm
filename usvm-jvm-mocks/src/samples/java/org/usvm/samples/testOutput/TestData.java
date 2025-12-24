package org.usvm.samples.testOutput;

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
    public void compute(String k1, String k2) throws InstantiationException {
        Cache c1 = Mockito.mock(Cache.class);
        Cache c2 = Mockito.mock(Cache.class);

        Data v = ReflectionUtils.<Data>allocateInstance(Data.class);
        ReflectionUtils.setFieldValue(v, "value", 0);
        ReflectionUtils.setFieldValue(v, "meta", null);
        Mockito.when(c1.load("")).thenReturn(v);
        
        Data d1 = c1.load(k1);
        Data v1 = ReflectionUtils.<Data>allocateInstance(Data.class);
        ReflectionUtils.setFieldValue(v1, "value", 0);
        ReflectionUtils.setFieldValue(v1, "meta", null);
        Mockito.when(c2.load("")).thenReturn(v1);
        
        Data d2 = c2.load(k2);

        Mockito.when(c1.exists("")).thenReturn(false);
        
        boolean e1 = c1.exists(k1);
        Mockito.when(c2.exists("")).thenReturn(true);
        
        boolean e2 = c2.exists(k2);

        Mockito.when(d1.getValue()).thenReturn(15);
        
        Mockito.when(d2.getValue()).thenReturn(-5);
        
        assert d1.getValue() + d2.getValue() == 10;
        assert e1 != e2;
        Mockito.when(d1.getMeta()).thenReturn("x");
        
        assert d1.getMeta().equals("x");
        Mockito.when(d2.getMeta()).thenReturn("y");
        
        assert d2.getMeta().equals("y");
}

}

