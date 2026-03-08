package com.gridtrading.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 网格线实体
 */
@Entity
@Table(name = "grid_line")
@Data
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
     * 是否暂缓买入（预留功能）
     */
    @Column(name = "deferred")
    private Boolean deferred = false;

    /**
     * 暂缓原因（预留功能）
     */
    @Column(name = "deferred_reason", length = 100)
    private String deferredReason;

    /**
     * 暂缓时间（预留功能）
     */
    @Column(name = "deferred_at")
    private LocalDateTime deferredAt;

    /**
     * JPA 要求的无参构造器
     */
    public GridLine() {
    }
}
