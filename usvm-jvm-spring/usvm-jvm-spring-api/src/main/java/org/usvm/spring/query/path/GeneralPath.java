package org.usvm.spring.query.path;

public class GeneralPath {
    public SimplePath path;
    public Index index;

    public GeneralPath() {

    }

    public GeneralPath(SimplePath path, Index index) {
        this.path = path;
        this.index = index;
    }
}
