package org.usvm.spring.query.join;

public class JpaCollectionJoin extends AJoin {

    public JpaCollectionJoin() {

    }

    @Override
    public <R, C, A> R accept(IJoinVisitor<R, C, A> visitor, A args, C ctx) {
        return visitor.visit(this, args, ctx);
    }
}
