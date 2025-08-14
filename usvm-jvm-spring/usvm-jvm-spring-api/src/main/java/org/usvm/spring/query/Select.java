package org.usvm.spring.query;

import java.util.List;

public class Select {
    public List<Order> orders;
    public Query query;

    public Select() {

    }

    public Select(List<Order> orders, Query query) {
        this.orders = orders;
        this.query = query;
    }

    public void addOrder(Order order) {
        orders.add(order);
    }
}
