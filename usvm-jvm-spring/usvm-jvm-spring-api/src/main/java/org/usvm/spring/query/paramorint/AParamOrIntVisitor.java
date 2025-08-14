package org.usvm.spring.query.paramorint;

public abstract class AParamOrIntVisitor {
    public abstract <R, C> R accept(IParamOrIntVisitor<R, C> visitor, C ctx);
}
