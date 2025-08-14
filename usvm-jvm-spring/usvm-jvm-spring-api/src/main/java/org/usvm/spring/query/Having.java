package org.usvm.spring.query;

import org.usvm.spring.query.expression.AExpression;

public class Having {
    public AExpression predicate;

    public Having() {

    }

    public Having(AExpression predicate) {
        this.predicate = predicate;
    }
}
