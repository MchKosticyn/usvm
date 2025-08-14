package org.usvm.spring.query.expression;

public abstract class AExpressionVisitor {
    public abstract <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx);
}
