package com.gridtrading.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易记录实体
 */
@Entity
@Table(name = "trade_record")
@Data
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
    @Column(nullable = false, precision = 20, scale = 3)
    private BigDecimal price;

    /**
     * 成交金额
     */
    @Column(nullable = false, precision = 20, scale = 3)
    private BigDecimal amount;

    /**
     * 成交数量
     */
    @Column(nullable = false, precision = 20, scale = 3)
    private BigDecimal quantity;

    /**
     * 成交时间
     */
    @Column(nullable = false)
    private LocalDateTime tradeTime;

    /**
     * 手续费（用户手动录入）
     */
    @Column(precision = 20, scale = 3)
    private BigDecimal fee;

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

    /**
     * 获取网格层级（用于 JSON 序列化）
     */
    public Integer getGridLevel() {
        return gridLine != null ? gridLine.getLevel() : null;
    }

    /**
     * 获取网格线ID（用于前端匹配）
     */
    public Long getGridLineId() {
        return gridLine != null ? gridLine.getId() : null;
    }
}
