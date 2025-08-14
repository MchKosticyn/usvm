package org.usvm.spring.query;

import org.usvm.spring.query.join.AJoin;
import org.usvm.spring.query.table.ATable;

import java.util.List;

public class TableWithJoins {
    public ATable root;
    public List<AJoin> joins;

    public TableWithJoins() {

    }

    public TableWithJoins(ATable root, List<AJoin> joins) {
        this.root = root;
        this.joins = joins;
    }
}
