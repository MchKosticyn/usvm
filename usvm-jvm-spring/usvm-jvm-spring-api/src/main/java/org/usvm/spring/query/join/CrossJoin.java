package org.usvm.spring.query.join;

public class CrossJoin extends AJoin {

    public CrossJoin() {
    }

    @Override
    public <R, C, A> R accept(IJoinVisitor<R, C, A> visitor, A args, C ctx) {
        return visitor.visit(this, args, ctx);
    }
}
