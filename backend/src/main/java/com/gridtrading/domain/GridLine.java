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
     * 买入价格
     */
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal buyPrice;

    /**
     * 卖出价格
     */
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal sellPrice;

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
}
