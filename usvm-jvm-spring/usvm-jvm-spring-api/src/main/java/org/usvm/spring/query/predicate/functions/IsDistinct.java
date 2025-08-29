package org.usvm.spring.query.predicate.functions;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

public class IsDistinct extends AExpression {
    public AExpression expr;
    public AExpression from;

    public IsDistinct() {
    }

    public IsDistinct(AExpression expr, AExpression from) {
        this.expr = expr;
        this.from = from;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
