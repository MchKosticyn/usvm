package org.usvm.spring.query.expression.paths;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;
import org.usvm.spring.query.path.GeneralPath;

public class ExprPath extends AExpression {
    public GeneralPath path;

    public ExprPath() {}


    public ExprPath(GeneralPath path) {
        this.path = path;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
