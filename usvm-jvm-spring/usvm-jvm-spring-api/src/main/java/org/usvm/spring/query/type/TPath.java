package org.usvm.spring.query.type;

import org.usvm.spring.query.path.SimplePath;

public class TPath extends AType {
    public SimplePath name;

    public TPath() {
    }

    public TPath(SimplePath name) {
        this.name = name;
    }

    @Override
    public <R, C> R accept(ITypeVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
