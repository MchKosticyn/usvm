package org.usvm.spring.query.selectfun;

import org.usvm.spring.query.path.Path;

public class Entry extends ASelection {
    public Path path;

    public Entry() {
    }

    public Entry(Path path, String alias) {
        this.path = path;
        this.alias = alias;
    }

    @Override
    public <R, C> R accept(ISelectionVisitor<R, C> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
