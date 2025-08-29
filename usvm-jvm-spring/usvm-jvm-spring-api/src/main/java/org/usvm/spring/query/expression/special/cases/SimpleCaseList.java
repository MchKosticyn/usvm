package org.usvm.spring.query.expression.special.cases;

import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.expression.IExpressionVisitor;

import java.util.List;

// CASE a WHEN 'a' THEN 1 WHEN 'b' THEN 2 ELSE 3 END
public class SimpleCaseList extends AExpression {
    public AExpression caseValue;
    public List<BranchCtx> branches;
    public AExpression elseBranch;

    public SimpleCaseList() {
    }

    public SimpleCaseList(AExpression caseValue, List<BranchCtx> branches, AExpression elseBranch) {
        this.caseValue = caseValue;
        this.branches = branches;
        this.elseBranch = elseBranch;
    }

    @Override
    public <R, C> R accept(IExpressionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
