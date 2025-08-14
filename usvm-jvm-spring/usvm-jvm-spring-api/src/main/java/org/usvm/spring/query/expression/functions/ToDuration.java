package org.usvm.spring.query.expression.functions;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;
import org.usvm.spring.query.expression.literals.Datetime;

public class ToDuration extends AExpression {
    public AExpression expr;
    public Datetime datetime;

    public ToDuration() {

    }

    public ToDuration(AExpression expr, Datetime datetime) {
        this.expr = expr;
        this.datetime = datetime;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
