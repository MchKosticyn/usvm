package org.usvm.spring.query.predicate;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

public class Not extends AExpression {
    public AExpression predicate;

    public Not() {
    }

    public Not(AExpression predicate) {
        this.predicate = predicate;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
