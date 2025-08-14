package org.usvm.spring.query.predicate.functions.InFunction;

import org.usvm.spring.query.expression.IExpressionVisitor;
import org.usvm.spring.query.parameter.AParameter;

public class ParameterIn extends AInValues {
    public AParameter param;

    public ParameterIn() {

    }

    public ParameterIn(AParameter param) {
        this.param = param;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
