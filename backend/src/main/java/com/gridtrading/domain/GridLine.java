package com.gridtrading.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * 网格线实体
 */
@Entity
@Table(name = "grid_line")
public class GridLine {

    /**
     * 主键 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属策略（多对一关联）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id", nullable = false)
    @JsonIgnoreProperties({"gridLines", "tradeRecords"})
    private Strategy strategy;

    /**
     * 买入价格（触发价格）
     */
    @Column(name = "buy_trigger_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal buyTriggerPrice;

    /**
     * 买入价格（实际成交价，兼容字段）
     */
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal buyPrice;

    /**
     * 卖出价格（触发价格）
     */
    @Column(name = "sell_trigger_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal sellTriggerPrice;

    /**
     * 卖出价格（兼容字段）
     */
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal sellPrice;

    /**
     * 实际买入价（用户可编辑，可为空表示未成交）
     */
    @Column(name = "actual_buy_price", precision = 20, scale = 8)
    private BigDecimal actualBuyPrice;

    /**
     * 实际卖出价（实际成交价，可为空表示未卖出）
     */
    @Column(name = "actual_sell_price", precision = 20, scale = 8)
    private BigDecimal actualSellPrice;

    /**
     * 网格类型（小网/中网/大网）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "grid_type", nullable = false, length = 20)
    private GridType gridType;

    /**
     * 网格线状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GridLineState state;

    /**
     * 网格层级（0 为基准线，正数向上，负数向下）
     */
    @Column(nullable = false)
    private Integer level;

    /**
     * 买入金额
     */
    @Column(name = "buy_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal buyAmount;

    /**
     * 买入数量
     */
    @Column(name = "buy_quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal buyQuantity;

    /**
     * 卖出金额
     */
    @Column(name = "sell_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal sellAmount;

    /**
     * 毛利润
     */
    @Column(name = "profit", nullable = false, precision = 20, scale = 2)
    private BigDecimal profit;

    /**
     * 利润率
     */
    @Column(name = "profit_rate", nullable = false, precision = 10, scale = 6)
    private BigDecimal profitRate;

    /**
     * 买入次数统计（支持多轮交易）
     */
    @Column(name = "buy_count", nullable = false)
    private Integer buyCount = 0;

    /**
     * 卖出次数统计（支持多轮交易）
     */
    @Column(name = "sell_count", nullable = false)
    private Integer sellCount = 0;

    /**
     * 真实累计收益（从实际交易记录计算，扣除手续费）
     */
    @Column(name = "actual_profit", precision = 20, scale = 2)
    private BigDecimal actualProfit = BigDecimal.ZERO;

    /**
     * JPA 要求的无参构造器
     */
    public GridLine() {
    }

    // ==================== Getter 和 Setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    public BigDecimal getBuyPrice() {
        return buyPrice;
    }

    public void setBuyPrice(BigDecimal buyPrice) {
        this.buyPrice = buyPrice;
    }

    public BigDecimal getSellPrice() {
        return sellPrice;
    }

    public void setSellPrice(BigDecimal sellPrice) {
        this.sellPrice = sellPrice;
    }

    public GridLineState getState() {
        return state;
    }

    public void setState(GridLineState state) {
        this.state = state;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public BigDecimal getBuyTriggerPrice() {
        return buyTriggerPrice;
    }

    public void setBuyTriggerPrice(BigDecimal buyTriggerPrice) {
        this.buyTriggerPrice = buyTriggerPrice;
    }

    public BigDecimal getSellTriggerPrice() {
        return sellTriggerPrice;
    }

    public void setSellTriggerPrice(BigDecimal sellTriggerPrice) {
        this.sellTriggerPrice = sellTriggerPrice;
    }

    public GridType getGridType() {
        return gridType;
    }

    public void setGridType(GridType gridType) {
        this.gridType = gridType;
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

    public Integer getBuyCount() {
        return buyCount;
    }

    public void setBuyCount(Integer buyCount) {
        this.buyCount = buyCount;
    }

    public Integer getSellCount() {
        return sellCount;
    }

    public void setSellCount(Integer sellCount) {
        this.sellCount = sellCount;
    }

    public BigDecimal getActualProfit() {
        return actualProfit;
    }

    public void setActualProfit(BigDecimal actualProfit) {
        this.actualProfit = actualProfit;
    }
}
