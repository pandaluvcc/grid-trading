package com.gridtrading.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 网格交易策略实体
 */
@Entity
@Table(name = "strategy")
public class Strategy {

    /**
     * 主键 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 策略名称
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 标的代码（如股票代码、币种等）
     */
    @Column(nullable = false, length = 50)
    private String symbol;

    /**
     * 基准价格
     */
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal basePrice;

    /**
     * 网格间距（百分比，如 0.05 表示 5%）
     */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal gridPercent;

    /**
     * 向下网格数量
     */
    @Column(nullable = false)
    private Integer gridCountDown;

    /**
     * 向上网格数量
     */
    @Column(nullable = false)
    private Integer gridCountUp;

    /**
     * 每格买入金额
     */
    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal amountPerGrid;

    /**
     * 最大投入资金
     */
    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal maxCapital;

    /**
     * 策略状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StrategyStatus status;

    /**
     * 创建时间
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 最新价格（引擎更新）
     */
    @Column(precision = 20, scale = 8)
    private BigDecimal lastPrice;

    /**
     * 可用资金
     */
    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal availableCash = BigDecimal.ZERO;

    /**
     * 已投入资金
     */
    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal investedAmount = BigDecimal.ZERO;

    /**
     * 当前持仓数量
     */
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal position = BigDecimal.ZERO;

    /**
     * 已实现收益
     */
    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal realizedProfit = BigDecimal.ZERO;

    /**
     * 关联的网格线集合
     */
    @OneToMany(mappedBy = "strategy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GridLine> gridLines = new ArrayList<>();

    /**
     * 关联的交易记录集合
     */
    @OneToMany(mappedBy = "strategy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TradeRecord> tradeRecords = new ArrayList<>();

    /**
     * JPA 要求的无参构造器
     */
    public Strategy() {
    }

    /**
     * 创建前自动设置创建时间
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
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

    public BigDecimal getGridPercent() {
        return gridPercent;
    }

    public void setGridPercent(BigDecimal gridPercent) {
        this.gridPercent = gridPercent;
    }

    public Integer getGridCountDown() {
        return gridCountDown;
    }

    public void setGridCountDown(Integer gridCountDown) {
        this.gridCountDown = gridCountDown;
    }

    public Integer getGridCountUp() {
        return gridCountUp;
    }

    public void setGridCountUp(Integer gridCountUp) {
        this.gridCountUp = gridCountUp;
    }

    public BigDecimal getAmountPerGrid() {
        return amountPerGrid;
    }

    public void setAmountPerGrid(BigDecimal amountPerGrid) {
        this.amountPerGrid = amountPerGrid;
    }

    public BigDecimal getMaxCapital() {
        return maxCapital;
    }

    public void setMaxCapital(BigDecimal maxCapital) {
        this.maxCapital = maxCapital;
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

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
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

    public List<GridLine> getGridLines() {
        return gridLines;
    }

    public void setGridLines(List<GridLine> gridLines) {
        this.gridLines = gridLines;
    }

    public List<TradeRecord> getTradeRecords() {
        return tradeRecords;
    }

    public void setTradeRecords(List<TradeRecord> tradeRecords) {
        this.tradeRecords = tradeRecords;
    }
}
