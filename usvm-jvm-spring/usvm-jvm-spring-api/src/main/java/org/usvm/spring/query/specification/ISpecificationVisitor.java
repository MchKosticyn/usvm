package org.usvm.spring.query.specification;

public interface ISpecificationVisitor<R, C> {

    R visit(ByExpression child, C ctx);
    R visit(ByIdentity child, C ctx);
    R visit(ByPosition child, C ctx);
}
