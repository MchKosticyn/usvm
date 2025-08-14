package org.usvm.spring.query.expression;

public abstract class AExpression extends AExpressionVisitor {
    public boolean isGrouped = false;
    public Object cached = null;
}
