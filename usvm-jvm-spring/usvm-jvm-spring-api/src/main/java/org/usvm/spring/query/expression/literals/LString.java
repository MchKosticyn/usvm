package org.usvm.spring.query.expression.literals;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

public class LString extends AExpression {
    public String value;

    public LString() {
    }

    public LString(String value) {
        this.value = value;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
