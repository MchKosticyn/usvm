package org.usvm.spring.query;

import org.usvm.spring.query.paramorint.AParamOrInt;
import org.usvm.spring.query.specification.SortSpecification;

import java.util.List;

public class Order {
    public List<SortSpecification> sorts;
    public AParamOrInt limit = null;
    public AParamOrInt offset = null;

    public Order() {

    }

    public Order(List<SortSpecification> sorts, AParamOrInt limit, AParamOrInt offset) {
        this.sorts = sorts;
        this.limit = limit;
        this.offset = offset;
    }
}
