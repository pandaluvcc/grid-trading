package com.gridtrading.controller;

import com.gridtrading.controller.dto.BatchImportRequest;
import com.gridtrading.controller.dto.OcrRecognizeResponse;
import com.gridtrading.controller.dto.OcrRematchRequest;
import com.gridtrading.service.ocr.ImportService;
import com.gridtrading.service.ocr.OcrService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OCR导入接口
 */
@RestController
@RequestMapping("/api/ocr")
@CrossOrigin(origins = "*")
public class OcrController {

    private final OcrService ocrService;
    private final ImportService importService;

    public OcrController(OcrService ocrService, ImportService importService) {
        this.ocrService = ocrService;
        this.importService = importService;
    }

    /**
     * 上传截图并识别
     */
    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OcrRecognizeResponse recognize(
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("strategyId") Long strategyId,
            @RequestParam(value = "brokerType", defaultValue = "EASTMONEY") String brokerType
    ) {
        List<MultipartFile> requestFiles = new ArrayList<>();
        if (files != null && !files.isEmpty()) {
            requestFiles.addAll(files);
        }
        if (file != null) {
            requestFiles.add(file);
        }
        return ocrService.recognizeAndParse(requestFiles, strategyId, brokerType);
    }

    /**
     * 批量导入确认后的记录
     */
    @PostMapping("/import")
    public Map<String, Object> batchImport(@RequestBody BatchImportRequest request) {
        return importService.batchImport(request);
    }

    /**
     * OCR重新匹配（支持人工标记建仓/清仓）
     */
    @PostMapping("/rematch")
    public OcrRecognizeResponse rematch(@RequestBody OcrRematchRequest request) {
        if (request == null) {
            return OcrRecognizeResponse.error("request is required");
        }
        return ocrService.rematch(request.getRecords(), request.getStrategyId());
    }
}
