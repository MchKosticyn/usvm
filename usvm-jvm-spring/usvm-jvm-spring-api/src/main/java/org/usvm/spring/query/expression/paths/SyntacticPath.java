package org.usvm.spring.query.expression.paths;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

public class SyntacticPath extends AExpression {

    public SyntacticPath() {
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
