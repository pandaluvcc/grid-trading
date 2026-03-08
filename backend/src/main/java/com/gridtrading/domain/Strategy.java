package com.gridtrading.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 网格交易策略实体
 */
@Entity
@Table(name = "strategy")
@Data
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
     * 每格买入数量（创建策略时设置，用于建议操作）
     */
    @Column(name = "quantity_per_grid", precision = 20, scale = 2)
    private BigDecimal quantityPerGrid;

    /**
     * 网格模型版本（固定 v2.0）
     */
    @Column(name = "grid_model_version", length = 20)
    private String gridModelVersion;

    /**
     * 网格分布摘要（如：小网13/中网4/大网2）
     */
    @Column(name = "grid_summary", length = 100)
    private String gridSummary;

    /**
     * 网格计算模式（PRICE_LOCK=价格锁定, INDEPENDENT=独立计算）
     */
    @Column(name = "grid_calculation_mode", length = 20)
    private String gridCalculationMode = "INDEPENDENT";

    /**
     * 小网价差
     */
    @Column(name = "small_gap", precision = 20, scale = 8)
    private BigDecimal smallGap;

    /**
     * 中网价差
     */
    @Column(name = "medium_gap", precision = 20, scale = 8)
    private BigDecimal mediumGap;

    /**
     * 大网价差
     */
    @Column(name = "large_gap", precision = 20, scale = 8)
    private BigDecimal largeGap;

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
     * 最大持仓比例（默认80%）
     */
    @Column(name = "max_position_ratio", precision = 5, scale = 4)
    private BigDecimal maxPositionRatio = new BigDecimal("0.8000");

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
     * 成本价（持仓净投入 / 持仓数量）
     */
    @Column(name = "cost_price", precision = 20, scale = 8)
    private BigDecimal costPrice = BigDecimal.ZERO;

    /**
     * 买入均价（买入总金额 / 买入总数量）
     */
    @Column(name = "avg_buy_price", precision = 20, scale = 8)
    private BigDecimal avgBuyPrice = BigDecimal.ZERO;

    /**
     * 累计税费
     */
    @Column(name = "total_fee", precision = 20, scale = 2)
    private BigDecimal totalFee = BigDecimal.ZERO;

    /**
     * 持股天数
     */
    @Column(name = "holding_days")
    private Integer holdingDays = 0;

    /**
     * 首次买入时间
     */
    @Column(name = "first_buy_time")
    private LocalDateTime firstBuyTime;

    /**
     * 持仓盈亏
     */
    @Column(name = "position_profit", precision = 20, scale = 2)
    private BigDecimal positionProfit = BigDecimal.ZERO;

    /**
     * 持仓盈亏百分比
     */
    @Column(name = "position_profit_percent", precision = 10, scale = 4)
    private BigDecimal positionProfitPercent = BigDecimal.ZERO;

    /**
     * 个股仓位比例
     */
    @Column(name = "position_ratio", precision = 10, scale = 4)
    private BigDecimal positionRatio = BigDecimal.ZERO;

    /**
     * 累计买入金额
     */
    @Column(name = "total_buy_amount", precision = 20, scale = 2)
    private BigDecimal totalBuyAmount = BigDecimal.ZERO;

    /**
     * 累计买入数量
     */
    @Column(name = "total_buy_quantity", precision = 20, scale = 8)
    private BigDecimal totalBuyQuantity = BigDecimal.ZERO;

    /**
     * 累计卖出金额
     */
    @Column(name = "total_sell_amount", precision = 20, scale = 2)
    private BigDecimal totalSellAmount = BigDecimal.ZERO;

    /**
     * 累计卖出数量
     */
    @Column(name = "total_sell_quantity", precision = 20, scale = 8)
    private BigDecimal totalSellQuantity = BigDecimal.ZERO;

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
}
