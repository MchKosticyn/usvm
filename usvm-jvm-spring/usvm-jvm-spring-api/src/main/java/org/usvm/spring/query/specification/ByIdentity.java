package org.usvm.spring.query.specification;

public class ByIdentity extends ASpecification {
    public String name;

    public ByIdentity() {

    }

    public ByIdentity(String name) {
        this.name = name;
    }

    @Override
    public <R, C> R accept(ISpecificationVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
