package com.gridtrading.controller.dto;

import com.gridtrading.domain.Strategy;
import com.gridtrading.domain.StrategyStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 策略详情 DTO（完整信息）
 */
public class StrategyDetailDTO {

    private Long id;
    private String name;
    private String symbol;
    private BigDecimal basePrice;
    private BigDecimal amountPerGrid;
    private String gridModelVersion;
    private String gridSummary;
    private StrategyStatus status;
    private LocalDateTime createdAt;

    // 资金概览
    private BigDecimal maxCapital;
    private BigDecimal availableCash;
    private BigDecimal investedAmount;
    private BigDecimal position;
    private BigDecimal realizedProfit;
    private BigDecimal lastPrice;
    private BigDecimal expectedProfit;  // 预计收益（所有网格收益总和）

    // 网格模型说明（固定文案）
    private String gridModelDescription;
    private Integer totalGridCount;

    public StrategyDetailDTO() {
    }

    /**
     * 从实体转换为 DTO
     */
    public static StrategyDetailDTO fromEntity(Strategy strategy) {
        StrategyDetailDTO dto = new StrategyDetailDTO();
        
        dto.setId(strategy.getId());
        dto.setName(strategy.getName());
        dto.setSymbol(strategy.getSymbol());
        dto.setBasePrice(strategy.getBasePrice());
        dto.setAmountPerGrid(strategy.getAmountPerGrid());
        dto.setGridModelVersion(strategy.getGridModelVersion() != null ? strategy.getGridModelVersion() : "v2.0");
        dto.setGridSummary(strategy.getGridSummary() != null ? strategy.getGridSummary() : "小网13/中网4/大网2");
        dto.setStatus(strategy.getStatus());
        dto.setCreatedAt(strategy.getCreatedAt());

        // 资金概览（处理 null）
        dto.setMaxCapital(strategy.getMaxCapital() != null ? strategy.getMaxCapital() : BigDecimal.ZERO);
        dto.setAvailableCash(strategy.getAvailableCash() != null ? strategy.getAvailableCash() : BigDecimal.ZERO);
        dto.setInvestedAmount(strategy.getInvestedAmount() != null ? strategy.getInvestedAmount() : BigDecimal.ZERO);
        dto.setPosition(strategy.getPosition() != null ? strategy.getPosition() : BigDecimal.ZERO);
        dto.setRealizedProfit(strategy.getRealizedProfit() != null ? strategy.getRealizedProfit() : BigDecimal.ZERO);
        dto.setLastPrice(strategy.getLastPrice());

        // 网格模型说明
        dto.setGridModelDescription("锚点回撤式网格：小网阶梯回撤，中网锚点反弹，大网极端反弹");
        dto.setTotalGridCount(19);

        return dto;
    }

    // ==================== Getter 和 Setter ====================

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

    public String getGridModelVersion() {
        return gridModelVersion;
    }

    public void setGridModelVersion(String gridModelVersion) {
        this.gridModelVersion = gridModelVersion;
    }

    public String getGridSummary() {
        return gridSummary;
    }

    public void setGridSummary(String gridSummary) {
        this.gridSummary = gridSummary;
    }

    public StrategyStatus getStatus() {
        return status;
    }

    public void setStatus(StrategyStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public BigDecimal getMaxCapital() {
        return maxCapital;
    }

    public void setMaxCapital(BigDecimal maxCapital) {
        this.maxCapital = maxCapital;
    }

    public BigDecimal getAvailableCash() {
        return availableCash;
    }

    public void setAvailableCash(BigDecimal availableCash) {
        this.availableCash = availableCash;
    }

    public BigDecimal getInvestedAmount() {
        return investedAmount;
    }

    public void setInvestedAmount(BigDecimal investedAmount) {
        this.investedAmount = investedAmount;
    }

    public BigDecimal getPosition() {
        return position;
    }

    public void setPosition(BigDecimal position) {
        this.position = position;
    }

    public BigDecimal getRealizedProfit() {
        return realizedProfit;
    }

    public void setRealizedProfit(BigDecimal realizedProfit) {
        this.realizedProfit = realizedProfit;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public String getGridModelDescription() {
        return gridModelDescription;
    }

    public void setGridModelDescription(String gridModelDescription) {
        this.gridModelDescription = gridModelDescription;
    }

    public Integer getTotalGridCount() {
        return totalGridCount;
    }

    public void setTotalGridCount(Integer totalGridCount) {
        this.totalGridCount = totalGridCount;
    }

    public BigDecimal getExpectedProfit() {
        return expectedProfit;
    }

    public void setExpectedProfit(BigDecimal expectedProfit) {
        this.expectedProfit = expectedProfit;
    }
}
