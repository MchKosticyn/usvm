package org.usvm.spring.query;

import org.usvm.spring.query.specification.GroupBySpecification;

import java.util.List;

public class GroupBy {
    public List<GroupBySpecification> specs;

    public GroupBy() {

    }

    public GroupBy(List<GroupBySpecification> specs) {
        this.specs = specs;
    }
}
