package com.gridtrading.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ApkFolderResultDTO {
    private Integer totalKeywords;
    private Integer filteredKeywords;
    private Integer createdFolders;
    private Integer skippedFolders;
    private Integer copiedApkCount;
    private List<KeywordInfo> keywords;

    @Data
    public static class KeywordInfo {
        private String keyword;
        private Integer monthlySearchVolume;
        private String feature;
        private BigDecimal recommendedPrice;
        private String competitionLevel;
    }
}
