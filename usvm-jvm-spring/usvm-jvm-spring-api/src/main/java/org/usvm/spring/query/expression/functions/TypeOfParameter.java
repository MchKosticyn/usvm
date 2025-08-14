package org.usvm.spring.query.expression.functions;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;
import org.usvm.spring.query.expression.special.Parameter;

public class TypeOfParameter extends AExpression {
    public Parameter param;

    public TypeOfParameter() {

    }

    public TypeOfParameter(Parameter param) {
        this.param = param;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
