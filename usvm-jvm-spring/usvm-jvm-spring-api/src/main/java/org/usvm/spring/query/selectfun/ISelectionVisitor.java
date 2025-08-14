package org.usvm.spring.query.selectfun;

public interface ISelectionVisitor<R, C> {

    R visit(Entry child, C ctx);
    R visit(Expression child, C ctx);
    R visit(Instance child, C ctx);
    R visit(JpaSelect child, C ctx);
}
