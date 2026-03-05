package com.gridtrading.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 暂缓网格信息
 */
public class DeferredGridInfo {
    private Long gridLineId;
    private Integer gridLevel;
    private String gridType;
    private String deferredReason;
    private LocalDateTime deferredAt;
    private Boolean canResume;
    private String resumeCondition;

    public DeferredGridInfo() {
    }

    public DeferredGridInfo(Long gridLineId, Integer gridLevel, String gridType,
                           String deferredReason, LocalDateTime deferredAt,
                           Boolean canResume, String resumeCondition) {
        this.gridLineId = gridLineId;
        this.gridLevel = gridLevel;
        this.gridType = gridType;
        this.deferredReason = deferredReason;
        this.deferredAt = deferredAt;
        this.canResume = canResume;
        this.resumeCondition = resumeCondition;
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

    public LocalDateTime getDeferredAt() {
        return deferredAt;
    }

    public void setDeferredAt(LocalDateTime deferredAt) {
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

