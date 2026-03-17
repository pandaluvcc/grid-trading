package com.gridtrading.service;

import com.gridtrading.constants.GridConstants;
import com.gridtrading.controller.dto.*;
import com.gridtrading.domain.*;
import com.gridtrading.engine.GridEngine;
import com.gridtrading.repository.GridLineRepository;
import com.gridtrading.repository.StrategyRepository;
import com.gridtrading.repository.TradeRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 策略业务服务
 * <p>
 * 负责策略的创建、查询、更新等核心业务逻辑
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StrategyService {

    private final StrategyRepository strategyRepository;
    private final GridLineRepository gridLineRepository;
    private final TradeRecordRepository tradeRecordRepository;
    private final GridEngine gridEngine;
    private final PositionCalculator positionCalculator;

    // ==================== 查询操作 ====================

    /**
     * 获取所有策略列表
     */
    @Transactional
    public List<StrategyResponse> getAllStrategies() {
        return strategyRepository.findAll().stream()
                .map(strategy -> {
                    positionCalculator.calculateAndUpdate(strategy);
                    strategyRepository.save(strategy);
                    return StrategyResponse.fromEntity(strategy);
                })
                .collect(Collectors.toList());
    }

    /**
     * 根据 ID 获取策略详情
     */
    public StrategyResponse getStrategyById(Long id) {
        Strategy strategy = findStrategyById(id);
        return StrategyResponse.fromEntity(strategy);
    }

    /**
     * 获取策略完整详情（包含持仓计算）
     */
    @Transactional
    public StrategyDetailDTO getStrategyDetail(Long id) {
        Strategy strategy = findStrategyWithGridLines(id);

        // 重新计算持仓相关字段
        positionCalculator.calculateAndUpdate(strategy);
        strategyRepository.save(strategy);

        StrategyDetailDTO dto = StrategyDetailDTO.fromEntity(strategy);

        // 计算预计收益
        BigDecimal expectedProfit = strategy.getGridLines().stream()
                .map(gl -> gl.getProfit() != null ? gl.getProfit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setExpectedProfit(expectedProfit);

        // 计算当日参考盈亏
        calculateTodayProfit(dto, strategy);

        return dto;
    }

    /**
     * 获取网格计划表
     */
    public GridPlanResponse getGridPlans(Long strategyId) {
        Strategy strategy = findStrategyWithGridLines(strategyId);

        GridPlanResponse response = new GridPlanResponse();

        // 策略基础信息
        GridPlanResponse.StrategyInfo strategyInfo = new GridPlanResponse.StrategyInfo();
        strategyInfo.setName(strategy.getName());
        strategyInfo.setSymbol(strategy.getSymbol());
        strategyInfo.setBasePrice(strategy.getBasePrice());
        response.setStrategy(strategyInfo);

        // 网格计划列表 - 按层级升序排列（第1网在最上面）
        List<GridPlanResponse.GridPlanItem> gridPlans = strategy.getGridLines().stream()
                .map(this::convertToGridPlanItem)
                .sorted((a, b) -> a.getLevel().compareTo(b.getLevel()))
                .collect(Collectors.toList());

        response.setGridPlans(gridPlans);
        return response;
    }

    // ==================== 创建操作 ====================

    /**
     * 创建新策略
     * <p>
     * 支持两种模式：
     * - 按金额：传入 amountPerGrid
     * - 按数量：传入 quantityPerGrid
     */
    @Transactional
    public StrategyResponse createStrategy(CreateStrategyRequest request) {
        validateCreateRequest(request);

        // 计算 amountPerGrid
        BigDecimal amountPerGrid = calculateAmountPerGrid(request);
        BigDecimal quantityPerGrid = calculateQuantityPerGrid(request, amountPerGrid);

        // 创建策略实体
        Strategy strategy = buildStrategy(request, amountPerGrid, quantityPerGrid);

        // 生成网格线
        generateGridLines(strategy);

        // 保存策略
        Strategy savedStrategy = strategyRepository.save(strategy);

        return StrategyResponse.fromEntity(savedStrategy);
    }

    // ==================== 更新操作 ====================

    /**
     * 执行交易录入
     */
    @Transactional
    public TickResponse executeTick(Long strategyId, TickRequest request) {
        validateTickRequest(request);

        Strategy strategy = findStrategyById(strategyId);
        LocalDateTime tradeTime = parseTradeTime(request.getTradeTime());

        LocalDateTime beforeExecution = LocalDateTime.now();
        gridEngine.processManualTrade(
                strategyId,
                request.getGridLineId(),
                request.getType(),
                request.getPrice(),
                request.getQuantity(),
                request.getFee(),
                tradeTime
        );

        // 重新加载策略
        strategy = findStrategyById(strategyId);

        // 查询本次执行产生的交易记录
        List<TradeRecord> newTrades = tradeRecordRepository
                .findByStrategyIdAndTradeTimeAfter(strategyId, beforeExecution);

        return buildTickResponse(strategy, request.getPrice(), newTrades);
    }

    /**
     * 更新最新价格
     */
    @Transactional
    public StrategyDetailDTO updateLastPrice(Long strategyId, BigDecimal lastPrice) {
        Strategy strategy = findStrategyById(strategyId);
        positionCalculator.updateByLastPrice(strategy, lastPrice);
        Strategy saved = strategyRepository.save(strategy);
        return StrategyDetailDTO.fromEntity(saved);
    }

    /**
     * 更新网格计划买入价
     */
    @Transactional
    public void updatePlanBuyPrice(Long gridLineId, BigDecimal newBuyPrice) {
        if (newBuyPrice == null || newBuyPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("买入价必须大于 0");
        }

        GridLine gridLine = findGridLineById(gridLineId);

        if (gridLine.getState() != GridLineState.WAIT_BUY) {
            throw new IllegalArgumentException("只有等待买入状态的网格才能修改计划买入价");
        }

        Strategy strategy = findStrategyWithGridLines(gridLine.getStrategy().getId());

        // 如果修改的是第1格，更新 base_price
        if (gridLine.getLevel() == 1) {
            strategy.setBasePrice(newBuyPrice);
        }

        gridLine.setBuyPrice(newBuyPrice);
        gridEngine.recalculateSubsequentGridsAfterManualBuy(strategy, gridLine, newBuyPrice);

        strategyRepository.save(strategy);
    }

    /**
     * 更新网格实际买入价
     */
    @Transactional
    public void updateActualBuyPrice(Long gridLineId, BigDecimal actualBuyPrice) {
        if (actualBuyPrice == null || actualBuyPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("actualBuyPrice 必须大于 0");
        }

        GridLine targetGridLine = findGridLineById(gridLineId);

        if (targetGridLine.getState() != GridLineState.BOUGHT) {
            throw new IllegalArgumentException("只有已买入状态的网格才能修改实际买入价");
        }

        Strategy strategy = findStrategyWithGridLines(targetGridLine.getStrategy().getId());

        // 更新实际买入价
        targetGridLine.setActualBuyPrice(actualBuyPrice);
        targetGridLine.setBuyPrice(actualBuyPrice);

        // 重新计算预计收益
        BigDecimal expectedProfit = calculateExpectedProfit(targetGridLine, actualBuyPrice);
        targetGridLine.setExpectedProfit(expectedProfit);

        gridEngine.recalculateSubsequentGridsAfterManualBuy(strategy, targetGridLine, actualBuyPrice);

        strategyRepository.save(strategy);
    }

    // ==================== 推荐操作 ====================

    /**
     * 根据价格推荐网格和交易类型
     */
    public GridSuggestionDTO suggestGridByPrice(Long strategyId, BigDecimal price) {
        Strategy strategy = findStrategyWithGridLines(strategyId);

        GridLine bestMatch = null;
        TradeType suggestedType = null;
        BigDecimal minDiff = null;

        for (GridLine grid : strategy.getGridLines()) {
            if (grid.getState() == GridLineState.WAIT_BUY) {
                BigDecimal diff = price.subtract(grid.getBuyPrice()).abs();
                if (minDiff == null || diff.compareTo(minDiff) < 0) {
                    minDiff = diff;
                    bestMatch = grid;
                    suggestedType = TradeType.BUY;
                }
            }

            if (grid.getState() == GridLineState.BOUGHT) {
                BigDecimal diff = price.subtract(grid.getSellPrice()).abs();
                if (minDiff == null || diff.compareTo(minDiff) < 0) {
                    minDiff = diff;
                    bestMatch = grid;
                    suggestedType = TradeType.SELL;
                }
            }
        }

        if (bestMatch == null) {
            throw new RuntimeException("未找到匹配的网格");
        }

        return buildGridSuggestion(bestMatch, suggestedType, price, minDiff);
    }

    // ==================== 删除操作 ====================

    /**
     * 删除策略及其关联数据
     */
    @Transactional
    public void deleteStrategy(Long id) {
        Strategy strategy = findStrategyById(id);
        log.info("删除策略: id={}, symbol={}", id, strategy.getSymbol());

        // 删除关联的交易记录
        tradeRecordRepository.deleteByStrategyId(id);

        // 删除关联的网格线（JPA 会级联删除，但显式删除更清晰）
        gridLineRepository.deleteByStrategyId(id);

        // 删除策略
        strategyRepository.deleteById(id);

        log.info("策略删除成功: id={}", id);
    }

    // ==================== 私有方法 ====================

    private Strategy findStrategyById(Long id) {
        return strategyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("策略不存在: " + id));
    }

    private Strategy findStrategyWithGridLines(Long id) {
        return strategyRepository.findByIdWithGridLines(id)
                .orElseThrow(() -> new RuntimeException("策略不存在: " + id));
    }

    private GridLine findGridLineById(Long id) {
        return gridLineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("网格线不存在: " + id));
    }

    private void validateCreateRequest(CreateStrategyRequest request) {
        if (request.getSymbol() == null || request.getSymbol().isEmpty()) {
            throw new IllegalArgumentException("symbol 不能为空");
        }
        if (request.getBasePrice() == null || request.getBasePrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("basePrice 必须大于 0");
        }
        if ((request.getAmountPerGrid() == null || request.getAmountPerGrid().compareTo(BigDecimal.ZERO) <= 0)
                && (request.getQuantityPerGrid() == null || request.getQuantityPerGrid().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalArgumentException("amountPerGrid 或 quantityPerGrid 必须提供一个且大于 0");
        }
    }

    private void validateTickRequest(TickRequest request) {
        if (request.getGridLineId() == null) {
            throw new IllegalArgumentException("gridLineId 必填");
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price 必填且必须大于 0");
        }
        if (request.getType() == null) {
            throw new IllegalArgumentException("type 必填");
        }
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity 必填且必须大于 0");
        }
        if (request.getFee() == null || request.getFee().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("fee 必填且必须大于等于 0");
        }
        if (request.getTradeTime() == null || request.getTradeTime().isEmpty()) {
            throw new IllegalArgumentException("tradeTime 必填");
        }
    }

    private BigDecimal calculateAmountPerGrid(CreateStrategyRequest request) {
        if (request.getQuantityPerGrid() != null && request.getQuantityPerGrid().compareTo(BigDecimal.ZERO) > 0) {
            return request.getBasePrice().multiply(request.getQuantityPerGrid());
        }
        return request.getAmountPerGrid();
    }

    private BigDecimal calculateQuantityPerGrid(CreateStrategyRequest request, BigDecimal amountPerGrid) {
        if (request.getQuantityPerGrid() != null && request.getQuantityPerGrid().compareTo(BigDecimal.ZERO) > 0) {
            return request.getQuantityPerGrid();
        }
        // 当只有 amountPerGrid 时，根据基准价计算数量
        return amountPerGrid.divide(request.getBasePrice(), 8, RoundingMode.DOWN);
    }

    private Strategy buildStrategy(CreateStrategyRequest request, BigDecimal amountPerGrid, BigDecimal quantityPerGrid) {
        Strategy strategy = new Strategy();
        strategy.setName(request.getName() != null ? request.getName() : "固定模板网格策略");
        strategy.setSymbol(request.getSymbol());
        strategy.setBasePrice(request.getBasePrice());
        strategy.setAmountPerGrid(amountPerGrid);
        strategy.setQuantityPerGrid(quantityPerGrid);

        // 固定网格参数
        BigDecimal smallGap = request.getBasePrice().multiply(GridConstants.SMALL_PROFIT_RATE);
        BigDecimal mediumGap = request.getBasePrice().multiply(GridConstants.MEDIUM_PROFIT_RATE);
        BigDecimal largeGap = request.getBasePrice().multiply(GridConstants.LARGE_PROFIT_RATE);

        strategy.setSmallGap(smallGap);
        strategy.setMediumGap(mediumGap);
        strategy.setLargeGap(largeGap);
        strategy.setGridCountDown(GridConstants.TOTAL_GRID_COUNT);
        strategy.setGridCountUp(0);
        strategy.setGridPercent(GridConstants.SMALL_PROFIT_RATE);

        BigDecimal maxCapital = amountPerGrid.multiply(BigDecimal.valueOf(GridConstants.TOTAL_GRID_COUNT));
        strategy.setMaxCapital(maxCapital);
        strategy.setAvailableCash(maxCapital);

        strategy.setStatus(StrategyStatus.RUNNING);
        strategy.setGridModelVersion("v2.0");
        strategy.setGridSummary("小网13/中网4/大网2");

        return strategy;
    }

    /**
     * 生成网格线（固定模板：19条网格 v2.0 - 锚点回撤式）
     */
    private void generateGridLines(Strategy strategy) {
        List<GridLine> gridLines = new ArrayList<>();

        BigDecimal basePrice = strategy.getBasePrice();
        BigDecimal amountPerGrid = strategy.getAmountPerGrid();
        BigDecimal decreaseFactor = BigDecimal.ONE.subtract(GridConstants.SMALL_PROFIT_RATE);

        BigDecimal lastSmallBuyPrice = basePrice;
        BigDecimal lastMediumBuyPrice = null;
        BigDecimal secondMediumBuyPrice = null;
        int mediumCount = 0;

        for (int i = 0; i < GridConstants.TOTAL_GRID_COUNT; i++) {
            GridType gridType = GridConstants.getGridType(i + 1);
            int level = i + 1;

            BigDecimal buyPrice;
            BigDecimal sellPrice;

            if (gridType == GridType.SMALL) {
                if (level == 1) {
                    buyPrice = basePrice;
                    sellPrice = basePrice.multiply(BigDecimal.ONE.add(GridConstants.SMALL_PROFIT_RATE))
                            .setScale(3, RoundingMode.HALF_UP);
                } else {
                    buyPrice = lastSmallBuyPrice.multiply(decreaseFactor)
                            .setScale(3, RoundingMode.DOWN);
                    sellPrice = lastSmallBuyPrice.setScale(3, RoundingMode.HALF_UP);
                }
                lastSmallBuyPrice = buyPrice;

            } else if (gridType == GridType.MEDIUM) {
                mediumCount++;
                buyPrice = lastSmallBuyPrice;

                if (level == 5) {
                    sellPrice = basePrice.setScale(3, RoundingMode.HALF_UP);
                } else {
                    sellPrice = lastMediumBuyPrice.setScale(3, RoundingMode.HALF_UP);
                }
                lastMediumBuyPrice = buyPrice;

                if (mediumCount == 2) {
                    secondMediumBuyPrice = buyPrice;
                }

            } else { // LARGE
                buyPrice = lastSmallBuyPrice;

                if (level == 10) {
                    sellPrice = basePrice.setScale(3, RoundingMode.HALF_UP);
                } else {
                    sellPrice = secondMediumBuyPrice.setScale(3, RoundingMode.HALF_UP);
                }
            }

            GridLine gridLine = createGridLine(strategy, gridType, level, buyPrice, sellPrice, amountPerGrid);
            gridLines.add(gridLine);
        }

        strategy.setGridLines(gridLines);
    }

    private GridLine createGridLine(
            Strategy strategy,
            GridType gridType,
            int level,
            BigDecimal buyPrice,
            BigDecimal sellPrice,
            BigDecimal buyAmount
    ) {
        GridLine gridLine = new GridLine();

        gridLine.setStrategy(strategy);
        gridLine.setGridType(gridType);
        gridLine.setLevel(level);
        gridLine.setState(GridLineState.WAIT_BUY);

        gridLine.setBuyPrice(buyPrice.setScale(3, RoundingMode.DOWN));
        gridLine.setSellPrice(sellPrice.setScale(3, RoundingMode.HALF_UP));

        gridLine.setBuyTriggerPrice(GridConstants.calculateBuyTriggerPrice(buyPrice));
        gridLine.setSellTriggerPrice(GridConstants.calculateSellTriggerPrice(sellPrice));

        // 使用每网默认数量（创建策略时必填，不考虑为空）
        BigDecimal buyQuantity = strategy.getQuantityPerGrid();
        BigDecimal actualBuyAmount = buyQuantity.multiply(buyPrice).setScale(2, RoundingMode.DOWN);

        gridLine.setBuyQuantity(buyQuantity);
        gridLine.setBuyAmount(actualBuyAmount);

        BigDecimal sellAmount = buyQuantity.multiply(gridLine.getSellPrice())
                .setScale(2, RoundingMode.DOWN);
        gridLine.setSellAmount(sellAmount);

        BigDecimal profit = sellAmount.subtract(actualBuyAmount);
        gridLine.setProfit(profit);

        BigDecimal profitRate = profit.divide(actualBuyAmount, 6, RoundingMode.HALF_UP);
        gridLine.setProfitRate(profitRate);

        return gridLine;
    }

    private LocalDateTime parseTradeTime(String tradeTimeStr) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(tradeTimeStr, formatter);
        } catch (Exception e) {
            throw new IllegalArgumentException("交易时间格式错误，请使用 yyyy-MM-dd HH:mm:ss 格式");
        }
    }

    private TickResponse buildTickResponse(Strategy strategy, BigDecimal price, List<TradeRecord> newTrades) {
        TickResponse response = new TickResponse();
        response.setStatus(strategy.getStatus());
        response.setCurrentPrice(price);
        response.setPosition(strategy.getPosition());
        response.setAvailableCash(strategy.getAvailableCash());
        response.setInvestedAmount(strategy.getInvestedAmount());
        response.setRealizedProfit(strategy.getRealizedProfit());

        List<TradeRecordDto> tradeDtos = newTrades.stream()
                .map(TradeRecordDto::fromEntity)
                .collect(Collectors.toList());
        response.setTrades(tradeDtos);

        return response;
    }

    private GridPlanResponse.GridPlanItem convertToGridPlanItem(GridLine gridLine) {
        GridPlanResponse.GridPlanItem item = new GridPlanResponse.GridPlanItem();
        item.setId(gridLine.getId());
        item.setGridType(gridLine.getGridType());
        item.setLevel(gridLine.getLevel());
        item.setBuyPrice(gridLine.getBuyPrice());
        item.setSellPrice(gridLine.getSellPrice());
        item.setActualBuyPrice(gridLine.getActualBuyPrice());
        item.setActualSellPrice(gridLine.getActualSellPrice());
        item.setBuyTriggerPrice(gridLine.getBuyTriggerPrice());
        item.setSellTriggerPrice(gridLine.getSellTriggerPrice());
        item.setQuantity(gridLine.getBuyQuantity());
        item.setBuyAmount(gridLine.getBuyAmount());
        item.setSellAmount(gridLine.getSellAmount());
        item.setProfit(gridLine.getProfit());
        item.setProfitRate(gridLine.getProfitRate());
        item.setState(gridLine.getState());

        Integer buyCount = gridLine.getBuyCount() != null ? gridLine.getBuyCount() : 0;
        Integer sellCount = gridLine.getSellCount() != null ? gridLine.getSellCount() : 0;
        item.setBuyCount(buyCount);
        item.setSellCount(sellCount);

        BigDecimal actualProfit = gridLine.getActualProfit() != null ? gridLine.getActualProfit() : BigDecimal.ZERO;
        item.setActualProfit(actualProfit.setScale(2, RoundingMode.HALF_UP));

        // 计算预计收益：(卖出价 - 买入价) × 数量
        // 卖出价/买入价优先使用实际值
        BigDecimal sellPrice = gridLine.getActualSellPrice() != null
            ? gridLine.getActualSellPrice()
            : gridLine.getSellPrice();
        BigDecimal buyPrice = gridLine.getActualBuyPrice() != null
            ? gridLine.getActualBuyPrice()
            : gridLine.getBuyPrice();
        BigDecimal quantity = gridLine.getBuyQuantity() != null
            ? gridLine.getBuyQuantity()
            : BigDecimal.ZERO;
        BigDecimal expectedProfit = sellPrice.subtract(buyPrice).multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        item.setExpectedProfit(expectedProfit);

        if (gridLine.getProfit() != null) {
            item.setProfit(gridLine.getProfit().setScale(2, RoundingMode.HALF_UP));
        }

        return item;
    }

    private BigDecimal calculateExpectedProfit(GridLine gridLine, BigDecimal actualBuyPrice) {
        BigDecimal sellPrice = gridLine.getSellPrice();
        BigDecimal quantity = gridLine.getBuyQuantity();
        BigDecimal estimatedFee = sellPrice.multiply(quantity).multiply(new BigDecimal("0.0001"));
        return sellPrice.multiply(quantity)
                .subtract(actualBuyPrice.multiply(quantity))
                .subtract(estimatedFee)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private GridSuggestionDTO buildGridSuggestion(GridLine gridLine, TradeType suggestedType, BigDecimal price, BigDecimal minDiff) {
        GridSuggestionDTO suggestion = new GridSuggestionDTO();
        suggestion.setGridLineId(gridLine.getId());
        suggestion.setLevel(gridLine.getLevel());
        suggestion.setGridType(gridLine.getGridType());
        suggestion.setSuggestedType(suggestedType);
        suggestion.setBuyPrice(gridLine.getBuyPrice());
        suggestion.setSellPrice(gridLine.getSellPrice());
        suggestion.setState(gridLine.getState());
        suggestion.setInputPrice(price);
        suggestion.setPriceDiff(minDiff);
        return suggestion;
    }

    /**
     * 计算当日参考盈亏
     */
    private void calculateTodayProfit(StrategyDetailDTO dto, Strategy strategy) {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        List<TradeRecord> allRecords = tradeRecordRepository.findByStrategyIdOrderByTradeTimeAsc(strategy.getId());

        List<TradeRecord> recordsBeforeToday = new ArrayList<>();
        List<TradeRecord> recordsToday = new ArrayList<>();

        for (TradeRecord record : allRecords) {
            if (record.getTradeTime().isBefore(todayStart)) {
                recordsBeforeToday.add(record);
            } else {
                recordsToday.add(record);
            }
        }

        BigDecimal yesterdayPosition = BigDecimal.ZERO;
        BigDecimal yesterdayCost = BigDecimal.ZERO;

        if (!recordsBeforeToday.isEmpty()) {
            BigDecimal totalBuyQty = BigDecimal.ZERO;
            BigDecimal totalBuyAmt = BigDecimal.ZERO;
            BigDecimal totalSellQty = BigDecimal.ZERO;
            BigDecimal totalSellAmt = BigDecimal.ZERO;
            BigDecimal totalFee = BigDecimal.ZERO;

            for (TradeRecord record : recordsBeforeToday) {
                BigDecimal amount = record.getAmount() != null ? record.getAmount() : BigDecimal.ZERO;
                BigDecimal fee = record.getFee() != null ? record.getFee() : BigDecimal.ZERO;
                BigDecimal quantity = record.getQuantity() != null ? record.getQuantity() : BigDecimal.ZERO;

                totalFee = totalFee.add(fee);

                if (record.getType() == TradeType.BUY || record.getType() == TradeType.OPENING_BUY) {
                    totalBuyQty = totalBuyQty.add(quantity);
                    totalBuyAmt = totalBuyAmt.add(amount);
                } else if (record.getType() == TradeType.SELL) {
                    totalSellQty = totalSellQty.add(quantity);
                    totalSellAmt = totalSellAmt.add(amount);
                }
            }

            yesterdayPosition = totalBuyQty.subtract(totalSellQty);
            BigDecimal netInvestment = totalBuyAmt.subtract(totalSellAmt).add(totalFee);

            if (yesterdayPosition.compareTo(BigDecimal.ZERO) > 0) {
                yesterdayCost = netInvestment.divide(yesterdayPosition, 8, RoundingMode.HALF_UP);
            }
        }

        BigDecimal currentPrice = strategy.getLastPrice() != null ? strategy.getLastPrice() : strategy.getBasePrice();
        BigDecimal currentPosition = strategy.getPosition() != null ? strategy.getPosition() : BigDecimal.ZERO;

        BigDecimal todayProfit = BigDecimal.ZERO;
        BigDecimal todayProfitPercent = BigDecimal.ZERO;

        if (yesterdayPosition.compareTo(BigDecimal.ZERO) > 0 && currentPrice != null) {
            BigDecimal yesterdayMarketValue = currentPrice.multiply(yesterdayPosition);
            BigDecimal yesterdayCostValue = yesterdayCost.multiply(yesterdayPosition);

            if (!recordsToday.isEmpty()) {
                BigDecimal totalPositionProfit = strategy.getPositionProfit() != null ? strategy.getPositionProfit() : BigDecimal.ZERO;
                todayProfit = totalPositionProfit.multiply(new BigDecimal("0.3")).setScale(2, RoundingMode.HALF_UP);
            } else {
                todayProfit = yesterdayMarketValue.subtract(yesterdayCostValue).setScale(2, RoundingMode.HALF_UP);
            }

            if (yesterdayCostValue.compareTo(BigDecimal.ZERO) > 0) {
                todayProfitPercent = todayProfit.divide(yesterdayCostValue, 6, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).setScale(3, RoundingMode.HALF_UP);
            }
        } else if (currentPosition.compareTo(BigDecimal.ZERO) > 0 && currentPrice != null) {
            BigDecimal totalPositionProfit = strategy.getPositionProfit() != null ? strategy.getPositionProfit() : BigDecimal.ZERO;
            todayProfit = totalPositionProfit.multiply(new BigDecimal("0.5")).setScale(2, RoundingMode.HALF_UP);

            BigDecimal costPrice = strategy.getCostPrice() != null ? strategy.getCostPrice() : BigDecimal.ZERO;
            if (costPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal positionProfitPercent = strategy.getPositionProfitPercent() != null ?
                        strategy.getPositionProfitPercent() : BigDecimal.ZERO;
                todayProfitPercent = positionProfitPercent.multiply(new BigDecimal("0.5")).setScale(3, RoundingMode.HALF_UP);
            }
        }

        if (todayProfit.compareTo(BigDecimal.ZERO) == 0 && todayProfitPercent.compareTo(BigDecimal.ZERO) == 0) {
            if (currentPrice != null && strategy.getCostPrice() != null &&
                    strategy.getPosition() != null && strategy.getCostPrice().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal costPrice = strategy.getCostPrice();
                BigDecimal position = strategy.getPosition();
                BigDecimal diff = currentPrice.subtract(costPrice);
                todayProfit = diff.multiply(position).setScale(2, RoundingMode.HALF_UP);
                todayProfitPercent = diff.divide(costPrice, 8, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).setScale(3, RoundingMode.HALF_UP);
            }
        }

        dto.setTodayProfit(todayProfit);
        dto.setTodayProfitPercent(todayProfitPercent);
    }
}
