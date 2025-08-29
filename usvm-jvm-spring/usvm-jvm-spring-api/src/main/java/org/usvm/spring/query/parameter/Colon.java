package org.usvm.spring.query.parameter;

import org.usvm.spring.query.expression.IExpressionVisitor;

public class Colon extends AParameter {
    public String name;

    public Colon() {
    }

    public Colon(String name) {
        this.name = name;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
