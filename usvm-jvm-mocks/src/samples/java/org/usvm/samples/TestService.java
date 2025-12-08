package org.usvm.samples;

import org.mockito.Mockito;

import java.util.List;
import java.util.Objects;

interface Service {
    String getName(int id);
    Profile getProfile(int id);
    List<String> getTags();
    int getAge(int id);
}

class Profile {
    public final String name;
    public final int age;

    public Profile(String name, int age) {
        this.name = name;
        this.age = age;
    }
}

public class TestService {
    public void compute(int id) {
        Service s = Mockito.mock(Service.class);

        String name = s.getName(id);
        Profile profile = s.getProfile(id);
        List<String> tags = s.getTags();
        int age = s.getAge(id);

        assert age > 18;
        assert age < 45;
        assert Objects.equals(name, "Bob");
//        assert name.length() == 3;
        assert profile.age > 18;
        assert tags.size() == 2;
    }
}
