package org.usvm.spring.query.join;

public abstract class AJoinVisitor {
    public abstract <R, C, A> R accept(IJoinVisitor<R, C, A> visitor, A args, C ctx);
}
