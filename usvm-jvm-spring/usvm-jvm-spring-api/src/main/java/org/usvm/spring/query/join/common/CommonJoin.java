package org.usvm.spring.query.join.common;

import org.jacodb.api.jvm.JcMethod;
import org.usvm.spring.query.expression.AExpression;
import org.usvm.spring.query.join.AJoin;
import org.usvm.spring.query.join.IJoinVisitor;
import org.usvm.spring.query.path.Path;

public class CommonJoin extends AJoin {
    public Path target;
    public AExpression pred;
    public CommonJoinType type;

    public JcMethod mapper = null;

    public CommonJoin() {

    }

    public CommonJoin(Path targer, AExpression pred, CommonJoinType type) {
        this.target = targer;
        this.pred = pred;
        this.type = type;
    }

    @Override
    public <R, C, A> R accept(IJoinVisitor<R, C, A> visitor, A args, C ctx) {
        return visitor.visit(this, args, ctx);
    }
}
