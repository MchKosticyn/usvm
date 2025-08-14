package org.usvm.spring.query.join;


import org.usvm.spring.query.join.common.CommonJoin;

public interface IJoinVisitor<R, C, A> {

    R visit(CrossJoin child, A args, C ctx);
    R visit(CommonJoin child, A args, C ctx);
    R visit(JpaCollectionJoin child, A args, C ctx);
}
