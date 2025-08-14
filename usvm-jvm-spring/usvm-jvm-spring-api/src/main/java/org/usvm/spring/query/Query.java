package org.usvm.spring.query;

import org.usvm.spring.query.selectfun.SelectFunction;

public class Query {
    public From from = null;
    public Where where = null;
    public SelectFunction select = null;
    public GroupBy groupBy = null;
    public Having having = null;

    public Query() {

    }

    public Query(From from, Where where, SelectFunction select, GroupBy groupBy, Having having) {
        this.from = from;
        this.where = where;
        this.select = select;
        this.groupBy = groupBy;
        this.having = having;
    }
}
