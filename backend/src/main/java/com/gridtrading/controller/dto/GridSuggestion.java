package com.gridtrading.controller.dto;

import java.math.BigDecimal;

/**
 * 单个操作建议
 */
public class GridSuggestion {

    /**
     * 网格线 ID
     */
    private Long gridLineId;

    /**
     * 网格层级
     */
    private Integer gridLevel;

    /**
     * 网格类型
     */
    private String gridType;

    /**
     * 操作类型（BUY/SELL）
     */
    private String action;

    /**
     * 建议价格
     */
    private BigDecimal price;

    /**
     * 建议数量
     */
    private BigDecimal quantity;

    /**
     * 建议金额
     */
    private BigDecimal amount;

    /**
     * 数量比例（1.0=全仓，0.5=半仓）
     */
    private BigDecimal quantityRatio;

    /**
     * 建议原因说明
     */
    private String reason;

    // Constructors
    public GridSuggestion() {
    }

    public GridSuggestion(Long gridLineId, Integer gridLevel, String gridType,
                         String action, BigDecimal price, BigDecimal quantity,
                         BigDecimal amount, BigDecimal quantityRatio, String reason) {
        this.gridLineId = gridLineId;
        this.gridLevel = gridLevel;
        this.gridType = gridType;
        this.action = action;
        this.price = price;
        this.quantity = quantity;
        this.amount = amount;
        this.quantityRatio = quantityRatio;
        this.reason = reason;
    }

    // Getters and Setters
    public Long getGridLineId() {
        return gridLineId;
    }

    public void setGridLineId(Long gridLineId) {
        this.gridLineId = gridLineId;
    }

    public Integer getGridLevel() {
        return gridLevel;
    }

    public void setGridLevel(Integer gridLevel) {
        this.gridLevel = gridLevel;
    }

    public String getGridType() {
        return gridType;
    }

    public void setGridType(String gridType) {
        this.gridType = gridType;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getQuantityRatio() {
        return quantityRatio;
    }

    public void setQuantityRatio(BigDecimal quantityRatio) {
        this.quantityRatio = quantityRatio;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}




