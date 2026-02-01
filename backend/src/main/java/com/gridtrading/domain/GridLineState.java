package com.gridtrading.domain;

/**
 * 网格线状态枚举
 * 状态机：WAIT_BUY → BOUGHT → SOLD → WAIT_BUY
 */
public enum GridLineState {
    /**
     * 等待买入
     */
    WAIT_BUY,

    /**
     * 已买入（等待卖出）
     */
    BOUGHT,

    /**
     * 已卖出（临时状态，会立即转为 WAIT_BUY）
     */
    SOLD,

    /**
     * @deprecated 使用 BOUGHT 代替
     */
    @Deprecated
    WAIT_SELL
}
