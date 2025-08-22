package org.usvm.spring.query;

import java.util.List;

public class From {
    public List<TableWithJoins> tables;

    public From() {
    }

    public From(List<TableWithJoins> tables) {
        this.tables = tables;
    }
}
