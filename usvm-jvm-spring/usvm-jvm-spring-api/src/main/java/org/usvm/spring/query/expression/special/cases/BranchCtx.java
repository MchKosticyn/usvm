package org.usvm.spring.query.expression.special.cases;

import org.usvm.spring.query.expression.AExpression;

public class BranchCtx {
    public AExpression pred;
    public AExpression value;

    public BranchCtx() {
    }

    public BranchCtx(AExpression pred, AExpression value) {
        this.pred = pred;
        this.value = value;
    }
}
