package org.usvm.spring.query.type;

import java.util.List;

public class TTuple extends AType {
    public List<AType> types;

    public TTuple() {

    }

    public TTuple(List<AType> types) {
        this.types = types;
    }

    @Override
    public <R, C> R accept(ITypeVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
