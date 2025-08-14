package org.usvm.spring.query.parameter;

import org.usvm.spring.query.expression.IExpressionVisitor;

public class Positional extends AParameter {
    public int pos;

    public Positional() {

    }

    public Positional(int pos) {
        this.pos = pos;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
