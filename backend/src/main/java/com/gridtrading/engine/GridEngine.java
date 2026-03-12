package com.gridtrading.engine;

import com.gridtrading.constants.GridConstants;
import com.gridtrading.domain.*;
import com.gridtrading.repository.GridLineRepository;
import com.gridtrading.repository.StrategyRepository;
import com.gridtrading.repository.TradeRecordRepository;
import com.gridtrading.service.GridService;
import com.gridtrading.service.PositionCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 网格交易执行引擎
 * <p>
 * 核心职责：
 * 1. 处理手动交易录入
 * 2. 更新网格状态
 * 3. 触发级联更新
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GridEngine {

    private final StrategyRepository strategyRepository;
    private final GridLineRepository gridLineRepository;
    private final TradeRecordRepository tradeRecordRepository;
    private final PositionCalculator positionCalculator;
    private final GridService gridService;

    /**
     * 处理手动录入的交易
     * <p>
     * 核心逻辑：
     * 1. 前端指定 gridLineId + type，后端不做自动匹配
     * 2. 使用用户传入的实际价格、数量、手续费、交易时间
     * 3. 更新网格线的 actualBuyPrice/actualSellPrice
     * 4. 触发级联更新
     */
    @Transactional
    public void processManualTrade(
            Long strategyId,
            Long gridLineId,
            TradeType type,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal fee,
            LocalDateTime tradeTime
    ) {
        Strategy strategy = strategyRepository.findByIdWithGridLines(strategyId)
                .orElseThrow(() -> new IllegalArgumentException("策略不存在: " + strategyId));

        GridLine gridLine = strategy.getGridLines().stream()
                .filter(gl -> gl.getId().equals(gridLineId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("网格线不存在: " + gridLineId));

        // 更新最新价格
        strategy.setLastPrice(price);

        // 计算交易金额
        BigDecimal amount = price.multiply(quantity);

        // 创建交易记录
        TradeRecord tradeRecord = createTradeRecord(strategy, gridLine, type, price, quantity, amount, fee, tradeTime);
        tradeRecordRepository.save(tradeRecord);

        // 执行买入或卖出的状态更新
        if (type == TradeType.BUY) {
            executeBuy(strategy, gridLine, price, quantity, amount);
        } else {
            executeSell(strategy, gridLine, price, quantity, amount);
        }

        // 计算持仓相关字段
        positionCalculator.calculateAndUpdate(strategy);

        // 保存
        gridLineRepository.save(gridLine);
        strategyRepository.save(strategy);
    }

    /**
     * 执行买入操作
     */
    private void executeBuy(Strategy strategy, GridLine gridLine, BigDecimal price, BigDecimal quantity, BigDecimal amount) {
        // 更新网格线状态
        gridLine.setState(GridLineState.BOUGHT);
        gridLine.setBuyCount(gridLine.getBuyCount() + 1);
        gridLine.setActualBuyPrice(price);
        gridLine.setBuyPrice(price);

        // 更新买入触发价
        gridLine.setBuyTriggerPrice(GridConstants.calculateBuyTriggerPrice(price));

        // 更新卖出价
        gridService.updateSellPriceAfterBuy(strategy, gridLine);

        log.info("[BUY] gridLineId={}, level={}, price={}, quantity={}, buyCount={}",
                gridLine.getId(), gridLine.getLevel(), price, quantity, gridLine.getBuyCount());

        // 重新计算后续网格
        gridService.recalculateSubsequentGrids(strategy, gridLine, price);

        // 更新策略资金和持仓
        strategy.setAvailableCash(strategy.getAvailableCash().subtract(amount));
        strategy.setInvestedAmount(strategy.getInvestedAmount().add(amount));
        strategy.setPosition(strategy.getPosition().add(quantity));
    }

    /**
     * 执行卖出操作
     */
    private void executeSell(Strategy strategy, GridLine gridLine, BigDecimal price, BigDecimal quantity, BigDecimal amount) {
        // 更新网格线状态（循环网格）
        gridLine.setState(GridLineState.WAIT_BUY);
        gridLine.setSellCount(gridLine.getSellCount() + 1);
        gridLine.setActualSellPrice(price);
        gridLine.setSellPrice(price);

        // 更新卖出触发价
        gridLine.setSellTriggerPrice(GridConstants.calculateSellTriggerPrice(price));

        log.info("[SELL] gridLineId={}, level={}, price={}, quantity={}, sellCount={}",
                gridLine.getId(), gridLine.getLevel(), price, quantity, gridLine.getSellCount());

        // 计算收益
        BigDecimal profit = amount.subtract(gridLine.getBuyAmount());

        // 更新实际收益（累计）
        BigDecimal currentActualProfit = gridLine.getActualProfit() != null ? gridLine.getActualProfit() : BigDecimal.ZERO;
        gridLine.setActualProfit(currentActualProfit.add(profit));

        // 更新策略资金和持仓
        strategy.setAvailableCash(strategy.getAvailableCash().add(amount));
        strategy.setInvestedAmount(strategy.getInvestedAmount().subtract(gridLine.getBuyAmount()));
        strategy.setPosition(strategy.getPosition().subtract(quantity));
        strategy.setRealizedProfit(strategy.getRealizedProfit().add(profit));
    }

    /**
     * 创建交易记录
     */
    private TradeRecord createTradeRecord(
            Strategy strategy,
            GridLine gridLine,
            TradeType type,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal amount,
            BigDecimal fee,
            LocalDateTime tradeTime
    ) {
        TradeRecord tradeRecord = new TradeRecord();
        tradeRecord.setStrategy(strategy);
        tradeRecord.setGridLine(gridLine);
        tradeRecord.setType(type);
        tradeRecord.setPrice(price);
        tradeRecord.setQuantity(quantity);
        tradeRecord.setAmount(amount);
        tradeRecord.setFee(fee);
        tradeRecord.setTradeTime(tradeTime != null ? tradeTime : LocalDateTime.now());
        return tradeRecord;
    }

    /**
     * 更新当前网格买入后的卖出价（供外部调用）
     */
    public void updateCurrentGridSellPriceAfterBuy(Strategy strategy, GridLine currentGridLine) {
        gridService.updateSellPriceAfterBuy(strategy, currentGridLine);
    }

    /**
     * 重新计算后续网格（供外部调用）
     */
    public void recalculateSubsequentGridsAfterManualBuy(Strategy strategy, GridLine currentGridLine, BigDecimal actualBuyPrice) {
        gridService.recalculateSubsequentGrids(strategy, currentGridLine, actualBuyPrice);
    }
}