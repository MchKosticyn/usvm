package org.usvm.spring.query.type;

import org.usvm.spring.query.parameter.AParameter;

public class TParam extends AType {
    public AParameter param;

    public TParam() {

    }

    public TParam(AParameter param) {
        this.param = param;
    }

    @Override
    public <R, C> R accept(ITypeVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
