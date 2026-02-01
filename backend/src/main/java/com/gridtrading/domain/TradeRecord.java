package com.gridtrading.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易记录实体
 */
@Entity
@Table(name = "trade_record")
public class TradeRecord {

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
    @JsonIgnore
    private Strategy strategy;

    /**
     * 关联的网格线（多对一关联）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grid_line_id", nullable = false)
    @JsonIgnore
    private GridLine gridLine;

    /**
     * 交易类型（买入/卖出）
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradeType type;

    /**
     * 成交价格
     */
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal price;

    /**
     * 成交金额
     */
    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    /**
     * 成交数量
     */
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    /**
     * 成交时间
     */
    @Column(nullable = false)
    private LocalDateTime tradeTime;

    /**
     * JPA 要求的无参构造器
     */
    public TradeRecord() {
    }

    /**
     * 创建前自动设置交易时间
     */
    @PrePersist
    protected void onCreate() {
        if (tradeTime == null) {
            tradeTime = LocalDateTime.now();
        }
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

    public GridLine getGridLine() {
        return gridLine;
    }

    public void setGridLine(GridLine gridLine) {
        this.gridLine = gridLine;
    }

    public TradeType getType() {
        return type;
    }

    public void setType(TradeType type) {
        this.type = type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public LocalDateTime getTradeTime() {
        return tradeTime;
    }

    public void setTradeTime(LocalDateTime tradeTime) {
        this.tradeTime = tradeTime;
    }

    /**
     * 获取网格层级（用于 JSON 序列化）
     */
    public Integer getGridLevel() {
        return gridLine != null ? gridLine.getLevel() : null;
    }
}
