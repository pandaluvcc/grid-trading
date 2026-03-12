package com.gridtrading.controller.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能建议响应
 */
public class SuggestionResponse {

    private SuggestionType suggestionType;
    private String reason;
    private List<GridSuggestionItem> suggestions = new ArrayList<>();
    private List<Warning> warnings = new ArrayList<>();
    private List<DeferredGrid> deferredGrids = new ArrayList<>();
    private MarketStatus marketStatus;

    public SuggestionResponse() {
    }

    public SuggestionResponse(SuggestionType suggestionType, String reason) {
        this.suggestionType = suggestionType;
        this.reason = reason;
    }

    // Getters and Setters
    public SuggestionType getSuggestionType() {
        return suggestionType;
    }

    public void setSuggestionType(SuggestionType suggestionType) {
        this.suggestionType = suggestionType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<GridSuggestionItem> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<GridSuggestionItem> suggestions) {
        this.suggestions = suggestions;
    }

    public List<Warning> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<Warning> warnings) {
        this.warnings = warnings;
    }

    public List<DeferredGrid> getDeferredGrids() {
        return deferredGrids;
    }

    public void setDeferredGrids(List<DeferredGrid> deferredGrids) {
        this.deferredGrids = deferredGrids;
    }

    public MarketStatus getMarketStatus() {
        return marketStatus;
    }

    public void setMarketStatus(MarketStatus marketStatus) {
        this.marketStatus = marketStatus;
    }

    /**
     * 网格操作建议
     */
    public static class GridSuggestionItem {
        private Long gridLineId;
        private Integer gridLevel;
        private String gridType;
        private String action;
        private BigDecimal price;
        private BigDecimal quantity;
        private BigDecimal amount;
        private BigDecimal quantityRatio;
        private String reason;

        public GridSuggestionItem() {
        }

        // Getters and Setters
        public Long getGridLineId() {
            return gridLineId;
        }

        public void setGridLineId(Long gridLineId) {
            this.gridLineId = gridLineId;
        }

        public Integer getGridLevel() {
            return gridLevel;
        }

        public void setGridLevel(Integer gridLevel) {
            this.gridLevel = gridLevel;
        }

        public String getGridType() {
            return gridType;
        }

        public void setGridType(String gridType) {
            this.gridType = gridType;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public BigDecimal getQuantityRatio() {
            return quantityRatio;
        }

        public void setQuantityRatio(BigDecimal quantityRatio) {
            this.quantityRatio = quantityRatio;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    /**
     * 警告信息
     */
    public static class Warning {
        private String type;
        private String message;

        public Warning() {
        }

        public Warning(String type, String message) {
            this.type = type;
            this.message = message;
        }

        // Getters and Setters
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

    /**
     * 暂缓网格信息
     */
    public static class DeferredGrid {
        private Long gridLineId;
        private Integer gridLevel;
        private String gridType;
        private String deferredReason;
        private String deferredAt;
        private Boolean canResume;
        private String resumeCondition;

        public DeferredGrid() {
        }

        // Getters and Setters
        public Long getGridLineId() {
            return gridLineId;
        }

        public void setGridLineId(Long gridLineId) {
            this.gridLineId = gridLineId;
        }

        public Integer getGridLevel() {
            return gridLevel;
        }

        public void setGridLevel(Integer gridLevel) {
            this.gridLevel = gridLevel;
        }

        public String getGridType() {
            return gridType;
        }

        public void setGridType(String gridType) {
            this.gridType = gridType;
        }

        public String getDeferredReason() {
            return deferredReason;
        }

        public void setDeferredReason(String deferredReason) {
            this.deferredReason = deferredReason;
        }

        public String getDeferredAt() {
            return deferredAt;
        }

        public void setDeferredAt(String deferredAt) {
            this.deferredAt = deferredAt;
        }

        public Boolean getCanResume() {
            return canResume;
        }

        public void setCanResume(Boolean canResume) {
            this.canResume = canResume;
        }

        public String getResumeCondition() {
            return resumeCondition;
        }

        public void setResumeCondition(String resumeCondition) {
            this.resumeCondition = resumeCondition;
        }
    }
}

