package org.usvm.spring.query.selectfun;

public class JpaSelect extends ASelection {

    public JpaSelect() {
    }

    public JpaSelect(String alias) {
        this.alias = alias;
    }

    @Override
    public <R, C> R accept(ISelectionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
