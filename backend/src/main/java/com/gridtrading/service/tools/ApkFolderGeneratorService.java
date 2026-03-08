package com.gridtrading.service.tools;

import com.gridtrading.controller.dto.ApkFolderResultDTO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApkFolderGeneratorService {

    private static final int MIN_SEARCH_VOLUME = 200;

    public ApkFolderResultDTO generate(MultipartFile file, String targetPath) throws IOException {
        List<KeywordData> allKeywords = readExcel(file);
        int totalCount = allKeywords.size();

        List<KeywordData> filteredKeywords = allKeywords.stream()
                .filter(k -> k.getMonthlySearchVolume() != null && k.getMonthlySearchVolume() >= MIN_SEARCH_VOLUME)
                .sorted(Comparator.comparing(KeywordData::getMonthlySearchVolume).reversed())
                .collect(Collectors.toList());

        int filteredCount = filteredKeywords.size();

        File targetDir = new File(targetPath);
        if (!targetDir.exists() || !targetDir.isDirectory()) {
            throw new IllegalArgumentException("目标路径不存在或不是目录: " + targetPath);
        }

        File[] sourceFiles = targetDir.listFiles((dir, name) -> {
            String lowerName = name.toLowerCase();
            return lowerName.endsWith(".apk") || lowerName.endsWith(".txt");
        });
        if (sourceFiles == null || sourceFiles.length == 0) {
            throw new IllegalArgumentException("目标路径下没有找到APK或TXT文件: " + targetPath);
        }

        int createdCount = 0;
        int skippedCount = 0;
        for (KeywordData data : filteredKeywords) {
            String safeFolderName = sanitizeFolderName(data.getKeyword());
            File folder = new File(targetPath, safeFolderName);
            if (folder.exists()) {
                skippedCount++;
                continue;
            }

            folder.mkdirs();
            createdCount++;

            for (File sourceFile : sourceFiles) {
                Path destPath = new File(folder, sourceFile.getName()).toPath();
                Files.copy(sourceFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        ApkFolderResultDTO result = new ApkFolderResultDTO();
        result.setTotalKeywords(totalCount);
        result.setFilteredKeywords(filteredCount);
        result.setCreatedFolders(createdCount);
        result.setSkippedFolders(skippedCount);
        result.setCopiedApkCount(sourceFiles.length);

        List<ApkFolderResultDTO.KeywordInfo> keywordInfos = filteredKeywords.stream()
                .limit(20)
                .map(k -> {
                    ApkFolderResultDTO.KeywordInfo info = new ApkFolderResultDTO.KeywordInfo();
                    info.setKeyword(k.getKeyword());
                    info.setMonthlySearchVolume(k.getMonthlySearchVolume());
                    info.setFeature(k.getFeature());
                    info.setRecommendedPrice(k.getRecommendedPrice());
                    info.setCompetitionLevel(k.getCompetitionLevel());
                    return info;
                })
                .collect(Collectors.toList());
        result.setKeywords(keywordInfos);

        log.info("APK文件夹生成完成: 总关键词={}, 过滤后={}, 创建文件夹={}, 跳过={}, 源文件={}", 
                totalCount, filteredCount, createdCount, skippedCount, sourceFiles.length);

        return result;
    }

    private List<KeywordData> readExcel(MultipartFile file) throws IOException {
        List<KeywordData> dataList = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || row.getCell(0) == null) {
                    continue;
                }

                String keyword = getCellValue(row.getCell(0));
                if (keyword == null || keyword.trim().isEmpty()) {
                    continue;
                }

                KeywordData data = new KeywordData();
                data.setKeyword(keyword.trim());
                data.setFeature(getCellValue(row.getCell(1)));
                data.setRecommendedPrice(parseBigDecimal(getCellValue(row.getCell(2))));
                data.setMonthlySearchVolume(parseInteger(getCellValue(row.getCell(3))));
                data.setCompetitionLevel(getCellValue(row.getCell(4)));

                dataList.add(data);
            }
        }

        return dataList;
    }

    private String getCellValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return cell.toString();
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            String cleanValue = value.replaceAll("[^0-9]", "");
            return Integer.parseInt(cleanValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String sanitizeFolderName(String name) {
        if (name == null) {
            return "unknown";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_")
                   .replaceAll("\\s+", "_")
                   .trim();
    }

    @Data
    private static class KeywordData {
        private String keyword;
        private String feature;
        private BigDecimal recommendedPrice;
        private Integer monthlySearchVolume;
        private String competitionLevel;

        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
        public String getFeature() { return feature; }
        public void setFeature(String feature) { this.feature = feature; }
        public BigDecimal getRecommendedPrice() { return recommendedPrice; }
        public void setRecommendedPrice(BigDecimal recommendedPrice) { this.recommendedPrice = recommendedPrice; }
        public Integer getMonthlySearchVolume() { return monthlySearchVolume; }
        public void setMonthlySearchVolume(Integer monthlySearchVolume) { this.monthlySearchVolume = monthlySearchVolume; }
        public String getCompetitionLevel() { return competitionLevel; }
        public void setCompetitionLevel(String competitionLevel) { this.competitionLevel = competitionLevel; }
    }
}
