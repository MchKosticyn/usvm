package org.usvm.spring.query.table;

public abstract class ATableVisitor {
    public abstract <R, C> R accept(ITableVisitor<R, C> visitor, C ctx);
}
