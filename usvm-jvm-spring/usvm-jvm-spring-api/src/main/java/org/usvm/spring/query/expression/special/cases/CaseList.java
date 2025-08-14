package org.usvm.spring.query.expression.special.cases;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

import java.util.List;

// CASE WHEN a == 'a' THEN 1 WHEN a == 'b' THEN 2 ELSE 3 END
public class CaseList extends AExpression {
    public List<BranchCtx> branches;
    public AExpression elseBranch;

    public CaseList() {

    }

    public CaseList(List<BranchCtx> branches, AExpression elseBranch) {
        this.branches = branches;
        this.elseBranch = elseBranch;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
