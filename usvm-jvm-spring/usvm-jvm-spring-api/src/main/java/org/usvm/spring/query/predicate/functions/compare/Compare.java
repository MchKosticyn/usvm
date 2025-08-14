package org.usvm.spring.query.predicate.functions.compare;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

public class Compare extends AExpression {
    public AExpression left;
    public AExpression right;
    public Operator operator;

    public Compare() {

    }

    public Compare(AExpression left, AExpression right, Operator operator) {
        this.left = left;
        this.right = right;
        this.operator = operator;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
