package com.gridtrading.controller.dto;

import java.math.BigDecimal;

/**
 * 市场状态信息
 */
public class MarketStatus {
    private BigDecimal lastPrice;
    private BigDecimal positionRatio;
    private Integer boughtGridCount;
    private Integer totalGridCount;
    private Integer recentBuyCount;
    private Integer recentBuyDays;

    public MarketStatus() {
    }

    public MarketStatus(BigDecimal lastPrice, BigDecimal positionRatio,
                       Integer boughtGridCount, Integer totalGridCount,
                       Integer recentBuyCount, Integer recentBuyDays) {
        this.lastPrice = lastPrice;
        this.positionRatio = positionRatio;
        this.boughtGridCount = boughtGridCount;
        this.totalGridCount = totalGridCount;
        this.recentBuyCount = recentBuyCount;
        this.recentBuyDays = recentBuyDays;
    }

    // Getters and Setters
    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public BigDecimal getPositionRatio() {
        return positionRatio;
    }

    public void setPositionRatio(BigDecimal positionRatio) {
        this.positionRatio = positionRatio;
    }

    public Integer getBoughtGridCount() {
        return boughtGridCount;
    }

    public void setBoughtGridCount(Integer boughtGridCount) {
        this.boughtGridCount = boughtGridCount;
    }

    public Integer getTotalGridCount() {
        return totalGridCount;
    }

    public void setTotalGridCount(Integer totalGridCount) {
        this.totalGridCount = totalGridCount;
    }

    public Integer getRecentBuyCount() {
        return recentBuyCount;
    }

    public void setRecentBuyCount(Integer recentBuyCount) {
        this.recentBuyCount = recentBuyCount;
    }

    public Integer getRecentBuyDays() {
        return recentBuyDays;
    }

    public void setRecentBuyDays(Integer recentBuyDays) {
        this.recentBuyDays = recentBuyDays;
    }
}

