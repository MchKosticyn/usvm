package org.usvm.spring.query.expression.literals;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

import java.time.LocalDateTime;

public class LTime extends AExpression {
    public LocalDateTime value;

    public LTime() {
    }

    public LTime(LocalDateTime value) {
        this.value = value;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
