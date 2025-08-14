package org.usvm.spring.query.predicate.functions;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;
import org.usvm.spring.query.path.Path;

public class Member extends AExpression {
    public AExpression expr;
    public Path of;

    public Member() {

    }

    public Member(AExpression expr, Path of) {
        this.expr = expr;
        this.of = of;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
