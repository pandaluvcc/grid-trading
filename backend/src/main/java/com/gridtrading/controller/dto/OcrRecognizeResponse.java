package com.gridtrading.controller.dto;

import java.util.Collections;
import java.util.List;

/**
 * OCR识别响应
 */
public class OcrRecognizeResponse {

    private boolean success;
    private String message;
    private String rawText;
    private List<OcrTradeRecord> records;
    private int totalCount;
    private int matchedCount;

    public static OcrRecognizeResponse success(String rawText, List<OcrTradeRecord> records) {
        OcrRecognizeResponse response = new OcrRecognizeResponse();
        response.success = true;
        response.message = "ok";
        response.rawText = rawText;
        response.records = records;
        response.totalCount = records != null ? records.size() : 0;
        response.matchedCount = records != null
                ? (int) records.stream().filter(r -> r.getMatchStatus() == OcrMatchStatus.MATCHED).count()
                : 0;
        return response;
    }

    public static OcrRecognizeResponse error(String message) {
        OcrRecognizeResponse response = new OcrRecognizeResponse();
        response.success = false;
        response.message = message;
        response.records = Collections.emptyList();
        response.totalCount = 0;
        response.matchedCount = 0;
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public List<OcrTradeRecord> getRecords() {
        return records;
    }

    public void setRecords(List<OcrTradeRecord> records) {
        this.records = records;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public void setMatchedCount(int matchedCount) {
        this.matchedCount = matchedCount;
    }
}

