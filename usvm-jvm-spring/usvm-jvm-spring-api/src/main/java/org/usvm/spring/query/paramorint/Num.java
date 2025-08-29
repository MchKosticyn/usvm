package org.usvm.spring.query.paramorint;

public class Num extends AParamOrInt {
    public int value;

    public Num() {
    }

    public Num(int value) {
        this.value = value;
    }

    @Override
    public <R, C> R accept(IParamOrIntVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
