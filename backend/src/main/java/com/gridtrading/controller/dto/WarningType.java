package com.gridtrading.controller.dto;

/**
 * 警告类型枚举
 */
public enum WarningType {
    /**
     * 短期密集买入
     */
    DENSE_BUY,

    /**
     * 持仓比例过高
     */
    HIGH_POSITION,

    /**
     * 持仓达到上限
     */
    POSITION_LIMIT,

    /**
     * 最新价格未设置
     */
    PRICE_NOT_SET
}

