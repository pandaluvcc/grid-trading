package com.gridtrading.controller.dto;

import java.math.BigDecimal;

/**
B
 */
public class CreateStrategyRequest {

    private String name;
    private String symbol;
    private BigDecimal basePrice;
    private BigDecimal amountPerGrid;
    private BigDecimal quantityPerGrid;  // 按数量创建时使用

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public BigDecimal getAmountPerGrid() {
        return amountPerGrid;
    }

    public void setAmountPerGrid(BigDecimal amountPerGrid) {
        this.amountPerGrid = amountPerGrid;
    }

    public BigDecimal getQuantityPerGrid() {
        return quantityPerGrid;
    }

    public void setQuantityPerGrid(BigDecimal quantityPerGrid) {
        this.quantityPerGrid = quantityPerGrid;
    }
}

