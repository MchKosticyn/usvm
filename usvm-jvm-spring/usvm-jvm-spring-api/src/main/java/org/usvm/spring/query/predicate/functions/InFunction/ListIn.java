package org.usvm.spring.query.predicate.functions.InFunction;

import org.usvm.spring.query.Select;
import org.usvm.spring.query.expression.IExpressionVisitor;

public class ListIn extends AInValues {
    public Select list;

    public ListIn() {

    }

    public ListIn(Select list) {
        this.list = list;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
