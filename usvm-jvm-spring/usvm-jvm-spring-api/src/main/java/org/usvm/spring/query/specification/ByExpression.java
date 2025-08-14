package org.usvm.spring.query.specification;

import org.usvm.spring.query.expression.AExpression;

public class ByExpression extends ASpecification {
    public AExpression expr;

    public Object translateMethod = null;

    public ByExpression() {

    }

    public ByExpression(AExpression expr) {
        this.expr = expr;
    }

    @Override
    public <R, C> R accept(ISpecificationVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
