package org.usvm.spring.query.selectfun;

import org.usvm.spring.query.expression.IExpressionVisitor;

public abstract class ASelectionVisitor {
    public abstract <R, C> R accept(ISelectionVisitor<R, C> visitor, C ctx);
}
