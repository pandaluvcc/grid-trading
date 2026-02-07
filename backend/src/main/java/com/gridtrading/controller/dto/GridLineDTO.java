package com.gridtrading.controller.dto;

import com.gridtrading.domain.GridLine;
import com.gridtrading.domain.GridLineState;
import com.gridtrading.domain.GridType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 网格线 DTO（展示层精度控制）
 */
public class GridLineDTO {

    private Long id;
    private Integer level;
    private GridType gridType;
    private GridLineState state;

    // 计划价格（内部精度8位）
    private BigDecimal plannedBuyPrice;
    private BigDecimal plannedSellPrice;

    // 实际价格（可为null）
    private BigDecimal actualBuyPrice;
    private BigDecimal actualSellPrice;

    // 展示用价格（前端优先显示actual，没有则显示planned）
    private BigDecimal displayBuyPrice;
    private BigDecimal displaySellPrice;

    // 金额和数量
    private BigDecimal buyAmount;
    private BigDecimal buyQuantity;  // 展示层保留2位小数
    private BigDecimal buyQuantityFull;  // 完整8位精度（供编辑使用）
    private BigDecimal sellAmount;

    // 收益
    private BigDecimal profit;
    private BigDecimal profitRate;

    // 是否可编辑实际买入价
    private Boolean canEditActualBuyPrice;

    public GridLineDTO() {
    }

    /**
     * 从实体转换为 DTO（展示层精度控制）
     */
    public static GridLineDTO fromEntity(GridLine gridLine) {
        GridLineDTO dto = new GridLineDTO();

        dto.setId(gridLine.getId());
        dto.setLevel(gridLine.getLevel());
        dto.setGridType(gridLine.getGridType());
        dto.setState(gridLine.getState());

        // 计划价格
        dto.setPlannedBuyPrice(gridLine.getBuyPrice());
        dto.setPlannedSellPrice(gridLine.getSellPrice());

        // 实际价格
        dto.setActualBuyPrice(gridLine.getActualBuyPrice());
        dto.setActualSellPrice(gridLine.getActualSellPrice());

        // 展示价格（优先actual）
        dto.setDisplayBuyPrice(gridLine.getActualBuyPrice() != null ? 
            gridLine.getActualBuyPrice() : gridLine.getBuyPrice());
        dto.setDisplaySellPrice(gridLine.getActualSellPrice() != null ? 
            gridLine.getActualSellPrice() : gridLine.getSellPrice());

        // 金额和数量
        dto.setBuyAmount(gridLine.getBuyAmount());
        dto.setBuyQuantityFull(gridLine.getBuyQuantity());  // 完整精度
        // 展示层保留2位小数
        dto.setBuyQuantity(gridLine.getBuyQuantity() != null ? 
            gridLine.getBuyQuantity().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        dto.setSellAmount(gridLine.getSellAmount());

        // 收益
        dto.setProfit(gridLine.getProfit());
        dto.setProfitRate(gridLine.getProfitRate());

        // 已买入状态才可编辑实际买入价
        dto.setCanEditActualBuyPrice(gridLine.getState() == GridLineState.BOUGHT);

        return dto;
    }

    // ==================== Getter 和 Setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public GridType getGridType() {
        return gridType;
    }

    public void setGridType(GridType gridType) {
        this.gridType = gridType;
    }

    public GridLineState getState() {
        return state;
    }

    public void setState(GridLineState state) {
        this.state = state;
    }

    public BigDecimal getPlannedBuyPrice() {
        return plannedBuyPrice;
    }

    public void setPlannedBuyPrice(BigDecimal plannedBuyPrice) {
        this.plannedBuyPrice = plannedBuyPrice;
    }

    public BigDecimal getPlannedSellPrice() {
        return plannedSellPrice;
    }

    public void setPlannedSellPrice(BigDecimal plannedSellPrice) {
        this.plannedSellPrice = plannedSellPrice;
    }

    public BigDecimal getActualBuyPrice() {
        return actualBuyPrice;
    }

    public void setActualBuyPrice(BigDecimal actualBuyPrice) {
        this.actualBuyPrice = actualBuyPrice;
    }

    public BigDecimal getActualSellPrice() {
        return actualSellPrice;
    }

    public void setActualSellPrice(BigDecimal actualSellPrice) {
        this.actualSellPrice = actualSellPrice;
    }

    public BigDecimal getDisplayBuyPrice() {
        return displayBuyPrice;
    }

    public void setDisplayBuyPrice(BigDecimal displayBuyPrice) {
        this.displayBuyPrice = displayBuyPrice;
    }

    public BigDecimal getDisplaySellPrice() {
        return displaySellPrice;
    }

    public void setDisplaySellPrice(BigDecimal displaySellPrice) {
        this.displaySellPrice = displaySellPrice;
    }

    public BigDecimal getBuyAmount() {
        return buyAmount;
    }

    public void setBuyAmount(BigDecimal buyAmount) {
        this.buyAmount = buyAmount;
    }

    public BigDecimal getBuyQuantity() {
        return buyQuantity;
    }

    public void setBuyQuantity(BigDecimal buyQuantity) {
        this.buyQuantity = buyQuantity;
    }

    public BigDecimal getBuyQuantityFull() {
        return buyQuantityFull;
    }

    public void setBuyQuantityFull(BigDecimal buyQuantityFull) {
        this.buyQuantityFull = buyQuantityFull;
    }

    public BigDecimal getSellAmount() {
        return sellAmount;
    }

    public void setSellAmount(BigDecimal sellAmount) {
        this.sellAmount = sellAmount;
    }

    public BigDecimal getProfit() {
        return profit;
    }

    public void setProfit(BigDecimal profit) {
        this.profit = profit;
    }

    public BigDecimal getProfitRate() {
        return profitRate;
    }

    public void setProfitRate(BigDecimal profitRate) {
        this.profitRate = profitRate;
    }

    public Boolean getCanEditActualBuyPrice() {
        return canEditActualBuyPrice;
    }

    public void setCanEditActualBuyPrice(Boolean canEditActualBuyPrice) {
        this.canEditActualBuyPrice = canEditActualBuyPrice;
    }
}
