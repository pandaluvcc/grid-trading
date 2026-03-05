package com.gridtrading.controller.dto;

/**
 * 建议类型枚举
 */
public enum SuggestionType {
    /**
     * 建议买入
     */
    BUY,

    /**
     * 建议卖出
     */
    SELL,

    /**
     * 建议补买暂缓网格
     */
    RESUME_BUY,

    /**
     * 建议持仓观望
     */
    HOLD,

    /**
     * 未设置最新价格
     */
    NO_PRICE
}

