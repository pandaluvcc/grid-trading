package com.gridtrading.controller;

import com.gridtrading.service.suggestion.SuggestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/suggestions")
@CrossOrigin(origins = "*")
@Slf4j
public class SuggestionController {

    @Autowired
    private SuggestionService suggestionService;

    /**
     * 获取智能建议
     */
    @GetMapping("/{strategyId}")
    public ResponseEntity<Map<String, Object>> getSmartSuggestions(
            @PathVariable Long strategyId,
            @RequestParam BigDecimal currentPrice) {
        try {
            log.info("获取策略{}的智能建议，当前价格：{}", strategyId, currentPrice);
            Map<String, Object> suggestions = suggestionService.getSmartSuggestions(strategyId, currentPrice);
            return ResponseEntity.ok(suggestions);
        } catch (Exception e) {
            log.error("获取智能建议失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}




