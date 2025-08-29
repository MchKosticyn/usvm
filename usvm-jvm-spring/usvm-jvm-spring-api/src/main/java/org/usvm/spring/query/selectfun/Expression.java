package org.usvm.spring.query.selectfun;

import org.usvm.spring.query.expression.AExpression;

public class Expression extends ASelection {
    public AExpression value;

    public Expression() {
    }

    public Expression(AExpression value, String alias) {
        this.value = value;
        this.alias = alias;
    }

    @Override
    public <R, C> R accept(ISelectionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
