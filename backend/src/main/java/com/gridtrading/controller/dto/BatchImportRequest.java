package com.gridtrading.controller.dto;

import java.util.List;

/**
 * 批量导入请求
 */
public class BatchImportRequest {

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

