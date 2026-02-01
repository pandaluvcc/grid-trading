package com.gridtrading.controller.dto;

import java.math.BigDecimal;

/**
 * 创建策略请求 DTO
 */
public class CreateStrategyRequest {

    private String name;
    private BigDecimal basePrice;
    private Integer gridCount;
    private BigDecimal gridSpacing;
    private BigDecimal amountPerGrid;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public Integer getGridCount() {
        return gridCount;
    }

    public void setGridCount(Integer gridCount) {
        this.gridCount = gridCount;
    }

    public BigDecimal getGridSpacing() {
        return gridSpacing;
    }

    public void setGridSpacing(BigDecimal gridSpacing) {
        this.gridSpacing = gridSpacing;
    }

    public BigDecimal getAmountPerGrid() {
        return amountPerGrid;
    }

    public void setAmountPerGrid(BigDecimal amountPerGrid) {
        this.amountPerGrid = amountPerGrid;
    }
}
