package org.usvm.spring.query.predicate.functions;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

public class Between extends AExpression {
    public AExpression expr;
    public AExpression left;
    public AExpression right;

    public Between() {
    }

    public Between(AExpression expr, AExpression left, AExpression right) {
        this.expr = expr;
        this.left = left;
        this.right = right;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
