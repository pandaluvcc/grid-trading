package com.gridtrading.controller.dto;

import com.gridtrading.domain.Strategy;
import com.gridtrading.domain.StrategyStatus;

import java.math.BigDecimal;
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
    private LocalDateTime createdAt;

    public StrategyResponse() {
    }

    /**
     * 从 Strategy 实体转换
     */
    public static StrategyResponse fromEntity(Strategy strategy) {
        StrategyResponse response = new StrategyResponse();
        response.setId(strategy.getId());
        response.setName(strategy.getName());
        response.setStatus(strategy.getStatus());
        response.setBasePrice(strategy.getBasePrice());
        // 计算总网格数
        response.setGridCount(strategy.getGridCountDown() + strategy.getGridCountUp());
        response.setCurrentPrice(strategy.getLastPrice());
        response.setCreatedAt(strategy.getCreatedAt());
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
}
