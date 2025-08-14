package org.usvm.spring.query.expression.special;

import org.usvm.spring.query.Select;
import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

public class Subquery extends AExpression {
    public Select query;

    public Subquery() {

    }

    public Subquery(Select query) {
        this.query = query;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
