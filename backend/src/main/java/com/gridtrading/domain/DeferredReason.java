package com.gridtrading.domain;

/**
 * 暂缓原因枚举
 */
public enum DeferredReason {
    /**
     * 短期密集买入导致暂缓
     */
    DENSE_BUY("短期密集买入"),

    /**
     * 持仓比例达到上限导致暂缓
     */
    POSITION_LIMIT("持仓比例上限");

    private final String description;

    DeferredReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

