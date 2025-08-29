package org.usvm.spring.query.path;

public class Path {
    public GeneralPath root;
    public SimplePath cont;
    public String alias;

    public Path() {
    }

    public Path(GeneralPath root, SimplePath cont, String alias) {
        this.root = root;
        this.cont = cont;
        this.alias = alias;
    }
}
