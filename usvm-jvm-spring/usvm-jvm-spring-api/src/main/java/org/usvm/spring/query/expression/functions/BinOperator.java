package org.usvm.spring.query.expression.functions;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

public class BinOperator extends AExpression {
    public AExpression left;
    public AExpression right;
    public FOperator operator;

    public BinOperator() {

    }

    public BinOperator(AExpression left, AExpression right, FOperator operator) {
        this.left = left;
        this.right = right;
        this.operator = operator;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
