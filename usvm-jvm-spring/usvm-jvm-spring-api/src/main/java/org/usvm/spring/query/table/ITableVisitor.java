package org.usvm.spring.query.table;

public interface ITableVisitor<R, C> {

    R visit(TableRoot child, C ctx);
    R visit(TableSubquery child, C ctx);
}
