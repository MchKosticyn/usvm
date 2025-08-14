package org.usvm.spring.query.type.primitive;

import org.usvm.spring.query.type.AType;
import org.usvm.spring.query.type.ITypeVisitor;

public class TLong extends AType {

    public TLong() {

    }

    @Override
    public <R, C> R accept(ITypeVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
