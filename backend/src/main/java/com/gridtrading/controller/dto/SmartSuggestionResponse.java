package com.gridtrading.controller.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能建议响应
 */
public class SmartSuggestionResponse {

    /**
     * 建议类型
     */
    private SuggestionType suggestionType;

    /**
     * 具体建议列表
     */
    private List<GridSuggestion> suggestions = new ArrayList<>();

    /**
     * 警告信息列表
     */
    private List<WarningMessage> warnings = new ArrayList<>();

    /**
     * 市场状态信息
     */
    private MarketStatus marketStatus;

    /**
     * 简要说明
     */
    private String message;

    // Constructors
    public SmartSuggestionResponse() {
    }

    // Getters and Setters
    public SuggestionType getSuggestionType() {
        return suggestionType;
    }

    public void setSuggestionType(SuggestionType suggestionType) {
        this.suggestionType = suggestionType;
    }

    public List<GridSuggestion> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<GridSuggestion> suggestions) {
        this.suggestions = suggestions;
    }

    public List<WarningMessage> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<WarningMessage> warnings) {
        this.warnings = warnings;
    }

    public MarketStatus getMarketStatus() {
        return marketStatus;
    }

    public void setMarketStatus(MarketStatus marketStatus) {
        this.marketStatus = marketStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    // 内部类：警告信息
    public static class WarningMessage {
        private String type;
        private String message;

        public WarningMessage() {
        }

        public WarningMessage(String type, String message) {
            this.type = type;
            this.message = message;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    // 内部类：市场状态
    public static class MarketStatus {
        private BigDecimal lastPrice;
        private BigDecimal positionRatio;
        private Integer boughtGridCount;
        private Integer totalGridCount;
        private Integer recentBuyCount;

        public MarketStatus() {
        }

        public BigDecimal getLastPrice() {
            return lastPrice;
        }

        public void setLastPrice(BigDecimal lastPrice) {
            this.lastPrice = lastPrice;
        }

        public BigDecimal getPositionRatio() {
            return positionRatio;
        }

        public void setPositionRatio(BigDecimal positionRatio) {
            this.positionRatio = positionRatio;
        }

        public Integer getBoughtGridCount() {
            return boughtGridCount;
        }

        public void setBoughtGridCount(Integer boughtGridCount) {
            this.boughtGridCount = boughtGridCount;
        }

        public Integer getTotalGridCount() {
            return totalGridCount;
        }

        public void setTotalGridCount(Integer totalGridCount) {
            this.totalGridCount = totalGridCount;
        }

        public Integer getRecentBuyCount() {
            return recentBuyCount;
        }

        public void setRecentBuyCount(Integer recentBuyCount) {
            this.recentBuyCount = recentBuyCount;
        }
    }
}

