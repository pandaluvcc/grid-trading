package com.gridtrading.controller.dto;

import java.math.BigDecimal;

/**
 * 更新最新价格请求
 */
public class UpdateLastPriceRequest {

    private BigDecimal lastPrice;

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }
}

