package com.gridtrading.service.suggestion;

import com.gridtrading.domain.GridLine;
import com.gridtrading.domain.Strategy;
import com.gridtrading.domain.TradeRecord;
import com.gridtrading.repository.GridLineRepository;
import com.gridtrading.repository.StrategyRepository;
import com.gridtrading.repository.TradeRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SuggestionService {

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private GridLineRepository gridLineRepository;

    @Autowired
    private TradeRecordRepository tradeRecordRepository;

    /**
     * 获取策略的智能建议
     */
    public Map<String, Object> getSmartSuggestions(Long strategyId, BigDecimal currentPrice) {
        Strategy strategy = strategyRepository.findById(strategyId)
                .orElseThrow(() -> new RuntimeException("策略不存在"));

        List<GridLine> gridLines = gridLineRepository.findByStrategyIdOrderByLevelAsc(strategyId);

        Map<String, Object> result = new HashMap<>();
        result.put("currentPrice", currentPrice);
        result.put("lastUpdateTime", LocalDateTime.now());

        // 1. 价格分析
        Map<String, Object> priceAnalysis = analyzePricePosition(strategy, gridLines, currentPrice);
        result.put("priceAnalysis", priceAnalysis);

        // 2. 建议操作
        List<Map<String, Object>> suggestions = generateSuggestions(strategy, gridLines, currentPrice);
        result.put("suggestions", suggestions);

        // 3. 风险提示
        List<Map<String, Object>> risks = analyzeRisks(strategy, gridLines, currentPrice);
        result.put("risks", risks);

        // 4. 优化建议
        List<Map<String, Object>> optimizations = generateOptimizations(strategy, gridLines);
        result.put("optimizations", optimizations);

        // 5. 暂缓网格
        List<GridLine> deferredGridLines = gridLineRepository.findByStrategyIdAndDeferredTrue(strategyId);
        List<Map<String, Object>> deferredGrids = deferredGridLines.stream().map(grid -> {
            Map<String, Object> deferredGrid = new HashMap<>();
            deferredGrid.put("gridLineId", grid.getId());
            deferredGrid.put("gridLevel", grid.getLevel());
            deferredGrid.put("gridType", grid.getGridType().name());
            deferredGrid.put("deferredReason", grid.getDeferredReason());
            deferredGrid.put("deferredAt", grid.getDeferredAt());
            return deferredGrid;
        }).collect(Collectors.toList());
        result.put("deferredGrids", deferredGrids);

        return result;
    }

    /**
     * 分析价格位置
     */
    private Map<String, Object> analyzePricePosition(Strategy strategy, List<GridLine> gridLines, BigDecimal currentPrice) {
        Map<String, Object> analysis = new HashMap<>();

        BigDecimal basePrice = strategy.getBasePrice();
        BigDecimal deviation = currentPrice.subtract(basePrice)
                .divide(basePrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        analysis.put("basePrice", basePrice);
        analysis.put("deviation", deviation);
        analysis.put("deviationPercent", deviation.setScale(2, RoundingMode.HALF_UP));

        // 判断价格位置
        String position;
        if (deviation.compareTo(BigDecimal.valueOf(10)) > 0) {
            position = "HIGH";
        } else if (deviation.compareTo(BigDecimal.valueOf(-10)) < 0) {
            position = "LOW";
        } else {
            position = "NORMAL";
        }
        analysis.put("position", position);

        // 找到最近的网格
        GridLine nearestGrid = findNearestGrid(gridLines, currentPrice);
        if (nearestGrid != null) {
            analysis.put("nearestGridLevel", nearestGrid.getLevel());
            analysis.put("nearestGridType", nearestGrid.getGridType());
            analysis.put("nearestGridBuyPrice", nearestGrid.getBuyPrice());
            analysis.put("nearestGridSellPrice", nearestGrid.getSellPrice());
        }

        return analysis;
    }

    /**
     * 生成操作建议
     */
    private List<Map<String, Object>> generateSuggestions(Strategy strategy, List<GridLine> gridLines, BigDecimal currentPrice) {
        List<Map<String, Object>> suggestions = new ArrayList<>();

        // 1. 检查待买入触发
        List<GridLine> buyTriggered = gridLines.stream()
                .filter(g -> "WAIT_BUY".equals(g.getState().name()))
                .filter(g -> currentPrice.compareTo(g.getBuyTriggerPrice()) <= 0)
                .sorted(Comparator.comparing(GridLine::getLevel))
                .collect(Collectors.toList());

        if (!buyTriggered.isEmpty()) {
            for (GridLine grid : buyTriggered) {
                Map<String, Object> suggestion = new HashMap<>();
                suggestion.put("gridLineId", grid.getId());
                suggestion.put("type", "BUY");
                suggestion.put("priority", calculatePriority(grid, currentPrice));
                suggestion.put("gridLevel", grid.getLevel());
                suggestion.put("gridType", grid.getGridType().name());
                suggestion.put("price", grid.getBuyPrice());
                suggestion.put("triggerPrice", grid.getBuyTriggerPrice());
                suggestion.put("quantity", grid.getBuyQuantity());
                suggestion.put("amount", grid.getBuyAmount());
                suggestion.put("quantityRatio", calculateQuantityRatio(grid, strategy));
                suggestion.put("reason", generateBuyReason(grid, currentPrice));
                suggestions.add(suggestion);
            }
        }

        // 2. 检查待卖出触发
        List<GridLine> sellTriggered = gridLines.stream()
                .filter(g -> "BOUGHT".equals(g.getState().name()))
                .filter(g -> currentPrice.compareTo(g.getSellTriggerPrice()) >= 0)
                .sorted(Comparator.comparing(GridLine::getLevel).reversed())
                .collect(Collectors.toList());

        if (!sellTriggered.isEmpty()) {
            for (GridLine grid : sellTriggered) {
                Map<String, Object> suggestion = new HashMap<>();
                suggestion.put("gridLineId", grid.getId());
                suggestion.put("type", "SELL");
                suggestion.put("priority", calculatePriority(grid, currentPrice));
                suggestion.put("gridLevel", grid.getLevel());
                suggestion.put("gridType", grid.getGridType().name());
                suggestion.put("price", grid.getSellPrice());
                suggestion.put("triggerPrice", grid.getSellTriggerPrice());
                suggestion.put("quantity", grid.getBuyQuantity());
                suggestion.put("amount", grid.getSellAmount());
                suggestion.put("quantityRatio", calculateQuantityRatio(grid, strategy));
                suggestion.put("expectedProfit", grid.getProfit());
                suggestion.put("reason", generateSellReason(grid, currentPrice));
                suggestions.add(suggestion);
            }
        }

        // 3. 按优先级排序
        suggestions.sort((a, b) -> ((String)b.get("priority")).compareTo((String)a.get("priority")));

        return suggestions;
    }

    /**
     * 分析风险
     */
    private List<Map<String, Object>> analyzeRisks(Strategy strategy, List<GridLine> gridLines, BigDecimal currentPrice) {
        List<Map<String, Object>> risks = new ArrayList<>();

        // 1. 检查资金不足风险
        long waitBuyCount = gridLines.stream()
                .filter(g -> "WAIT_BUY".equals(g.getState().name()))
                .count();

        if (waitBuyCount > 0) {
            BigDecimal requiredAmount = gridLines.stream()
                    .filter(g -> "WAIT_BUY".equals(g.getState().name()))
                    .map(GridLine::getBuyAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (strategy.getAvailableCash().compareTo(requiredAmount) < 0) {
                Map<String, Object> risk = new HashMap<>();
                risk.put("type", "INSUFFICIENT_FUNDS");
                risk.put("level", "HIGH");
                risk.put("message", "可用资金不足，无法买入所有待买入网格");
                risk.put("requiredAmount", requiredAmount);
                risk.put("availableAmount", strategy.getAvailableCash());
                risks.add(risk);
            }
        }

        // 2. 检查持仓过重风险
        long boughtCount = gridLines.stream()
                .filter(g -> "BOUGHT".equals(g.getState().name()))
                .count();

        BigDecimal investedRatio = strategy.getInvestedAmount()
                .divide(strategy.getMaxCapital(), 4, RoundingMode.HALF_UP);

        if (investedRatio.compareTo(BigDecimal.valueOf(0.8)) > 0) {
            Map<String, Object> risk = new HashMap<>();
            risk.put("type", "HEAVY_POSITION");
            risk.put("level", "MEDIUM");
            risk.put("message", "持仓较重，建议关注卖出机会");
            risk.put("investedRatio", investedRatio.multiply(BigDecimal.valueOf(100)));
            risk.put("boughtGridCount", boughtCount);
            risks.add(risk);
        }

        // 3. 检查连续下跌风险
        BigDecimal deviation = currentPrice.subtract(strategy.getBasePrice())
                .divide(strategy.getBasePrice(), 4, RoundingMode.HALF_UP);

        if (deviation.compareTo(BigDecimal.valueOf(-0.15)) < 0) {
            Map<String, Object> risk = new HashMap<>();
            risk.put("type", "CONTINUOUS_DECLINE");
            risk.put("level", "HIGH");
            risk.put("message", "价格持续下跌，已偏离基准价超过15%");
            risk.put("deviation", deviation.multiply(BigDecimal.valueOf(100)));
            risks.add(risk);
        }

        return risks;
    }

    /**
     * 生成优化建议
     */
    private List<Map<String, Object>> generateOptimizations(Strategy strategy, List<GridLine> gridLines) {
        List<Map<String, Object>> optimizations = new ArrayList<>();

        // 1. 检查长期未成交的网格
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
        List<GridLine> boughtGrids = gridLines.stream()
                .filter(g -> "BOUGHT".equals(g.getState().name()))
                .collect(Collectors.toList());

        for (GridLine grid : boughtGrids) {
            List<TradeRecord> trades = tradeRecordRepository.findByGridLineIdOrderByTradeTimeAsc(grid.getId());
            if (!trades.isEmpty()) {
                TradeRecord lastTrade = trades.get(trades.size() - 1);
                if (lastTrade.getTradeTime().isBefore(oneWeekAgo)) {
                    Map<String, Object> optimization = new HashMap<>();
                    optimization.put("type", "LONG_TERM_HOLD");
                    optimization.put("gridLevel", grid.getLevel());
                    optimization.put("message", "网格" + grid.getLevel() + "已持有超过1周，可考虑调整卖出价");
                    optimization.put("holdDays", java.time.Duration.between(lastTrade.getTradeTime(), LocalDateTime.now()).toDays());
                    optimizations.add(optimization);
                }
            }
        }

        // 2. 检查是否有多次买入未卖出的网格
        List<GridLine> heavyGrids = boughtGrids.stream()
                .filter(g -> g.getBuyCount() > 3)
                .collect(Collectors.toList());

        if (!heavyGrids.isEmpty()) {
            for (GridLine grid : heavyGrids) {
                Map<String, Object> optimization = new HashMap<>();
                optimization.put("type", "MULTIPLE_BUY");
                optimization.put("gridLevel", grid.getLevel());
                optimization.put("message", "网格" + grid.getLevel() + "已买入" + grid.getBuyCount() + "次，建议优先卖出");
                optimization.put("buyCount", grid.getBuyCount());
                optimizations.add(optimization);
            }
        }

        // 3. 检查收益率较高的网格
        List<GridLine> highProfitGrids = boughtGrids.stream()
                .filter(g -> g.getProfitRate().compareTo(BigDecimal.valueOf(0.1)) > 0)
                .collect(Collectors.toList());

        if (!highProfitGrids.isEmpty()) {
            Map<String, Object> optimization = new HashMap<>();
            optimization.put("type", "HIGH_PROFIT");
            optimization.put("message", "有" + highProfitGrids.size() + "个网格收益率超过10%，可考虑止盈");
            optimization.put("gridCount", highProfitGrids.size());
            optimizations.add(optimization);
        }

        return optimizations;
    }

    /**
     * 找到最近的网格
     */
    private GridLine findNearestGrid(List<GridLine> gridLines, BigDecimal currentPrice) {
        GridLine nearest = null;
        BigDecimal minDistance = null;

        for (GridLine grid : gridLines) {
            BigDecimal distance = currentPrice.subtract(grid.getBuyPrice()).abs();
            if (minDistance == null || distance.compareTo(minDistance) < 0) {
                minDistance = distance;
                nearest = grid;
            }
        }

        return nearest;
    }

    /**
     * 计算优先级
     */
    private String calculatePriority(GridLine grid, BigDecimal currentPrice) {
        // 根据网格类型和价格偏离度计算优先级
        BigDecimal targetPrice = "BOUGHT".equals(grid.getState().name())
                ? grid.getSellTriggerPrice()
                : grid.getBuyTriggerPrice();

        BigDecimal deviation = currentPrice.subtract(targetPrice).abs()
                .divide(targetPrice, 4, RoundingMode.HALF_UP);

        if (deviation.compareTo(BigDecimal.valueOf(0.01)) < 0) {
            return "HIGH";
        } else if (deviation.compareTo(BigDecimal.valueOf(0.03)) < 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * 计算买入数量占持仓比例
     */
    private BigDecimal calculateQuantityRatio(GridLine grid, Strategy strategy) {
        if (strategy.getInvestedAmount() == null || strategy.getInvestedAmount().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        return grid.getBuyAmount().divide(strategy.getInvestedAmount(), 4, RoundingMode.HALF_UP);
    }

    /**
     * 生成买入原因
     */
    private String generateBuyReason(GridLine grid, BigDecimal currentPrice) {
        StringBuilder reason = new StringBuilder();
        reason.append("当前价格").append(currentPrice)
              .append("已触及网格").append(grid.getLevel())
              .append("买入触发价").append(grid.getBuyTriggerPrice());

        if ("MEDIUM".equals(grid.getGridType().name())) {
            reason.append("，这是中网格，建议买入");
        } else if ("LARGE".equals(grid.getGridType().name())) {
            reason.append("，这是大网格，预期收益较高，建议买入");
        } else {
            reason.append("，建议买入");
        }

        return reason.toString();
    }

    /**
     * 生成卖出原因
     */
    private String generateSellReason(GridLine grid, BigDecimal currentPrice) {
        StringBuilder reason = new StringBuilder();
        reason.append("当前价格").append(currentPrice)
              .append("已触及网格").append(grid.getLevel())
              .append("卖出触发价").append(grid.getSellTriggerPrice());

        if (grid.getBuyCount() > 1) {
            reason.append("，该网格已买入").append(grid.getBuyCount()).append("次");
        }

        reason.append("，预期收益").append(grid.getProfit()).append("元");

        return reason.toString();
    }
}




