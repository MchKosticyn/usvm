package org.usvm.spring.query;

import org.usvm.spring.query.expression.AExpression;

public class Where {
    public AExpression predicate;

    public Where() {
    }

    public Where(AExpression predicate) {
        this.predicate = predicate;
    }
}
