package org.usvm.spring.query.table;

// ... FROM SomeTable AS st
public class TableRoot extends ATable {
    public EntityName entityName;

    public TableRoot() {

    }

    public TableRoot(EntityName entityName, String alias) {
        this.entityName = entityName;
        this.alias = alias;
    }

    @Override
    public <R, C> R accept(ITableVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
