package org.usvm.spring.query.predicate;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

public class And extends AExpression {
    public AExpression left;
    public AExpression right;

    public And() {
    }

    public And(AExpression left, AExpression right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
