package com.gridtrading.controller;

import com.gridtrading.controller.dto.ApkFolderResultDTO;
import com.gridtrading.service.tools.ApkFolderGeneratorService;
import com.gridtrading.service.tools.KnowledgeBaseExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/tools")
@Slf4j
public class ToolsController {

    @Autowired
    private ApkFolderGeneratorService apkFolderGeneratorService;

    @Autowired
    private KnowledgeBaseExportService knowledgeBaseExportService;

    @PostMapping("/apk-folder-generator")
    public ResponseEntity<ApkFolderResultDTO> generateApkFolders(
            @RequestParam("file") MultipartFile file,
            @RequestParam("targetPath") String targetPath) throws IOException {

        log.info("开始处理APK文件夹生成: targetPath={}", targetPath);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Excel文件不能为空");
        }

        ApkFolderResultDTO result = apkFolderGeneratorService.generate(file, targetPath);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/knowledge-base-export")
    public ResponseEntity<byte[]> exportKnowledgeBase(
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("开始处理知识库文件导出");

        if (file.isEmpty()) {
            throw new IllegalArgumentException("CSV文件不能为空");
        }

        byte[] result = knowledgeBaseExportService.exportKnowledgeBase(file);

        String filename = "知识库_" + System.currentTimeMillis() + ".xlsx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(result);
    }
}
