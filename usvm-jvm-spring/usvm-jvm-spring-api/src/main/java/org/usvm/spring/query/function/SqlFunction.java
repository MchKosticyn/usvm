package org.usvm.spring.query.function;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

import java.util.List;

public class SqlFunction extends AExpression {
    public FunctionType func;
    public List<AExpression> args;
    public boolean isDistinct;

    // for ... SUM(book.id + 1) ... we need to use lambda book.id + 1 on table that we aggregate
    // so we need to add this lambda to repository interface
    public Object cachedSelector = null;

    public SqlFunction() {
    }

    public SqlFunction(FunctionType func, List<AExpression> args, boolean isDistinct) {
        this.func = func;
        this.args = args;
        this.isDistinct = isDistinct;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
