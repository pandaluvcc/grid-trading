package com.gridtrading.controller.dto;

import com.gridtrading.domain.TradeType;

import java.math.BigDecimal;

/**
 * Tick 执行请求 DTO（重构版 - 方案B）
 * 前端明确指定操作哪个网格线，后端不做自动匹配
 */
public class TickRequest {

    private Long gridLineId;      // 前端指定的网格线ID
    private BigDecimal price;
    private TradeType type;
    private BigDecimal quantity;
    private BigDecimal fee;
    private String tradeTime;

    public Long getGridLineId() {
        return gridLineId;
    }

    public void setGridLineId(Long gridLineId) {
        this.gridLineId = gridLineId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public TradeType getType() {
        return type;
    }

    public void setType(TradeType type) {
        this.type = type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public String getTradeTime() {
        return tradeTime;
    }

    public void setTradeTime(String tradeTime) {
        this.tradeTime = tradeTime;
    }
}
