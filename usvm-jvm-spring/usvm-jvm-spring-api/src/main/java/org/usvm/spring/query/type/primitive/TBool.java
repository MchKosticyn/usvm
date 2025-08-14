package org.usvm.spring.query.type.primitive;

import org.usvm.spring.query.type.AType;
import org.usvm.spring.query.type.ITypeVisitor;

public class TBool extends AType {

    public TBool() {

    }

    @Override
    public <R, C> R accept(ITypeVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
