package com.gridtrading.controller.dto;

import com.gridtrading.domain.Strategy;
import com.gridtrading.domain.StrategyStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 策略响应 DTO
 */
public class StrategyResponse {

    private Long id;
    private String name;
    private StrategyStatus status;
    private BigDecimal basePrice;
    private Integer gridCount;
    private BigDecimal currentPrice;
    private BigDecimal lastPrice;
    private BigDecimal realizedProfit;
    private String symbol;
    private LocalDateTime createdAt;
    private BigDecimal position;
    private BigDecimal costPrice;
    private BigDecimal positionProfit;
    private BigDecimal positionProfitPercent;
    private BigDecimal positionRatio;
    private BigDecimal marketValue; // 市值 = 持仓数量 × 当前现价

    public StrategyResponse() {
    }

    /**
     * 从 Strategy 实体转换
     */
    public static StrategyResponse fromEntity(Strategy strategy) {
        StrategyResponse response = new StrategyResponse();
        response.setId(strategy.getId());
        response.setName(strategy.getName());
        response.setSymbol(strategy.getSymbol());
        response.setStatus(strategy.getStatus());
        response.setBasePrice(strategy.getBasePrice());
        // 计算总网格数
        response.setGridCount(strategy.getGridCountDown() + strategy.getGridCountUp());
        response.setCurrentPrice(strategy.getLastPrice());
        response.setLastPrice(strategy.getLastPrice());
        response.setRealizedProfit(strategy.getRealizedProfit());
        response.setCreatedAt(strategy.getCreatedAt());
        response.setPosition(strategy.getPosition());
        response.setCostPrice(strategy.getCostPrice());
        response.setPositionProfit(strategy.getPositionProfit());
        response.setPositionProfitPercent(strategy.getPositionProfitPercent());
        response.setPositionRatio(strategy.getPositionRatio());
        // 计算市值 = 持仓数量 × 当前现价
        if (strategy.getPosition() != null && strategy.getLastPrice() != null) {
            response.setMarketValue(strategy.getPosition().multiply(strategy.getLastPrice()).setScale(2, RoundingMode.HALF_UP));
        } else {
            response.setMarketValue(BigDecimal.ZERO);
        }
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public StrategyStatus getStatus() {
        return status;
    }

    public void setStatus(StrategyStatus status) {
        this.status = status;
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

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public BigDecimal getRealizedProfit() {
        return realizedProfit;
    }

    public void setRealizedProfit(BigDecimal realizedProfit) {
        this.realizedProfit = realizedProfit;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getPosition() {
        return position;
    }

    public void setPosition(BigDecimal position) {
        this.position = position;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public BigDecimal getPositionProfit() {
        return positionProfit;
    }

    public void setPositionProfit(BigDecimal positionProfit) {
        this.positionProfit = positionProfit;
    }

    public BigDecimal getPositionProfitPercent() {
        return positionProfitPercent;
    }

    public void setPositionProfitPercent(BigDecimal positionProfitPercent) {
        this.positionProfitPercent = positionProfitPercent;
    }

    public BigDecimal getPositionRatio() {
        return positionRatio;
    }

    public void setPositionRatio(BigDecimal positionRatio) {
        this.positionRatio = positionRatio;
    }

    public BigDecimal getMarketValue() {
        return marketValue;
    }

    public void setMarketValue(BigDecimal marketValue) {
        this.marketValue = marketValue;
    }
}
