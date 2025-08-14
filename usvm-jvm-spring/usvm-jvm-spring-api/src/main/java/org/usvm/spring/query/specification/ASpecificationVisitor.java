package org.usvm.spring.query.specification;

public abstract class ASpecificationVisitor {
    public abstract <R, C> R accept(ISpecificationVisitor<R, C> visitor, C ctx);
}
