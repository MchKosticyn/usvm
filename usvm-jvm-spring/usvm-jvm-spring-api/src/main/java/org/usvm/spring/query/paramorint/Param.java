package org.usvm.spring.query.paramorint;

import org.usvm.spring.query.parameter.AParameter;

public class Param extends AParamOrInt {
    public AParameter param;

    public Param() {
    }

    public Param(AParameter param) {
        this.param = param;
    }

    @Override
    public <R, C> R accept(IParamOrIntVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
