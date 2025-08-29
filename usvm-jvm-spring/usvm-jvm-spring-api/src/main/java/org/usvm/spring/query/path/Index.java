package org.usvm.spring.query.path;

import org.usvm.spring.query.expression.AExpression;

public class Index {
    public AExpression ix;
    public GeneralPath cont;

    public Index() {
    }

    public Index(AExpression ix, GeneralPath cont) {
        this.ix = ix;
        this.cont = cont;
    }
}
