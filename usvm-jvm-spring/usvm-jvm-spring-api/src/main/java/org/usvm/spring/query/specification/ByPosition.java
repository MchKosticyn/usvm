package org.usvm.spring.query.specification;

public class ByPosition extends ASpecification {
    public int pos;

    public ByPosition() {

    }

    public ByPosition(int pos) {
        this.pos = pos;
    }

    @Override
    public <R, C> R accept(ISpecificationVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
