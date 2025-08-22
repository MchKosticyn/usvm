package org.usvm.spring.query.type;

public class TNull extends AType {

    public TNull() {
    }

    @Override
    public <R, C> R accept(ITypeVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
