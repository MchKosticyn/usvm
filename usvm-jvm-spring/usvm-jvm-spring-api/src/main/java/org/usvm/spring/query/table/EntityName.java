package org.usvm.spring.query.table;

import java.util.List;

// Name of class (may contain points: java.lang.Boolean)
// TODO: polymorphism like FROM java.lang.Object
public class EntityName {
    public List<String> names;

    public EntityName() {

    }

    public EntityName(List<String> names) {
        this.names = names;
    }
}
