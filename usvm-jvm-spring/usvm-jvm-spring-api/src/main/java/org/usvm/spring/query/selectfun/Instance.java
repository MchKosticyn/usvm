package org.usvm.spring.query.selectfun;

public class Instance extends ASelection {
    public org.usvm.spring.query.function.Instance inst;

    public Instance() {

    }

    public Instance(org.usvm.spring.query.function.Instance inst, String alias) {
        this.inst = inst;
        this.alias = alias;
    }

    @Override
    public <R, C> R accept(ISelectionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
