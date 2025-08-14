package org.usvm.spring.query.selectfun;

import java.util.List;

public class SelectFunction {
    public boolean isDistinct;
    public List<ASelection> selections;

    public Object cachedSelector = null;
    public boolean isGrouped = false;

    public SelectFunction() {

    }

    public SelectFunction(boolean isDistinct, List<ASelection> selections) {
        this.isDistinct = isDistinct;
        this.selections = selections;
    }
}
