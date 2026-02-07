package com.gridtrading.controller.dto;

import java.math.BigDecimal;

/**
 * 创建策略请求 DTO
 * <p>
 * 固定模板网格策略：不需要传入网格比例参数
 * - 网格类型固定：SMALL=5%, MEDIUM=15%, LARGE=30%
 * - 网格数量固定：19条
 * - 网格顺序固定（见策略文档）
 */
public class CreateStrategyRequest {

    private String name;
    private String symbol;
    private BigDecimal basePrice;
    private BigDecimal amountPerGrid;

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
}
