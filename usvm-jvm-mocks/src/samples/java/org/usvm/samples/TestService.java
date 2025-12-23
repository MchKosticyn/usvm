package org.usvm.samples;

import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
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
    public void compute(int id) {
        Service s = Mockito.mock(Service.class);

        String [] tags = s.getTags();
        Profile profile = s.getProfile(id);
        String name = s.getName(id);
        String name2 = s.getName(2);
        int age = s.getAge(id);
        int ssn = profile.getSsn();

        assert name2.equals("Alice");
        String [] tags1 = {"a", "b", "c"};
        assert Arrays.equals(tags, tags1);
        assert age > 18 && age < 45;
        assert ssn > 9999 && ssn < 1000000;
        assert Objects.equals(name, "Bob");
    }
}
