package org.usvm.spring.query.expression.functions;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;
import org.usvm.spring.query.path.Path;
import org.usvm.spring.query.path.SimplePath;

public class NaturalId extends AExpression {
    public Path path;
    public SimplePath cont;

    public NaturalId() {
    }

    public NaturalId(Path path, SimplePath cont) {
        this.path = path;
        this.cont = cont;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
