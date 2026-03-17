package com.gridtrading.controller;

import com.gridtrading.controller.dto.*;
import com.gridtrading.domain.GridLine;
import com.gridtrading.domain.GridLineState;
import com.gridtrading.domain.TradeType;
import com.gridtrading.engine.GridEngine;
import com.gridtrading.repository.GridLineRepository;
import com.gridtrading.repository.StrategyRepository;
import com.gridtrading.service.StrategyService;
import com.gridtrading.service.suggestion.SuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 策略管理 Controller
 * <p>
 * 仅负责 HTTP 请求/响应，业务逻辑委托给 Service 层
 */
@RestController
@RequestMapping("/api/strategies")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyService strategyService;
    private final StrategyRepository strategyRepository;
    private final GridLineRepository gridLineRepository;
    private final GridEngine gridEngine;
    private final SuggestionService suggestionService;

    // ==================== 查询接口 ====================

    /**
     * 获取所有策略列表
     */
    @GetMapping
    public List<StrategyResponse> getAllStrategies() {
        return strategyService.getAllStrategies();
    }

    /**
     * 根据 ID 获取策略详情
     */
    @GetMapping("/{id}")
    public StrategyResponse getStrategy(@PathVariable Long id) {
        return strategyService.getStrategyById(id);
    }

    /**
     * 获取策略完整详情
     */
    @GetMapping("/{id}/detail")
    public StrategyDetailDTO getStrategyDetail(@PathVariable Long id) {
        return strategyService.getStrategyDetail(id);
    }

    /**
     * 获取网格计划表
     */
    @GetMapping("/{id}/grid-plans")
    public GridPlanResponse getGridPlans(@PathVariable Long id) {
        return strategyService.getGridPlans(id);
    }

    /**
     * 根据价格推荐网格和交易类型
     */
    @GetMapping("/{id}/suggest")
    public GridSuggestionDTO suggestGridByPrice(
            @PathVariable Long id,
            @RequestParam BigDecimal price) {
        return strategyService.suggestGridByPrice(id, price);
    }

    // ==================== 创建接口 ====================

    /**
     * 创建新策略
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StrategyResponse createStrategy(@RequestBody CreateStrategyRequest request) {
        return strategyService.createStrategy(request);
    }

    // ==================== 删除接口 ====================

    /**
     * 删除策略
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStrategy(@PathVariable Long id) {
        strategyService.deleteStrategy(id);
    }

    // ==================== 更新接口 ====================

    /**
     * 执行交易录入
     */
    @PostMapping("/{id}/tick")
    public TickResponse executeTick(@PathVariable Long id, @RequestBody TickRequest request) {
        return strategyService.executeTick(id, request);
    }

    /**
     * 更新最新价格
     */
    @PutMapping("/{id}/last-price")
    public StrategyDetailDTO updateLastPrice(
            @PathVariable Long id,
            @RequestBody UpdateLastPriceRequest request) {
        return strategyService.updateLastPrice(id, request.getLastPrice());
    }

    /**
     * 更新网格计划买入价
     */
    @PutMapping("/grid-lines/{gridLineId}/update-plan-buy-price")
    public void updatePlanBuyPrice(
            @PathVariable Long gridLineId,
            @RequestParam BigDecimal newBuyPrice) {
        strategyService.updatePlanBuyPrice(gridLineId, newBuyPrice);
    }

    /**
     * 更新网格实际买入价
     */
    @PutMapping("/grid-lines/actual-buy-price")
    public void updateActualBuyPrice(@RequestBody UpdateActualBuyPriceRequest request) {
        strategyService.updateActualBuyPrice(request.getGridLineId(), request.getActualBuyPrice());
    }

    // ==================== 智能建议接口 ====================

    /**
     * 获取智能建议
     */
    @GetMapping("/{id}/suggestion")
    public Map<String, Object> getSuggestion(
            @PathVariable Long id,
            @RequestParam(required = false) BigDecimal currentPrice) {

        var strategy = strategyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("策略不存在"));

        BigDecimal price = currentPrice != null ? currentPrice : strategy.getLastPrice();

        if (price == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "请先设置当前价格");
            return error;
        }

        return suggestionService.getSmartSuggestions(id, price);
    }

    /**
     * 获取所有暂缓网格
     */
    @GetMapping("/{id}/deferred-grids")
    public List<SuggestionResponse.DeferredGrid> getDeferredGrids(@PathVariable Long id) {
        strategyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("策略不存在"));

        List<GridLine> deferredGrids = gridLineRepository.findByStrategyIdAndDeferredTrue(id);

        return deferredGrids.stream().map(grid -> {
            SuggestionResponse.DeferredGrid deferredGrid = new SuggestionResponse.DeferredGrid();
            deferredGrid.setGridLineId(grid.getId());
            deferredGrid.setGridLevel(grid.getLevel());
            deferredGrid.setGridType(grid.getGridType().name());
            deferredGrid.setDeferredReason(grid.getDeferredReason());
            deferredGrid.setDeferredAt(grid.getDeferredAt().toString());
            deferredGrid.setCanResume(true);
            deferredGrid.setResumeCondition("手动恢复操作");
            return deferredGrid;
        }).collect(Collectors.toList());
    }

    /**
     * 手动补买暂缓网格
     */
    @PostMapping("/{id}/grids/{gridId}/resume-buy")
    public Map<String, Object> resumeBuy(
            @PathVariable Long id,
            @PathVariable Long gridId,
            @RequestBody TickRequest request) {

        strategyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("策略不存在"));

        GridLine gridLine = gridLineRepository.findById(gridId)
                .orElseThrow(() -> new RuntimeException("网格不存在"));

        if (!gridLine.getStrategy().getId().equals(id)) {
            throw new RuntimeException("网格不属于该策略");
        }

        if (!gridLine.getDeferred()) {
            throw new RuntimeException("网格未处于暂缓状态");
        }

        // 执行买入操作
        gridEngine.processManualTrade(
                id,
                gridId,
                TradeType.BUY,
                request.getPrice(),
                request.getQuantity(),
                request.getFee(),
                LocalDateTime.parse(request.getTradeTime())
        );

        // 更新网格状态为非暂缓
        gridLine.setDeferred(false);
        gridLine.setDeferredReason(null);
        gridLine.setDeferredAt(null);
        gridLineRepository.save(gridLine);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "手动补买成功");
        return result;
    }
}