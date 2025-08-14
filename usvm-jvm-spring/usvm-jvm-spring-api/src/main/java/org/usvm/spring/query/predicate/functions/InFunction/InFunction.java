package org.usvm.spring.query.predicate.functions.InFunction;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

public class InFunction extends AExpression {
    public AExpression expr;
    public AInValues list;

    public InFunction() {

    }

    public InFunction(AExpression expr, AInValues list) {
        this.expr = expr;
        this.list = list;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
