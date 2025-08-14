package org.usvm.spring.query.predicate;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

public class Or extends AExpression {
    public AExpression left;
    public AExpression right;

    public Or() {

    }

    public Or(AExpression left, AExpression right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
