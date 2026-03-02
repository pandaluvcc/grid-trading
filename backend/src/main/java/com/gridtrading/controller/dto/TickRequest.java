package com.gridtrading.controller.dto;

import com.gridtrading.domain.TradeType;

import java.math.BigDecimal;

/**
 * Tick 执行请求 DTO
 */
public class TickRequest {

    private BigDecimal price;
    private TradeType type;
    private BigDecimal quantity;
    private BigDecimal fee;
    private String tradeTime;

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
