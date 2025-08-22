package org.usvm.spring.query.specification;

public class SortSpecification {
    public ASpecification spec;
    public boolean isAscending = true; // true -  ASC, false - DESC
    public boolean isNullsLast = true; // true - LAST, false - FIRST

    public SortSpecification() {
    }

    public SortSpecification(ASpecification spec, boolean isAscending, boolean isNullsLast) {
        this.spec = spec;
        this.isAscending = isAscending;
        this.isNullsLast = isNullsLast;
    }
}
