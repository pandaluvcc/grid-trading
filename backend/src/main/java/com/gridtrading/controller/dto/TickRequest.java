package com.gridtrading.controller.dto;

import java.math.BigDecimal;

/**
 * Tick 执行请求 DTO
 */
public class TickRequest {

    private BigDecimal price;

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
