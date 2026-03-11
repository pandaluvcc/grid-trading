package com.gridtrading.domain;

/**
 * 交易类型枚举
 */
public enum TradeType {
    /**
     * 建仓买入（首笔买入，特殊标识）
     */
    OPENING_BUY,

    /**
     * 买入
     */
    BUY,

    /**
     * 卖出
     */
    SELL;

    /**
     * 判断是否为买入类型（包括建仓买入）
     */
    public boolean isBuy() {
        return this == BUY || this == OPENING_BUY;
    }
}
