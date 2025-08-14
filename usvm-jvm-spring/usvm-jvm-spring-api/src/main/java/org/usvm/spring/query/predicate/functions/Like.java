package org.usvm.spring.query.predicate.functions;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

public class Like extends AExpression {
    public AExpression expr;
    public AExpression pattern;
    public AExpression escape;
    public boolean caseSenc;

    public Like() {

    }

    public Like(AExpression expr, AExpression pattern, AExpression escape, boolean caseSenc) {
        this.expr = expr;
        this.pattern = pattern;
        this.escape = escape;
        this.caseSenc = caseSenc;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
