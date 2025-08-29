package org.usvm.spring.query.expression.literals;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

public class LBinary extends AExpression {
    public byte[] bytes;

    public LBinary() {
    }

    public LBinary(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
