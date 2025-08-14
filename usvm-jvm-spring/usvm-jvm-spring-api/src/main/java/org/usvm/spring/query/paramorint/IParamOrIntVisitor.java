package org.usvm.spring.query.paramorint;

public interface IParamOrIntVisitor<R, C> {

    R visit(Num child, C ctx);
    R visit(Param child, C ctx);
}
