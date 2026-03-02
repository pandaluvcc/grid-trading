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
     * 处理价格更新（自动模式）
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
     * 处理手动录入的交易
     * <p>
     * 核心逻辑：
     * 1. 根据价格找到最匹配的网格线
     * 2. 根据网格线状态判断应该执行的交易类型（BUY/SELL）
     * 3. 验证用户传入的 type 是否与引擎判断的一致
     * 4. 使用用户传入的实际价格、数量、手续费、交易时间
     * 5. 更新网格线的 actualBuyPrice/actualSellPrice
     *
     * @param strategyId 策略 ID
     * @param userType   用户传入的交易类型（用于验证）
     * @param price      实际成交价格
     * @param quantity   实际成交数量
     * @param fee        手续费
     * @param tradeTime  交易时间
     */
    @Transactional
    public void processManualTrade(
            Long strategyId,
            TradeType userType,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal fee,
            LocalDateTime tradeTime
    ) {
        // 加载策略（包含网格线）
        Strategy strategy = strategyRepository.findByIdWithGridLines(strategyId)
                .orElseThrow(() -> new IllegalArgumentException("策略不存在: " + strategyId));

        // 更新最新价格
        strategy.setLastPrice(price);

        // 根据价格查找最匹配的网格线（不分交易类型，只看价格）
        GridLine matchedGridLine = findMatchingGridLineByPrice(strategy, price);
        if (matchedGridLine == null) {
            throw new RuntimeException("未找到匹配的网格线，价格=" + price);
        }

        // 根据网格线当前状态判断应该执行的交易类型
        TradeType expectedType = determineTradeType(matchedGridLine, price);

        // 验证用户传入的交易类型是否与引擎判断的一致
        if (userType != expectedType) {
            throw new IllegalArgumentException(
                String.format("交易类型不匹配！网格线 [Level=%d, 状态=%s] 应该执行 %s，但用户传入了 %s。请检查交易引擎算法或重新确认交易类型。",
                    matchedGridLine.getLevel(),
                    matchedGridLine.getState(),
                    expectedType,
                    userType)
            );
        }

        // 计算交易金额
        BigDecimal amount = price.multiply(quantity);

        // 创建交易记录
        TradeRecord tradeRecord = new TradeRecord();
        tradeRecord.setStrategy(strategy);
        tradeRecord.setGridLine(matchedGridLine);
        tradeRecord.setType(expectedType);  // 使用引擎判断的类型
        tradeRecord.setPrice(price);
        tradeRecord.setQuantity(quantity);
        tradeRecord.setAmount(amount);
        tradeRecord.setFee(fee);
        tradeRecord.setTradeTime(tradeTime != null ? tradeTime : LocalDateTime.now());
        tradeRecordRepository.save(tradeRecord);

        // 执行买入或卖出的状态更新
        if (expectedType == TradeType.BUY) {
            executeManualBuy(strategy, matchedGridLine, price, quantity, amount);
        } else {
            executeManualSell(strategy, matchedGridLine, price, quantity, amount);
        }

        // 保存策略和网格线
        gridLineRepository.save(matchedGridLine);
        strategyRepository.save(strategy);
    }

    /**
     * 根据价格查找最匹配的网格线（不考虑交易类型）
     * 找到买入价或卖出价最接近当前价格的网格线
     */
    private GridLine findMatchingGridLineByPrice(Strategy strategy, BigDecimal price) {
        List<GridLine> gridLines = strategy.getGridLines();
        GridLine bestMatch = null;
        BigDecimal minDiff = null;

        for (GridLine gridLine : gridLines) {
            // 计算与买入价的差异
            BigDecimal buyDiff = price.subtract(gridLine.getBuyPrice()).abs();
            // 计算与卖出价的差异
            BigDecimal sellDiff = price.subtract(gridLine.getSellPrice()).abs();

            // 取较小的差异
            BigDecimal diff = buyDiff.compareTo(sellDiff) < 0 ? buyDiff : sellDiff;

            // 找到差异最小的网格线
            if (minDiff == null || diff.compareTo(minDiff) < 0) {
                minDiff = diff;
                bestMatch = gridLine;
            }
        }

        return bestMatch;
    }

    /**
     * 根据网格线状态和价格判断应该执行的交易类型
     *
     * @param gridLine 网格线
     * @param price 当前价格
     * @return 应该执行的交易类型（BUY 或 SELL）
     */
    private TradeType determineTradeType(GridLine gridLine, BigDecimal price) {
        GridLineState state = gridLine.getState();

        // 判断价格更接近买入价还是卖出价
        BigDecimal buyDiff = price.subtract(gridLine.getBuyPrice()).abs();
        BigDecimal sellDiff = price.subtract(gridLine.getSellPrice()).abs();
        boolean closerToBuyPrice = buyDiff.compareTo(sellDiff) <= 0;

        // 根据状态判断
        if (state == GridLineState.WAIT_BUY) {
            // 等待买入状态 → 应该执行买入
            return TradeType.BUY;
        } else if (state == GridLineState.BOUGHT) {
            // 已买入状态 → 应该执行卖出
            return TradeType.SELL;
        } else if (state == GridLineState.WAIT_SELL) {
            // 等待卖出状态（兼容旧状态）→ 应该执行卖出
            return TradeType.SELL;
        } else {
            // 其他状态，根据价格判断
            return closerToBuyPrice ? TradeType.BUY : TradeType.SELL;
        }
    }

    /**
     * 执行手动买入的状态更新
     */
    private void executeManualBuy(Strategy strategy, GridLine gridLine, BigDecimal price, BigDecimal quantity, BigDecimal amount) {
        // 更新网格线状态
        gridLine.setState(GridLineState.BOUGHT);
        gridLine.setBuyCount(gridLine.getBuyCount() + 1);
        gridLine.setActualBuyPrice(price);  // 记录实际买入价
        gridLine.setBuyPrice(price);        // 同步更新计划买入价，以便后续网格基于此计算

        log.info("[MANUAL-BUY] gridLineId={}, level={}, price={}, quantity={}, buyCount={}",
                gridLine.getId(), gridLine.getLevel(), price, quantity, gridLine.getBuyCount());

        // 重新计算后续网格的买入价（因为当前网格的买入价改变了）
        recalculateSubsequentGridsAfterManualBuy(strategy, gridLine, price);

        // 更新策略资金和持仓
        strategy.setAvailableCash(strategy.getAvailableCash().subtract(amount));
        strategy.setInvestedAmount(strategy.getInvestedAmount().add(amount));
        strategy.setPosition(strategy.getPosition().add(quantity));
    }

    /**
     * 执行手动卖出的状态更新
     */
    private void executeManualSell(Strategy strategy, GridLine gridLine, BigDecimal price, BigDecimal quantity, BigDecimal amount) {
        // 更新网格线状态（循环网格）
        gridLine.setState(GridLineState.WAIT_BUY);
        gridLine.setSellCount(gridLine.getSellCount() + 1);
        gridLine.setActualSellPrice(price);  // 记录实际卖出价

        log.info("[MANUAL-SELL] gridLineId={}, level={}, price={}, quantity={}, sellCount={}",
                gridLine.getId(), gridLine.getLevel(), price, quantity, gridLine.getSellCount());

        // 计算收益（卖出金额 - 买入金额）
        BigDecimal profit = amount.subtract(gridLine.getBuyAmount());

        // 更新策略资金和持仓
        strategy.setAvailableCash(strategy.getAvailableCash().add(amount));
        strategy.setInvestedAmount(strategy.getInvestedAmount().subtract(gridLine.getBuyAmount()));
        strategy.setPosition(strategy.getPosition().subtract(quantity));
        strategy.setRealizedProfit(strategy.getRealizedProfit().add(profit));
    }

    /**
     * 重新计算后续网格的买入价（手动买入后触发）
     *
     * 核心逻辑：
     * 当一个小网的买入价变化时，需要更新：
     * 1. 紧跟在后面的中网和大网（它们继承小网的买入价）
     * 2. 后续所有小网（基于新价格按×0.95递减）
     * 3. 递归更新所有受影响的网格
     *
     * @param strategy 策略
     * @param currentGridLine 当前手动买入的网格线
     * @param actualBuyPrice 实际买入价
     */
    private void recalculateSubsequentGridsAfterManualBuy(Strategy strategy, GridLine currentGridLine, BigDecimal actualBuyPrice) {
        List<GridLine> allGridLines = strategy.getGridLines();
        int currentLevel = currentGridLine.getLevel();
        GridType currentType = currentGridLine.getGridType();

        // 只有小网的买入价变化才会影响后续网格
        if (currentType != GridType.SMALL) {
            return;
        }

        // 按 level 排序
        allGridLines.sort((a, b) -> a.getLevel().compareTo(b.getLevel()));

        // 固定比例递减系数 0.95
        BigDecimal decreaseFactor = new BigDecimal("0.95");

        // 追踪最新的小网买入价（用于后续小网计算）
        BigDecimal lastSmallBuyPrice = actualBuyPrice;
        // 追踪上一个小网的买入价（用于小网的sellPrice）
        BigDecimal previousSmallBuyPrice = actualBuyPrice;

        // 追踪中网买入价（用于中网卖出锚点）
        BigDecimal lastMediumBuyPrice = null;
        BigDecimal secondMediumBuyPrice = null;
        int mediumCount = 0;

        // 先找到当前小网之前的中网信息（用于锚点计算）
        for (GridLine gl : allGridLines) {
            if (gl.getLevel() >= currentLevel) {
                break;
            }
            if (gl.getGridType() == GridType.MEDIUM) {
                mediumCount++;
                BigDecimal mediumPrice = gl.getActualBuyPrice() != null ?
                    gl.getActualBuyPrice() : gl.getBuyPrice();
                lastMediumBuyPrice = mediumPrice;
                if (mediumCount == 2) {
                    secondMediumBuyPrice = mediumPrice;
                }
            }
        }

        // 找到当前小网后面的所有网格并重新计算
        boolean foundCurrent = false;
        for (GridLine gridLine : allGridLines) {
            if (!foundCurrent) {
                if (gridLine.getLevel() == currentLevel) {
                    foundCurrent = true;
                }
                continue;
            }

            // 只更新未成交的网格
            if (gridLine.getState() != GridLineState.WAIT_BUY) {
                // 已成交的网格，更新追踪变量
                if (gridLine.getGridType() == GridType.SMALL) {
                    previousSmallBuyPrice = lastSmallBuyPrice;
                    lastSmallBuyPrice = gridLine.getActualBuyPrice() != null ?
                        gridLine.getActualBuyPrice() : gridLine.getBuyPrice();
                } else if (gridLine.getGridType() == GridType.MEDIUM) {
                    mediumCount++;
                    BigDecimal mediumPrice = gridLine.getActualBuyPrice() != null ?
                        gridLine.getActualBuyPrice() : gridLine.getBuyPrice();
                    lastMediumBuyPrice = mediumPrice;
                    if (mediumCount == 2) {
                        secondMediumBuyPrice = mediumPrice;
                    }
                }
                continue;
            }

            // 重新计算未成交网格的买入价和卖出价
            BigDecimal newBuyPrice;
            BigDecimal newSellPrice;

            if (gridLine.getGridType() == GridType.SMALL) {
                // 小网：基于上一个小网 × 0.95（固定比例递减），3位小数向下舍入
                newBuyPrice = lastSmallBuyPrice.multiply(decreaseFactor)
                    .setScale(3, RoundingMode.DOWN);
                // 🔥 小网的sellPrice = 上一个小网的buyPrice（阶梯回撤）
                // 卖出价四舍五入（卖得更贵）
                newSellPrice = lastSmallBuyPrice.setScale(3, RoundingMode.HALF_UP);

                previousSmallBuyPrice = lastSmallBuyPrice;
                lastSmallBuyPrice = newBuyPrice;

            } else if (gridLine.getGridType() == GridType.MEDIUM) {
                // 中网：继承最新小网的买入价
                newBuyPrice = lastSmallBuyPrice;
                // 中网的sellPrice = 锚点逻辑
                if (gridLine.getLevel() == 5) {
                    // 第1个中网（第5条）：卖回 basePrice
                    newSellPrice = strategy.getBasePrice().setScale(3, RoundingMode.HALF_UP);
                } else {
                    // 后续中网：卖回上一个中网的 buyPrice
                    newSellPrice = lastMediumBuyPrice.setScale(3, RoundingMode.HALF_UP);
                }

                mediumCount++;
                lastMediumBuyPrice = newBuyPrice;
                if (mediumCount == 2) {
                    secondMediumBuyPrice = newBuyPrice;
                }

            } else { // LARGE
                // 大网：继承最新小网的买入价
                newBuyPrice = lastSmallBuyPrice;

                // 大网的sellPrice = 特殊锚点规则
                if (gridLine.getLevel() == 10) {
                    // 第1个大网：卖回 basePrice
                    newSellPrice = strategy.getBasePrice().setScale(3, RoundingMode.HALF_UP);
                } else {
                    // 第2个大网（Level 19）：卖回第2个中网(Level 9)的买入价
                    newSellPrice = secondMediumBuyPrice.setScale(3, RoundingMode.HALF_UP);
                }
            }

            // 更新买入价和卖出价
            gridLine.setBuyPrice(newBuyPrice);
            gridLine.setSellPrice(newSellPrice);
            log.info("[RECALC] 更新网格 level={}, type={}, 新买入价={}, 新卖出价={}",
                gridLine.getLevel(), gridLine.getGridType(), newBuyPrice, newSellPrice);

            // 重新计算相关字段
            BigDecimal buyAmount = gridLine.getBuyAmount();
            BigDecimal buyQuantity = buyAmount.divide(newBuyPrice, 8, RoundingMode.DOWN);
            gridLine.setBuyQuantity(buyQuantity);

            // 卖出金额和收益也需要重新计算
            BigDecimal sellAmount = buyQuantity.multiply(gridLine.getSellPrice())
                .setScale(2, RoundingMode.DOWN);
            gridLine.setSellAmount(sellAmount);

            BigDecimal profit = sellAmount.subtract(buyAmount);
            gridLine.setProfit(profit);

            BigDecimal profitRate = profit.divide(buyAmount, 6, RoundingMode.HALF_UP);
            gridLine.setProfitRate(profitRate);
        }
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
