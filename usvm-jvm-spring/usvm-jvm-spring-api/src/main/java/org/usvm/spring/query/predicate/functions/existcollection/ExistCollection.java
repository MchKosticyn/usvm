package org.usvm.spring.query.predicate.functions.existcollection;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;
import org.usvm.spring.query.path.SimplePath;

public class ExistCollection extends AExpression {
    public ColQuantifierCtx quantifier;
    public SimplePath path;

    public ExistCollection() {

    }

    public ExistCollection(ColQuantifierCtx quantifier, SimplePath path) {
        this.quantifier = quantifier;
        this.path = path;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
