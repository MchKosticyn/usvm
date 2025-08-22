package org.usvm.spring.query.expression.functions;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;
import org.usvm.spring.query.path.Path;

public class TypeOfPath extends AExpression {
    public Path path;

    public TypeOfPath() {
    }

    public TypeOfPath(Path path) {
        this.path = path;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
