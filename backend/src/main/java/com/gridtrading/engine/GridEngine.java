package com.gridtrading.engine;

import com.gridtrading.domain.*;
import com.gridtrading.repository.GridLineRepository;
import com.gridtrading.repository.StrategyRepository;
import com.gridtrading.repository.TradeRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 网格交易执行引擎
 * <p>
 * 核心职责：
 * 1. 判断是否触发买卖
 * 2. 支持"一网打尽"（一次价格更新触发多个网格）
 * 3. 自动切换网格状态
 * 4. 维护资金/持仓/收益
 * 5. 支持 STOP 风控
 */
@Service
@Slf4j
public class GridEngine {

    private final StrategyRepository strategyRepository;
    private final GridLineRepository gridLineRepository;
    private final TradeRecordRepository tradeRecordRepository;

    public GridEngine(
            StrategyRepository strategyRepository,
            GridLineRepository gridLineRepository,
            TradeRecordRepository tradeRecordRepository
    ) {
        this.strategyRepository = strategyRepository;
        this.gridLineRepository = gridLineRepository;
        this.tradeRecordRepository = tradeRecordRepository;
    }

    /**
     * 处理价格更新
     * <p>
     * 执行顺序：Step 0 → Step 1 → Step 2 → Step 3
     *
     * @param strategyId 策略 ID
     * @param price      当前价格
     */
    @Transactional
    public void processPrice(Long strategyId, BigDecimal price) {
        // 加载策略（包含网格线）
        Strategy strategy = strategyRepository.findByIdWithGridLines(strategyId)
                .orElseThrow(() -> new IllegalArgumentException("策略不存在: " + strategyId));
        log.info("GridLines size = {}", strategy.getGridLines().size());

        // ============ Step 0: 更新最新价格 ============
        strategy.setLastPrice(price);

        // ============ Step 1: 买入处理（仅 RUNNING 状态） ============
        processBuy(strategy, price);

        // ============ Step 2: 卖出处理（RUNNING / STOPPED 都允许） ============
        processSell(strategy, price);

        // ============ Step 3: 风控 STOP 判断 ============
        checkAndStop(strategy, price);

        // 保存策略状态
        strategyRepository.save(strategy);
    }

    /**
     * Step 1: 买入处理（固定模板网格）
     * <p>
     * 状态机：WAIT_BUY → BOUGHT
     * <p>
     * 触发条件：
     * 1. Strategy.status == RUNNING
     * 2. GridLine.state == WAIT_BUY （严格过滤）
     * 3. price <= gridLine.buyPrice
     * 4. availableCash >= amountPerGrid
     * 5. 已买入网格数量 < 19（固定模板硬风控）
     * <p>
     * 排序：按 level 从小到大（按顺序买入）
     * <p>
     * 禁止：同一个 GridLine 在一次 processPrice 中 BUY 两次
     */
    private void processBuy(Strategy strategy, BigDecimal price) {
        // 只有 RUNNING 状态才允许买入
        if (strategy.getStatus() != StrategyStatus.RUNNING) {
            return;
        }

        // 【固定模板风控】检查已买入网格数量
        long boughtCount = gridLineRepository.countByStrategyAndState(strategy, GridLineState.BOUGHT);
        if (boughtCount >= 19) {
            log.info("[BUY-STOP] 已买入网格数量达到19条，禁止继续买入");
            return;
        }

        // 查询等待买入的网格线（严格过滤 state = WAIT_BUY）
        List<GridLine> waitBuyLines = gridLineRepository
                .findByStrategyAndStateOrderByLevelAsc(strategy, GridLineState.WAIT_BUY);

        // 遍历所有等待买入的网格线（支持"一网打尽"）
        for (GridLine gridLine : waitBuyLines) {
            // 【固定模板风控】再次检查已买入数量（防止在循环中超出限制）
            boughtCount = gridLineRepository.countByStrategyAndState(strategy, GridLineState.BOUGHT);
            if (boughtCount >= 19) {
                log.info("[BUY-STOP] 已买入网格数量达到19条，停止买入");
                break;
            }

            // 二次确认状态（防止并发或重复）
            if (gridLine.getState() != GridLineState.WAIT_BUY) {
                log.warn("[BUY-SKIP] gridLineId={}, level={}, state={} (不是 WAIT_BUY)",
                        gridLine.getId(), gridLine.getLevel(), gridLine.getState());
                continue;
            }

            // 检查是否触发买入条件
            if (price.compareTo(gridLine.getBuyPrice()) > 0) {
                // 价格高于买入价，不触发
                continue;
            }

            // 检查资金是否充足
            if (strategy.getAvailableCash().compareTo(strategy.getAmountPerGrid()) < 0) {
                // 资金不足，停止后续买入
                log.info("[BUY-STOP] 资金不足，停止买入");
                break;
            }

            // ===== 执行买入 =====
            log.info("[BUY] gridLineId={}, level={}, price={}, buyPrice={}",
                    gridLine.getId(), gridLine.getLevel(), price, gridLine.getBuyPrice());

            // 计算买入数量 = 每格金额 / 买入价格
            BigDecimal quantity = strategy.getAmountPerGrid()
                    .divide(gridLine.getBuyPrice(), 8, RoundingMode.DOWN);

            // 【关键】先更新状态：WAIT_BUY → BOUGHT
            gridLine.setState(GridLineState.BOUGHT);
            // 增加买入次数统计
            gridLine.setBuyCount(gridLine.getBuyCount() + 1);
            log.info("[BUY-COUNT] gridLineId={}, level={}, buyCount={}", 
                    gridLine.getId(), gridLine.getLevel(), gridLine.getBuyCount());
            gridLineRepository.save(gridLine);

            // 生成买入交易记录（且仅一条）
            TradeRecord tradeRecord = new TradeRecord();
            tradeRecord.setStrategy(strategy);
            tradeRecord.setGridLine(gridLine);
            tradeRecord.setType(TradeType.BUY);
            tradeRecord.setPrice(gridLine.getBuyPrice());
            tradeRecord.setAmount(strategy.getAmountPerGrid());
            tradeRecord.setQuantity(quantity);
            tradeRecord.setTradeTime(LocalDateTime.now());
            tradeRecordRepository.save(tradeRecord);


            // 更新策略资金和持仓
            strategy.setAvailableCash(
                    strategy.getAvailableCash().subtract(strategy.getAmountPerGrid())
            );
            strategy.setInvestedAmount(
                    strategy.getInvestedAmount().add(strategy.getAmountPerGrid())
            );
            strategy.setPosition(
                    strategy.getPosition().add(quantity)
            );
        }
    }

    /**
     * Step 2: 卖出处理（固定模板网格）
     * <p>
     * 状态机：BOUGHT → WAIT_BUY （循环网格）
     * <p>
     * 触发条件：
     * 1. GridLine.state == BOUGHT （严格过滤，兼容旧的 WAIT_SELL）
     * 2. price >= gridLine.sellPrice
     * <p>
     * 排序：按 sellPrice 从低到高（靠近当前价的优先）
     * <p>
     * 注意：无论策略是 RUNNING 还是 STOPPED，卖出都允许执行
     */
    private void processSell(Strategy strategy, BigDecimal price) {
        // 查询已买入的网格线（严格过滤 state = BOUGHT，兼容 WAIT_SELL）
        List<GridLine> boughtLines = gridLineRepository
                .findByStrategyAndStateOrderBySellPriceAsc(strategy, GridLineState.BOUGHT);

        // 兼容旧状态 WAIT_SELL
        List<GridLine> waitSellLines = gridLineRepository
                .findByStrategyAndStateOrderBySellPriceAsc(strategy, GridLineState.WAIT_SELL);

        // 合并两个列表（优先处理 BOUGHT）
        List<GridLine> allSellableLines = new java.util.ArrayList<>(boughtLines);
        allSellableLines.addAll(waitSellLines);

        // 遍历所有可卖出的网格线（支持"一网打尽"）
        for (GridLine gridLine : allSellableLines) {
            // 二次确认状态（防止并发或重复）
            if (gridLine.getState() != GridLineState.BOUGHT
                && gridLine.getState() != GridLineState.WAIT_SELL) {
                log.warn("[SELL-SKIP] gridLineId={}, level={}, state={} (不是 BOUGHT/WAIT_SELL)",
                        gridLine.getId(), gridLine.getLevel(), gridLine.getState());
                continue;
            }

            // 检查是否触发卖出条件
            if (price.compareTo(gridLine.getSellPrice()) < 0) {
                // 价格低于卖出价，不触发
                continue;
            }

            // ===== 执行卖出 =====
            log.info("[SELL] gridLineId={}, level={}, price={}, sellPrice={}",
                    gridLine.getId(), gridLine.getLevel(), price, gridLine.getSellPrice());

            // 计算卖出数量（与买入时相同）
            BigDecimal quantity = strategy.getAmountPerGrid()
                    .divide(gridLine.getBuyPrice(), 8, RoundingMode.DOWN);

            // 计算卖出金额 = 数量 × 卖出价格
            BigDecimal sellAmount = quantity.multiply(gridLine.getSellPrice())
                    .setScale(2, RoundingMode.DOWN);

            // 计算收益 = 卖出金额 - 买入金额
            BigDecimal profit = sellAmount.subtract(strategy.getAmountPerGrid());

            // 【关键】先更新状态：BOUGHT/WAIT_SELL → WAIT_BUY（循环网格）
            gridLine.setState(GridLineState.WAIT_BUY);
            // 增加卖出次数统计
            gridLine.setSellCount(gridLine.getSellCount() + 1);
            log.info("[SELL-COUNT] gridLineId={}, level={}, sellCount={}", 
                    gridLine.getId(), gridLine.getLevel(), gridLine.getSellCount());
            gridLineRepository.save(gridLine);

            // 生成卖出交易记录（且仅一条）
            TradeRecord tradeRecord = new TradeRecord();
            tradeRecord.setStrategy(strategy);
            tradeRecord.setGridLine(gridLine);
            tradeRecord.setType(TradeType.SELL);
            tradeRecord.setPrice(gridLine.getSellPrice());
            tradeRecord.setAmount(sellAmount);
            tradeRecord.setQuantity(quantity);
            tradeRecord.setTradeTime(LocalDateTime.now());
            tradeRecordRepository.save(tradeRecord);

            // 更新策略资金和持仓
            strategy.setAvailableCash(
                    strategy.getAvailableCash().add(sellAmount)
            );
            strategy.setInvestedAmount(
                    strategy.getInvestedAmount().subtract(strategy.getAmountPerGrid())
            );
            strategy.setPosition(
                    strategy.getPosition().subtract(quantity)
            );
            strategy.setRealizedProfit(
                    strategy.getRealizedProfit().add(profit)
            );
        }
    }

    /**
     * Step 3: 风控 STOP 判断
     * <p>
     * 触发条件（满足任一即可）：
     * 1. price <= 最低买入网格价格
     * 2. 已投入资金 >= maxCapital
     * <p>
     * STOP 行为：
     * - 不清仓（保留已买入的持仓）
     * - 只停止后续买入
     * - 允许卖出（价格上涨时可平仓获利）
     */
    private void checkAndStop(Strategy strategy, BigDecimal price) {
        // 如果已经是 STOPPED 状态，无需重复判断
        if (strategy.getStatus() == StrategyStatus.STOPPED) {
            return;
        }

        boolean shouldStop = false;

        // 条件 1: 价格跌破最低买入网格价格
        BigDecimal lowestBuyPrice = gridLineRepository.findLowestBuyPrice(strategy);
        if (lowestBuyPrice != null && price.compareTo(lowestBuyPrice) <= 0) {
            shouldStop = true;
        }

        // 条件 2: 已投入资金达到或超过最大资金
        if (strategy.getInvestedAmount().compareTo(strategy.getMaxCapital()) >= 0) {
            shouldStop = true;
        }

        // 触发 STOP
        if (shouldStop) {
            strategy.setStatus(StrategyStatus.STOPPED);
        }
    }
}
