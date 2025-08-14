package org.usvm.spring.query.path;

import java.util.List;

public class SimplePath {
    public String root; // not case-sensitive
    public List<String> cont; // case-sensitive

    public SimplePath() {

    }

    public SimplePath(String root, List<String> cont) {
        this.root = root;
        this.cont = cont;
    }
}
