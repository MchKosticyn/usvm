package org.usvm.spring.query.table;

import org.usvm.spring.query.Select;

// ... FROM (SELECT ...) AS sub
public class TableSubquery extends ATable {
    public Select subquery;

    public TableSubquery() {
    }

    public TableSubquery(Select subquery, String alias) {
        this.subquery = subquery;
        this.alias = alias;
    }

    @Override
    public <R, C> R accept(ITableVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
