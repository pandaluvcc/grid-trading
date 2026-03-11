package com.gridtrading.service.ocr;

import com.gridtrading.controller.dto.BatchImportRequest;
import com.gridtrading.controller.dto.OcrMatchStatus;
import com.gridtrading.controller.dto.OcrTradeRecord;
import com.gridtrading.domain.*;
import com.gridtrading.engine.GridEngine;
import com.gridtrading.repository.GridLineRepository;
import com.gridtrading.repository.StrategyRepository;
import com.gridtrading.repository.TradeRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OCR批量导入服务
 */
@Service
public class ImportService {

    private final StrategyRepository strategyRepository;
    private final GridLineRepository gridLineRepository;
    private final TradeRecordRepository tradeRecordRepository;
    private final GridEngine gridEngine;

    public ImportService(StrategyRepository strategyRepository,
                         GridLineRepository gridLineRepository,
                         TradeRecordRepository tradeRecordRepository,
                         GridEngine gridEngine) {
        this.strategyRepository = strategyRepository;
        this.gridLineRepository = gridLineRepository;
        this.tradeRecordRepository = tradeRecordRepository;
        this.gridEngine = gridEngine;
    }

    @Transactional
    public Map<String, Object> batchImport(BatchImportRequest request) {
        System.out.println("\n========== OCR 批量导入开始 ==========");
        if (request == null || request.getStrategyId() == null) {
            throw new IllegalArgumentException("strategyId is required");
        }

        Strategy strategy = strategyRepository.findByIdWithGridLines(request.getStrategyId())
                .orElseThrow(() -> new IllegalArgumentException("策略不存在"));
        System.out.println("策略ID: " + strategy.getId() + ", 网格数: " + strategy.getGridLines().size());

        List<OcrTradeRecord> records = request.getRecords();
        int total = records != null ? records.size() : 0;
        int imported = 0;
        int skipped = 0;
        
        System.out.println("待导入记录数: " + total);

        // ✅ 修复：记录最小的买入level，用于最后统一触发级联更新
        Integer minBuyLevel = null;

        if (records != null) {
            int index = 0;
            for (OcrTradeRecord record : records) {
                index++;
                
                if (record == null) {
                    skipped++;
                    continue;
                }
                
                if (record.getMatchStatus() == OcrMatchStatus.DUPLICATE
                        || record.getMatchStatus() == OcrMatchStatus.INVALID) {
                    skipped++;
                    continue;
                }
                if (record.getMatchedGridLineId() == null) {
                    skipped++;
                    continue;
                }
                if (record.getType() == null || record.getPrice() == null) {
                    skipped++;
                    continue;
                }

                GridLine gridLine = strategy.getGridLines().stream()
                    .filter(gl -> gl.getId().equals(record.getMatchedGridLineId()))
                    .findFirst()
                    .orElse(null);
                if (gridLine == null || gridLine.getStrategy() == null
                        || !gridLine.getStrategy().getId().equals(strategy.getId())) {
                    skipped++;
                    continue;
                }

                BigDecimal price = record.getPrice();
                BigDecimal quantity = record.getQuantity();
                BigDecimal amount = record.getAmount();

                if (quantity == null && amount != null) {
                    quantity = amount.divide(price, 8, RoundingMode.DOWN);
                } else if (amount == null && quantity != null) {
                    amount = quantity.multiply(price).setScale(2, RoundingMode.DOWN);
                }

                if (quantity == null || amount == null) {
                    skipped++;
                    continue;
                }

                System.out.println("导入第" + index + "条: " + record.getType() + " 网格" + gridLine.getLevel() + " 价格" + price);
                
                TradeRecord entity = new TradeRecord();
                entity.setStrategy(strategy);
                entity.setGridLine(gridLine);
                entity.setType(record.getType());
                entity.setPrice(price);
                entity.setQuantity(quantity);
                entity.setAmount(amount);
                entity.setFee(record.getFee());
                entity.setTradeTime(record.getTradeTime() != null ? record.getTradeTime() : LocalDateTime.now());
                tradeRecordRepository.save(entity);

                // ✅ 修复：只更新网格状态和价格，不触发级联更新
                updateGridLineWithoutCascade(strategy, gridLine, record);

                // ✅ 记录买入交易的最小level
                if (record.getType().isBuy()) {
                    if (minBuyLevel == null || gridLine.getLevel() < minBuyLevel) {
                        minBuyLevel = gridLine.getLevel();
                        System.out.println("  -> 记录最小买入level: " + minBuyLevel);
                    }
                }

                imported++;
            }
        }

        // ✅ 修复：批量导入完成后，从最小的买入level开始统一触发级联更新
        if (minBuyLevel != null) {
            System.out.println("\n========== 触发全局级联更新（从level " + minBuyLevel + " 开始）==========");
            triggerCascadeUpdateFromLevel(strategy, minBuyLevel);
        } else {
            System.out.println("\n========== 未找到买入记录，跳过级联更新 ==========");
        }
        // ✅ 设置lastPrice为最新交易记录的价格
        BigDecimal latestPrice = findLatestTradePrice(records);
        if (latestPrice != null) {
            strategy.setLastPrice(latestPrice);
            strategyRepository.save(strategy);
            System.out.println("[OCR导入] 设置lastPrice: " + latestPrice);
        }
        System.out.println("========== OCR 批量导入结束: 成功" + imported + "条, 跳过" + skipped + "条 ==========\n");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("strategyId", strategy.getId());
        return result;
    }

    /**
     * 更新网格状态和价格（不触发级联更新）
     */
    private void updateGridLineWithoutCascade(Strategy strategy, GridLine gridLine, OcrTradeRecord record) {
        System.out.println("更新网格" + gridLine.getLevel() + ": " + record.getType() +
                         " (更新前 buyCount=" + gridLine.getBuyCount() + ", sellCount=" + gridLine.getSellCount() + ")");

        if (record.getType().isBuy()) {
            // 同时更新actualBuyPrice和buyPrice
            gridLine.setActualBuyPrice(record.getPrice());
            gridLine.setBuyPrice(record.getPrice());

            // 更新买入触发价
            BigDecimal buyTriggerPrice = record.getPrice().add(new BigDecimal("0.002"))
                .setScale(3, RoundingMode.DOWN);
            gridLine.setBuyTriggerPrice(buyTriggerPrice);

            Integer oldBuyCount = gridLine.getBuyCount();
            gridLine.setBuyCount(oldBuyCount + 1);
            System.out.println("  -> buyCount: " + oldBuyCount + " → " + gridLine.getBuyCount());
            System.out.println("  -> buyTriggerPrice: " + buyTriggerPrice);

            // 只要买入次数 > 卖出次数，就保持BOUGHT状态
            if (gridLine.getBuyCount() > gridLine.getSellCount()) {
                gridLine.setState(GridLineState.BOUGHT);
            }
        } else if (record.getType() == TradeType.SELL) {
            gridLine.setActualSellPrice(record.getPrice());
            gridLine.setSellPrice(record.getPrice());

            // 更新卖出触发价
            BigDecimal sellTriggerPrice = record.getPrice().subtract(new BigDecimal("0.002"))
                .setScale(3, RoundingMode.HALF_UP);
            gridLine.setSellTriggerPrice(sellTriggerPrice);

            Integer oldSellCount = gridLine.getSellCount();
            gridLine.setSellCount(oldSellCount + 1);
            System.out.println("  -> sellCount: " + oldSellCount + " → " + gridLine.getSellCount());
            System.out.println("  -> sellTriggerPrice: " + sellTriggerPrice);

            // 卖出后判断是否还有持仓：买入次数 > 卖出次数 → 保持BOUGHT，否则改为WAIT_BUY
            if (gridLine.getBuyCount() > gridLine.getSellCount()) {
                gridLine.setState(GridLineState.BOUGHT);
            } else {
                gridLine.setState(GridLineState.WAIT_BUY);
            }
        }

        strategyRepository.save(strategy);
    }

    /**
     * 从指定level开始触发级联更新
     * 1. 先更新所有已导入网格的sellPrice（基于上一网格的actualBuyPrice）
     * 2. 然后从最后一个已导入网格触发级联更新（更新未导入的网格）
     */
    private void triggerCascadeUpdateFromLevel(Strategy strategy, Integer startLevel) {
        List<GridLine> allGridLines = strategy.getGridLines();

        // 按level排序
        allGridLines.sort((a, b) -> Integer.compare(a.getLevel(), b.getLevel()));

        System.out.println("  -> 步骤1: 更新已导入网格的sellPrice");

        // 先更新所有已导入网格的sellPrice
        GridLine lastImportedGrid = null;
        for (GridLine gridLine : allGridLines) {
            if (gridLine.getActualBuyPrice() != null) {
                // 这是已导入的网格，更新其sellPrice
                BigDecimal newSellPrice = calculateSellPriceForGrid(strategy, gridLine, allGridLines);
                if (newSellPrice != null) {
                    gridLine.setSellPrice(newSellPrice);
                    BigDecimal sellTriggerPrice = newSellPrice.subtract(new BigDecimal("0.002"))
                        .setScale(3, RoundingMode.HALF_UP);
                    gridLine.setSellTriggerPrice(sellTriggerPrice);
                    System.out.println("    更新level " + gridLine.getLevel() + " sellPrice=" + newSellPrice);
                }
                lastImportedGrid = gridLine;
            }
        }

        strategyRepository.save(strategy);

        System.out.println("  -> 步骤2: 触发级联更新（更新未导入的网格）");

        // 从最后一个已导入的小网触发级联更新
        if (lastImportedGrid != null
            && lastImportedGrid.getGridType() == com.gridtrading.domain.GridType.SMALL) {
            System.out.println("  -> 从level " + lastImportedGrid.getLevel() + " 触发全局级联更新");
            gridEngine.recalculateSubsequentGridsAfterManualBuy(
                strategy,
                lastImportedGrid,
                lastImportedGrid.getActualBuyPrice()
            );
            strategyRepository.save(strategy);
            System.out.println("  -> 全局级联更新完成");
        } else {
            System.out.println("  -> 未找到需要级联更新的网格");
        }
    }

    /**
     * 计算单个网格的sellPrice（基于阶梯回撤规则）
     */
    private BigDecimal calculateSellPriceForGrid(Strategy strategy, GridLine gridLine, List<GridLine> allGridLines) {
        com.gridtrading.domain.GridType gridType = gridLine.getGridType();
        int currentLevel = gridLine.getLevel();

        if (gridType == com.gridtrading.domain.GridType.SMALL) {
            if (currentLevel == 1) {
                // 第1网：sellPrice = buyPrice × 1.05
                return gridLine.getBuyPrice().multiply(new BigDecimal("1.05"))
                    .setScale(3, RoundingMode.HALF_UP);
            } else {
                // 后续小网：sellPrice = 上一小网的buyPrice（优先actualBuyPrice）
                GridLine prevSmallGrid = null;
                for (int i = allGridLines.size() - 1; i >= 0; i--) {
                    GridLine gl = allGridLines.get(i);
                    if (gl.getLevel() < currentLevel && gl.getGridType() == com.gridtrading.domain.GridType.SMALL) {
                        prevSmallGrid = gl;
                        break;
                    }
                }
                if (prevSmallGrid != null) {
                    BigDecimal prevBuyPrice = prevSmallGrid.getActualBuyPrice() != null ?
                        prevSmallGrid.getActualBuyPrice() : prevSmallGrid.getBuyPrice();
                    return prevBuyPrice.setScale(3, RoundingMode.HALF_UP);
                }
            }
        } else if (gridType == com.gridtrading.domain.GridType.MEDIUM) {
            // 中网：卖回锚点
            if (currentLevel == 5) {
                // 第1个中网：卖回basePrice
                return strategy.getBasePrice().setScale(3, RoundingMode.HALF_UP);
            } else {
                // 后续中网：卖回上一个中网的buyPrice（优先actualBuyPrice）
                GridLine prevMediumGrid = null;
                for (int i = allGridLines.size() - 1; i >= 0; i--) {
                    GridLine gl = allGridLines.get(i);
                    if (gl.getLevel() < currentLevel && gl.getGridType() == com.gridtrading.domain.GridType.MEDIUM) {
                        prevMediumGrid = gl;
                        break;
                    }
                }
                if (prevMediumGrid != null) {
                    BigDecimal prevBuyPrice = prevMediumGrid.getActualBuyPrice() != null ?
                        prevMediumGrid.getActualBuyPrice() : prevMediumGrid.getBuyPrice();
                    return prevBuyPrice.setScale(3, RoundingMode.HALF_UP);
                }
                return strategy.getBasePrice().setScale(3, RoundingMode.HALF_UP);
            }
        } else { // LARGE
            // 大网：特殊锚点
            if (currentLevel == 10) {
                return strategy.getBasePrice().setScale(3, RoundingMode.HALF_UP);
            } else {
                // 第2个大网：卖回第2个中网的buyPrice
                for (GridLine gl : allGridLines) {
                    if (gl.getLevel() == 9 && gl.getGridType() == com.gridtrading.domain.GridType.MEDIUM) {
                        BigDecimal buyPrice = gl.getActualBuyPrice() != null ?
                            gl.getActualBuyPrice() : gl.getBuyPrice();
                        return buyPrice.setScale(3, RoundingMode.HALF_UP);
                    }
                }
                return strategy.getBasePrice().setScale(3, RoundingMode.HALF_UP);
            }
        }

        return null;
    }

    /**
     * 从交易记录中找出最新的价格（按交易时间排序）
     */
    private BigDecimal findLatestTradePrice(List<OcrTradeRecord> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        
        return records.stream()
            .filter(r -> r != null && r.getPrice() != null && r.getTradeTime() != null)
            .max((a, b) -> a.getTradeTime().compareTo(b.getTradeTime()))
            .map(OcrTradeRecord::getPrice)
            .orElse(records.stream()
                .filter(r -> r != null && r.getPrice() != null)
                .map(OcrTradeRecord::getPrice)
                .findFirst()
                .orElse(null));
    }
}

