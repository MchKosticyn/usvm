package org.usvm.spring.query.expression.special;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;
import org.usvm.spring.query.parameter.AParameter;

public class Parameter extends AExpression {
    public AParameter param;

    public Parameter() {}


    public Parameter(AParameter param) {
        this.param = param;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
