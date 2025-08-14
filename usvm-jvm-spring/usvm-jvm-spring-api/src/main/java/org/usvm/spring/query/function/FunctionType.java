package org.usvm.spring.query.function;

public enum FunctionType {

    // https://docs.jboss.org/hibernate/orm/6.4/querylanguage/html_single/Hibernate_Query_Language.html#aggregation
    COUNT("count", true, true),
    AVG("avg", true, true),
    MIN("min",  true, false),
    MAX("max", true, false),
    SUM("sum", true, false),
    VAR_POP("varPop", true, true),
    VAR_SAMP("varSamp", true, true),
    STDDEV_POP("stddevPop", true, true),
    STDDEV_SAMP("stddevSamp", true, true),
    ANY("any", true, true),
    EVERY("every", true, true),

    DATE("date", false, true);

    public final String name;
    public final boolean isAggregator;
    public final boolean hasCommonRetType;

    FunctionType(String name, boolean isAggregator, boolean hasCommonRetType) {
        this.name = name;
        this.isAggregator = isAggregator;
        this.hasCommonRetType = hasCommonRetType;
    }
}
