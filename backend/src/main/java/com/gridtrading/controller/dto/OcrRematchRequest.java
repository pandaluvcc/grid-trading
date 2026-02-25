package com.gridtrading.controller.dto;

import java.util.List;

/**
 * OCR重新匹配请求
 */
public class OcrRematchRequest {

    private Long strategyId;
    private List<OcrTradeRecord> records;

    public Long getStrategyId() {
        return strategyId;
    }

    public void setStrategyId(Long strategyId) {
        this.strategyId = strategyId;
    }

    public List<OcrTradeRecord> getRecords() {
        return records;
    }

    public void setRecords(List<OcrTradeRecord> records) {
        this.records = records;
    }
}

