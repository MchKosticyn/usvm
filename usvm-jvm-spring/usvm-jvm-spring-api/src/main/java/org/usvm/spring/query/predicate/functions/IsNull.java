package org.usvm.spring.query.predicate.functions;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

public class IsNull extends AExpression {
    public AExpression expr;

    public IsNull() {
    }

    public IsNull(AExpression expr) {
        this.expr = expr;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
