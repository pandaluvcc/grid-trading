package com.gridtrading.service.tools;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class KnowledgeBaseExportService {

    private static final String PREFIX = "[安卓][2026]";
    private static final String SUFFIX = "[无毒][下载][免费]";

    public byte[] exportKnowledgeBase(MultipartFile file) throws IOException {
        List<String[]> records = readCsv(file);
        log.info("读取到{}条记录", records.size());

        ClassPathResource templateResource = new ClassPathResource("templates/知识库模板.xlsx");
        Workbook workbook;

        if (templateResource.exists()) {
            log.info("找到模板文件");
            try (InputStream is = templateResource.getInputStream()) {
                workbook = new XSSFWorkbook(is);
            }
        } else {
            log.warn("模板文件不存在，创建新工作簿");
            workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet();
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("问题或相关句子");
            headerRow.createCell(1).setCellValue("段落（包含网站描述与链接）");
        }

        Sheet sheet = workbook.getSheetAt(0);

        int rowNum = 1;
        for (String[] record : records) {
            String fileName = record[0];
            String link = record[1];

            String processedFileName = PREFIX + fileName + SUFFIX;
            log.debug("处理记录: 文件名={}, 链接={}", fileName, link);

            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(processedFileName);
            row.createCell(1).setCellValue(link);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        log.info("知识库Excel文件导出完成，共写入{}条记录", records.size());
        return out.toByteArray();
    }

    private List<String[]> readCsv(MultipartFile file) throws IOException {
        List<String[]> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean isFirstLine = true;
            int lineNum = 0;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                
                if (isFirstLine) {
                    log.info("CSV表头: {}", line);
                    isFirstLine = false;
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] columns = parseCsvLine(line);
                log.debug("第{}行解析结果: 列数={}", lineNum, columns.length);
                
                if (columns.length >= 2) {
                    String fileName = columns[0].trim();
                    String link = columns[1].trim();

                    if (!fileName.isEmpty() && !link.isEmpty()) {
                        log.info("读取记录: 文件名={}, 链接={}", fileName, link);
                        records.add(new String[]{fileName, link});
                    } else {
                        log.warn("第{}行数据为空: fileName={}, link={}", lineNum, fileName, link);
                    }
                } else {
                    log.warn("第{}行列数不足: {}", lineNum, line);
                }
            }
        }

        return records;
    }

    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if ((c == '\t' || c == ',') && !inQuotes) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());

        return result.toArray(new String[0]);
    }
}
