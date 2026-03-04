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
     * 处理手动录入的交易（重构版 - 方案B）
     * <p>
     * 核心逻辑：
     * 1. 前端指定 gridLineId + type，后端不做自动匹配和判断
     * 2. 使用用户传入的实际价格、数量、手续费、交易时间
     * 3. 更新网格线的 actualBuyPrice/actualSellPrice
     * 4. 触发级联更新（如果价格改变了）
     *
     * @param strategyId 策略 ID
     * @param gridLineId 网格线 ID（前端指定）
     * @param type       交易类型（前端指定：BUY/SELL）
     * @param price      实际成交价格
     * @param quantity   实际成交数量
     * @param fee        手续费
     * @param tradeTime  交易时间
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
        // 加载策略（包含网格线）
        Strategy strategy = strategyRepository.findByIdWithGridLines(strategyId)
                .orElseThrow(() -> new IllegalArgumentException("策略不存在: " + strategyId));

        // 查找指定的网格线
        GridLine gridLine = strategy.getGridLines().stream()
                .filter(gl -> gl.getId().equals(gridLineId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("网格线不存在: " + gridLineId));

        // 更新最新价格
        strategy.setLastPrice(price);

        // 计算交易金额
        BigDecimal amount = price.multiply(quantity);

        // 创建交易记录
        TradeRecord tradeRecord = new TradeRecord();
        tradeRecord.setStrategy(strategy);
        tradeRecord.setGridLine(gridLine);
        tradeRecord.setType(type);
        tradeRecord.setPrice(price);
        tradeRecord.setQuantity(quantity);
        tradeRecord.setAmount(amount);
        tradeRecord.setFee(fee);
        tradeRecord.setTradeTime(tradeTime != null ? tradeTime : LocalDateTime.now());
        tradeRecordRepository.save(tradeRecord);

        // 执行买入或卖出的状态更新
        if (type == TradeType.BUY) {
            executeManualBuy(strategy, gridLine, price, quantity, amount);
        } else {
            executeManualSell(strategy, gridLine, price, quantity, amount);
        }

        // 保存策略和网格线
        gridLineRepository.save(gridLine);
        strategyRepository.save(strategy);
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

        // ✅ 修复：更新买入触发价（买入价 + 0.002，价格涨到此处触发买入）
        BigDecimal buyTriggerPrice = price.add(new BigDecimal("0.002"))
            .setScale(3, RoundingMode.HALF_UP);
        gridLine.setBuyTriggerPrice(buyTriggerPrice);

        // ✅ 新增：更新当前网格的sellPrice（基于阶梯回撤规则）
        updateCurrentGridSellPriceAfterBuy(strategy, gridLine);

        log.info("[MANUAL-BUY] gridLineId={}, level={}, price={}, quantity={}, buyCount={}, buyTriggerPrice={}",
                gridLine.getId(), gridLine.getLevel(), price, quantity, gridLine.getBuyCount(), buyTriggerPrice);

        // 重新计算后续网格的买入价（因为当前网格的买入价改变了）
        recalculateSubsequentGridsAfterManualBuy(strategy, gridLine, price);

        // 更新策略资金和持仓
        strategy.setAvailableCash(strategy.getAvailableCash().subtract(amount));
        strategy.setInvestedAmount(strategy.getInvestedAmount().add(amount));
        strategy.setPosition(strategy.getPosition().add(quantity));
    }

    /**
     * 手动买入后，更新当前网格的sellPrice（基于阶梯回撤规则）
     * ✅ 优化：基于实际买入价计算卖出价，确保收益率
     */
    /**
     * 更新当前网格买入后的sellPrice（包含收益率保护）
     * ✅ public方法：供OCR导入服务等外部调用
     */
    public void updateCurrentGridSellPriceAfterBuy(Strategy strategy, GridLine currentGridLine) {
        GridType gridType = currentGridLine.getGridType();
        int currentLevel = currentGridLine.getLevel();
        BigDecimal actualBuyPrice = currentGridLine.getActualBuyPrice();
        List<GridLine> allGridLines = strategy.getGridLines();
        allGridLines.sort((a, b) -> a.getLevel().compareTo(b.getLevel()));

        BigDecimal newSellPrice = null;
        BigDecimal targetProfitRate = null; // 目标收益率

        if (gridType == GridType.SMALL) {
            targetProfitRate = new BigDecimal("0.05"); // 5%
            BigDecimal minSellPrice = actualBuyPrice.multiply(new BigDecimal("1.05")); // 成本+5%

            if (currentLevel == 1) {
                // 第1网：直接使用成本+5%
                newSellPrice = minSellPrice;
            } else {
                // 后续小网：找上一小网的有效买入价
                BigDecimal targetSellPrice = null;
                for (int i = allGridLines.size() - 1; i >= 0; i--) {
                    GridLine gl = allGridLines.get(i);
                    if (gl.getLevel() < currentLevel && gl.getGridType() == GridType.SMALL) {
                        targetSellPrice = gl.getActualBuyPrice() != null ?
                            gl.getActualBuyPrice() : gl.getBuyPrice();
                        break;
                    }
                }
                // ✅ 收益最大化：MAX(成本+5%, 上一小网buyPrice)
                newSellPrice = (targetSellPrice != null && targetSellPrice.compareTo(minSellPrice) > 0) ?
                    targetSellPrice : minSellPrice;
            }
            newSellPrice = newSellPrice.setScale(3, RoundingMode.HALF_UP);

        } else if (gridType == GridType.MEDIUM) {
            targetProfitRate = new BigDecimal("0.15"); // 15%
            BigDecimal minSellPrice = actualBuyPrice.multiply(new BigDecimal("1.15")); // 成本+15%
            BigDecimal targetSellPrice;

            if (currentLevel == 5) {
                // 第1个中网：卖回 basePrice
                targetSellPrice = strategy.getBasePrice();
            } else {
                // 后续中网：卖回上一个中网的有效buyPrice
                targetSellPrice = null;
                for (int i = allGridLines.size() - 1; i >= 0; i--) {
                    GridLine gl = allGridLines.get(i);
                    if (gl.getLevel() < currentLevel && gl.getGridType() == GridType.MEDIUM) {
                        targetSellPrice = gl.getActualBuyPrice() != null ?
                            gl.getActualBuyPrice() : gl.getBuyPrice();
                        break;
                    }
                }
                if (targetSellPrice == null) {
                    targetSellPrice = strategy.getBasePrice();
                }
            }
            // ✅ 收益最大化：MAX(成本+15%, 锚点/上一中网)
            newSellPrice = minSellPrice.compareTo(targetSellPrice) > 0 ? minSellPrice : targetSellPrice;
            newSellPrice = newSellPrice.setScale(3, RoundingMode.HALF_UP);

        } else { // LARGE
            targetProfitRate = new BigDecimal("0.30"); // 30%
            BigDecimal minSellPrice = actualBuyPrice.multiply(new BigDecimal("1.30")); // 成本+30%
            BigDecimal targetSellPrice;

            if (currentLevel == 10) {
                // 第1个大网：卖回 basePrice
                targetSellPrice = strategy.getBasePrice();
            } else {
                // 第2个大网：卖回第9网的有效buyPrice
                targetSellPrice = null;
                int mediumCount = 0;
                for (GridLine gl : allGridLines) {
                    if (gl.getGridType() == GridType.MEDIUM) {
                        mediumCount++;
                        if (mediumCount == 2) {
                            targetSellPrice = gl.getActualBuyPrice() != null ?
                                gl.getActualBuyPrice() : gl.getBuyPrice();
                            break;
                        }
                    }
                }
                if (targetSellPrice == null) {
                    targetSellPrice = strategy.getBasePrice();
                }
            }
            // ✅ 收益最大化：MAX(成本+30%, 锚点/第9网)
            newSellPrice = minSellPrice.compareTo(targetSellPrice) > 0 ? minSellPrice : targetSellPrice;
            newSellPrice = newSellPrice.setScale(3, RoundingMode.HALF_UP);
        }

        if (newSellPrice != null) {
            currentGridLine.setSellPrice(newSellPrice);
            // ✅ 修正：卖出触发价 = 卖出价 - 0.002（价格跌到此处触发卖出）
            BigDecimal sellTriggerPrice = newSellPrice.subtract(new BigDecimal("0.002"))
                .setScale(3, RoundingMode.HALF_UP);
            currentGridLine.setSellTriggerPrice(sellTriggerPrice);

            // 计算实际收益率用于日志
            BigDecimal actualProfitRate = newSellPrice.subtract(actualBuyPrice)
                .divide(actualBuyPrice, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

            log.info("[UPDATE-SELL-PRICE] level={} sellPrice={}, 收益率={}% (目标{}%)",
                currentGridLine.getLevel(), newSellPrice,
                actualProfitRate.setScale(1, RoundingMode.HALF_UP),
                targetProfitRate.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP));
        }
    }

    /**
     * 执行手动卖出的状态更新
     */
    private void executeManualSell(Strategy strategy, GridLine gridLine, BigDecimal price, BigDecimal quantity, BigDecimal amount) {
        // 更新网格线状态（循环网格）
        gridLine.setState(GridLineState.WAIT_BUY);
        gridLine.setSellCount(gridLine.getSellCount() + 1);
        gridLine.setActualSellPrice(price);  // 记录实际卖出价

        // ✅ 修复：同步更新sellPrice（保持一致性）
        gridLine.setSellPrice(price);

        // ✅ 修正：更新卖出触发价（卖出价 - 0.002，价格跌到此处触发卖出）
        BigDecimal sellTriggerPrice = price.subtract(new BigDecimal("0.002"))
            .setScale(3, RoundingMode.HALF_UP);
        gridLine.setSellTriggerPrice(sellTriggerPrice);

        log.info("[MANUAL-SELL] gridLineId={}, level={}, price={}, quantity={}, sellCount={}, sellTriggerPrice={}",
                gridLine.getId(), gridLine.getLevel(), price, quantity, gridLine.getSellCount(), sellTriggerPrice);

        // 计算收益（卖出金额 - 买入金额）
        BigDecimal profit = amount.subtract(gridLine.getBuyAmount());

        // 更新策略资金和持仓
        strategy.setAvailableCash(strategy.getAvailableCash().add(amount));
        strategy.setInvestedAmount(strategy.getInvestedAmount().subtract(gridLine.getBuyAmount()));
        strategy.setPosition(strategy.getPosition().subtract(quantity));
        strategy.setRealizedProfit(strategy.getRealizedProfit().add(profit));
    }

    /**
     * 统一的网格价格重算方法（核心方法）
     * <p>
     * 适用场景：
     * 1. 手动交易后级联更新
     * 2. OCR批量导入后更新
     * 3. 计划阶段调整价格
     * <p>
     * 核心规则：
     * - 小网 buyPrice：上一小网 × 0.95（向下舍入3位）
     * - 中网/大网 buyPrice：继承最近小网
     * - sellPrice：MAX(buyPrice × 目标收益率, 回撤参考价)，四舍五入3位
     * - 收益率：小网5%，中网15%，大网30%
     * <p>
     * 状态保护：
     * - WAIT_BUY：更新 buyPrice + sellPrice + 触发价
     * - BOUGHT：只更新 sellPrice + sellTriggerPrice（保持历史buyPrice）
     * - 已交易（actualBuyPrice != null）：跳过更新，但更新追踪变量
     * <p>
     * ✅ 此方法可被外部调用（如OCR导入服务、Controller）
     *
     * @param strategy 策略
     * @param currentGridLine 当前触发更新的网格线
     * @param actualBuyPrice 实际买入价（如果是买入交易）
     */
    public void recalculateSubsequentGridsAfterManualBuy(Strategy strategy, GridLine currentGridLine, BigDecimal actualBuyPrice) {
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

        // ✅ 新增：追踪小网的有效买入价（用于小网sellPrice，优先actual）
        BigDecimal lastSmallEffectiveBuyPrice = actualBuyPrice;

        // 追踪中网买入价（用于中网卖出锚点）
        BigDecimal lastMediumBuyPrice = null;
        BigDecimal secondMediumBuyPrice = null;
        int mediumCount = 0;

        // ✅ 修复：先找到当前小网及其之前的网格信息（包含currentLevel本身）
        for (GridLine gl : allGridLines) {
            if (gl.getLevel() > currentLevel) {
                break;
            }

            // 更新小网的有效买入价追踪
            if (gl.getGridType() == GridType.SMALL) {
                // ✅ 优先使用actualBuyPrice（真实价格）
                lastSmallEffectiveBuyPrice = gl.getActualBuyPrice() != null ?
                    gl.getActualBuyPrice() : gl.getBuyPrice();
            }

            // 更新中网信息
            if (gl.getGridType() == GridType.MEDIUM) {
                mediumCount++;
                // ✅ 优先使用actualBuyPrice（真实价格）
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

            // ✅ 修复：区分状态保护策略
            GridLineState currentState = gridLine.getState();
            boolean hasActualBuyPrice = gridLine.getActualBuyPrice() != null;

            // 如果网格已交易（actualBuyPrice不为null），更新追踪变量但跳过价格修改
            if (hasActualBuyPrice) {
                // 已成交的网格，更新追踪变量供后续计算使用
                if (gridLine.getGridType() == GridType.SMALL) {
                    lastSmallBuyPrice = gridLine.getActualBuyPrice();
                    // ✅ 更新小网的有效买入价（用于sellPrice）
                    lastSmallEffectiveBuyPrice = gridLine.getActualBuyPrice();
                } else if (gridLine.getGridType() == GridType.MEDIUM) {
                    mediumCount++;
                    lastMediumBuyPrice = gridLine.getActualBuyPrice();
                    if (mediumCount == 2) {
                        secondMediumBuyPrice = gridLine.getActualBuyPrice();
                    }
                }
                log.info("[RECALC-SKIP] 网格 level={} 已交易(actualBuyPrice={}), 跳过价格更新",
                    gridLine.getLevel(), gridLine.getActualBuyPrice());
                continue;
            }

            // 重新计算未成交网格的买入价和卖出价
            BigDecimal newBuyPrice = null;
            BigDecimal newSellPrice = null;

            if (gridLine.getGridType() == GridType.SMALL) {
                // 小网：基于上一个小网 × 0.95（固定比例递减），3位小数向下舍入
                newBuyPrice = lastSmallBuyPrice.multiply(decreaseFactor)
                    .setScale(3, RoundingMode.DOWN);

                // ✅ 小网 sellPrice = MAX(当前buyPrice × 1.05, 上一小网的有效buyPrice)
                BigDecimal minSellPrice = newBuyPrice.multiply(new BigDecimal("1.05")); // 5%收益保护
                BigDecimal targetSellPrice = lastSmallEffectiveBuyPrice; // 卖回上一小网
                newSellPrice = minSellPrice.compareTo(targetSellPrice) > 0 ? minSellPrice : targetSellPrice;
                newSellPrice = newSellPrice.setScale(3, RoundingMode.HALF_UP); // 四舍五入

                // ✅ 更新追踪变量
                lastSmallBuyPrice = newBuyPrice;
                lastSmallEffectiveBuyPrice = newBuyPrice; // 新计算的网格，有效价格就是计划价格

            } else if (gridLine.getGridType() == GridType.MEDIUM) {
                // 中网：继承最新小网的买入价
                newBuyPrice = lastSmallBuyPrice;

                // ✅ 中网 sellPrice = MAX(当前buyPrice × 1.15, 锚点/上一中网buyPrice)
                BigDecimal minSellPrice = newBuyPrice.multiply(new BigDecimal("1.15")); // 15%收益保护
                BigDecimal targetSellPrice;
                if (gridLine.getLevel() == 5) {
                    // 第1个中网（第5条）：卖回 basePrice
                    targetSellPrice = strategy.getBasePrice();
                } else {
                    // 后续中网：卖回上一个中网的有效buyPrice
                    targetSellPrice = lastMediumBuyPrice != null ? lastMediumBuyPrice : strategy.getBasePrice();
                }
                newSellPrice = minSellPrice.compareTo(targetSellPrice) > 0 ? minSellPrice : targetSellPrice;
                newSellPrice = newSellPrice.setScale(3, RoundingMode.HALF_UP); // 四舍五入

                mediumCount++;
                lastMediumBuyPrice = newBuyPrice;
                if (mediumCount == 2) {
                    secondMediumBuyPrice = newBuyPrice;
                }

            } else { // LARGE
                // 大网：继承最新小网的买入价
                newBuyPrice = lastSmallBuyPrice;

                // ✅ 大网 sellPrice = MAX(当前buyPrice × 1.30, 锚点/第9网buyPrice)
                BigDecimal minSellPrice = newBuyPrice.multiply(new BigDecimal("1.30")); // 30%收益保护
                BigDecimal targetSellPrice;
                if (gridLine.getLevel() == 10) {
                    // 第1个大网：卖回 basePrice
                    targetSellPrice = strategy.getBasePrice();
                } else {
                    // 第2个大网（Level 19）：卖回第9网的有效buyPrice
                    targetSellPrice = secondMediumBuyPrice != null ? secondMediumBuyPrice : strategy.getBasePrice();
                }
                newSellPrice = minSellPrice.compareTo(targetSellPrice) > 0 ? minSellPrice : targetSellPrice;
                newSellPrice = newSellPrice.setScale(3, RoundingMode.HALF_UP); // 四舍五入
            }

            // ✅ 修复：根据网格状态决定更新哪些字段
            if (currentState == GridLineState.WAIT_BUY) {
                // 未成交网格：可以更新 buyPrice 和 sellPrice
                gridLine.setBuyPrice(newBuyPrice);
                gridLine.setSellPrice(newSellPrice);
                log.info("[RECALC] 更新网格 level={}, type={}, state=WAIT_BUY, 新买入价={}, 新卖出价={}",
                    gridLine.getLevel(), gridLine.getGridType(), newBuyPrice, newSellPrice);

                // ✅ 同步更新触发价
                // buyTriggerPrice = buyPrice + 0.002（价格涨到此处触发买入）
                // sellTriggerPrice = sellPrice - 0.002（价格跌到此处触发卖出）
                BigDecimal newBuyTriggerPrice = newBuyPrice.add(new BigDecimal("0.002"))
                    .setScale(3, RoundingMode.HALF_UP);
                BigDecimal newSellTriggerPrice = newSellPrice.subtract(new BigDecimal("0.002"))
                    .setScale(3, RoundingMode.HALF_UP);
                gridLine.setBuyTriggerPrice(newBuyTriggerPrice);
                gridLine.setSellTriggerPrice(newSellTriggerPrice);

            } else if (currentState == GridLineState.BOUGHT) {
                // ✅ 新增：已买入网格：只更新 sellPrice，不更新 buyPrice（保持历史买入计划）
                gridLine.setSellPrice(newSellPrice);
                log.info("[RECALC] 更新网格 level={}, type={}, state=BOUGHT, 保持买入价={}, 新卖出价={}",
                    gridLine.getLevel(), gridLine.getGridType(), gridLine.getBuyPrice(), newSellPrice);

                // ✅ 只更新卖出触发价：sellTriggerPrice = sellPrice - 0.002（价格跌到此处触发卖出）
                BigDecimal newSellTriggerPrice = newSellPrice.subtract(new BigDecimal("0.002"))
                    .setScale(3, RoundingMode.HALF_UP);
                gridLine.setSellTriggerPrice(newSellTriggerPrice);

            } else {
                // SOLD 或其他状态：不更新任何建议价格
                log.info("[RECALC-SKIP] 网格 level={}, state={}, 不更新价格",
                    gridLine.getLevel(), currentState);
                continue;
            }

            // 重新计算相关字段（仅针对 WAIT_BUY 状态）
            if (currentState == GridLineState.WAIT_BUY) {
                BigDecimal buyAmount = gridLine.getBuyAmount();
                BigDecimal buyQuantity = buyAmount.divide(newBuyPrice, 8, RoundingMode.DOWN);
                gridLine.setBuyQuantity(buyQuantity);

                // ✅ 修正：sellAmount = 数量 × 卖出价（不扣除手续费，手续费在实际成交时才考虑）
                BigDecimal sellAmount = buyQuantity.multiply(newSellPrice)
                    .setScale(2, RoundingMode.DOWN);
                gridLine.setSellAmount(sellAmount);

                BigDecimal profit = sellAmount.subtract(buyAmount);
                gridLine.setProfit(profit);

                BigDecimal profitRate = profit.divide(buyAmount, 6, RoundingMode.HALF_UP);
                gridLine.setProfitRate(profitRate);
            } else if (currentState == GridLineState.BOUGHT) {
                // ✅ 新增：BOUGHT状态只重新计算卖出相关字段
                BigDecimal buyQuantity = gridLine.getBuyQuantity(); // 使用已有的买入数量
                if (buyQuantity != null) {
                    // ✅ 修正：sellAmount = 数量 × 卖出价（不扣除手续费）
                    BigDecimal sellAmount = buyQuantity.multiply(newSellPrice)
                        .setScale(2, RoundingMode.DOWN);
                    gridLine.setSellAmount(sellAmount);

                    BigDecimal buyAmount = gridLine.getBuyAmount();
                    BigDecimal profit = sellAmount.subtract(buyAmount);
                    gridLine.setProfit(profit);

                    BigDecimal profitRate = profit.divide(buyAmount, 6, RoundingMode.HALF_UP);
                    gridLine.setProfitRate(profitRate);
                }
            }
        }
    }


}
