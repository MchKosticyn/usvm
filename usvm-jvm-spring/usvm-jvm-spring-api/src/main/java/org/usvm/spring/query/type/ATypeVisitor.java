package org.usvm.spring.query.type;

public abstract class ATypeVisitor {
    public abstract <R, C> R accept(ITypeVisitor<R, C> visitor, C ctx);
}
