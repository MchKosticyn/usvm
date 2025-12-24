package org.usvm.samples.testOutput;

import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Objects;


interface Service {
    String getName(int id);
    Profile getProfile(int id);
    String [] getTags();
    int getAge(int id);
}

class Profile {
    public final String name;
    public final int ssn;
    public int  getSsn() {
        return ssn;
    }
    public String getName() {
        return name;
    }

    public Profile(String name, int ssn) {
        this.name = name;
        this.ssn = ssn;
    }
}

public class TestService {
    public void compute(int id) throws InstantiationException {
        Service s = Mockito.mock(Service.class);

        String[] v = new String[3];
        v[0] = "a";
        v[1] = "b";
        v[2] = "c";
        Mockito.when(s.getTags()).thenReturn(v);
        
        String [] tags = s.getTags();
        Profile v1 = ReflectionUtils.<Profile>allocateInstance(Profile.class);
        ReflectionUtils.setFieldValue(v1, "name", null);
        ReflectionUtils.setFieldValue(v1, "ssn", 0);
        Mockito.when(s.getProfile(0)).thenReturn(v1);
        
        Profile profile = s.getProfile(id);
        Mockito.when(s.getName(0)).thenReturn("Bob");
        
        String name = s.getName(id);
        Mockito.when(s.getName(0)).thenReturn("Alice");
        
        String name2 = s.getName(2);
        Mockito.when(s.getAge(0)).thenReturn(29);
        
        int age = s.getAge(id);
        Mockito.when(profile.getSsn()).thenReturn(475776);
        
        int ssn = profile.getSsn();

        assert name2.equals("Alice");
        String [] tags1 = {"a", "b", "c"};
        assert Arrays.equals(tags, tags1);
        assert age > 18 && age < 45;
        assert ssn > 9999 && ssn < 1000000;
        assert Objects.equals(name, "Bob");
    }
}
