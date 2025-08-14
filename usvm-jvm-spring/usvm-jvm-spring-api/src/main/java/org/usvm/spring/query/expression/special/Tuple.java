package org.usvm.spring.query.expression.special;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

import java.util.List;

public class Tuple extends AExpression {
    public List<AExpression> elems;

    public Tuple() {

    }

    public Tuple(List<AExpression> elems) {
        this.elems = elems;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
