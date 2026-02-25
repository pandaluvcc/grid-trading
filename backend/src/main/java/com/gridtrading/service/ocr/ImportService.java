package com.gridtrading.service.ocr;

import com.gridtrading.controller.dto.BatchImportRequest;
import com.gridtrading.controller.dto.OcrMatchStatus;
import com.gridtrading.controller.dto.OcrTradeRecord;
import com.gridtrading.domain.GridLine;
import com.gridtrading.domain.GridLineState;
import com.gridtrading.domain.GridType;
import com.gridtrading.domain.Strategy;
import com.gridtrading.domain.TradeRecord;
import com.gridtrading.repository.GridLineRepository;
import com.gridtrading.repository.StrategyRepository;
import com.gridtrading.repository.TradeRecordRepository;
import com.gridtrading.service.grid.GridPlanGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    public ImportService(StrategyRepository strategyRepository,
                         GridLineRepository gridLineRepository,
                         TradeRecordRepository tradeRecordRepository) {
        this.strategyRepository = strategyRepository;
        this.gridLineRepository = gridLineRepository;
        this.tradeRecordRepository = tradeRecordRepository;
    }

    @Transactional
    public Map<String, Object> batchImport(BatchImportRequest request) {
        if (request == null || request.getStrategyId() == null) {
            throw new IllegalArgumentException("strategyId is required");
        }

        Strategy strategy = strategyRepository.findByIdWithGridLines(request.getStrategyId())
                .orElseThrow(() -> new IllegalArgumentException("策略不存在"));

        List<OcrTradeRecord> records = request.getRecords();
        int total = records != null ? records.size() : 0;
        int imported = 0;
        int skipped = 0;

        if (records != null) {
            for (OcrTradeRecord record : records) {
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

                updateGridLine(strategy, gridLine, record);
                imported++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("strategyId", strategy.getId());
        return result;
    }

    private void updateGridLine(Strategy strategy, GridLine gridLine, OcrTradeRecord record) {
        switch (record.getType()) {
            case BUY -> {
                gridLine.setActualBuyPrice(record.getPrice());
                if (gridLine.getState() == GridLineState.WAIT_BUY) {
                    gridLine.setState(GridLineState.BOUGHT);
                }
                recalculateSubsequentGrids(strategy, gridLine);
            }
            case SELL -> {
                gridLine.setActualSellPrice(record.getPrice());
                if (gridLine.getState() == GridLineState.BOUGHT || gridLine.getState() == GridLineState.WAIT_SELL) {
                    gridLine.setState(GridLineState.WAIT_BUY);
                }
            }
        }
        strategyRepository.save(strategy);
    }

    private void recalculateSubsequentGrids(Strategy strategy, GridLine fromGridLine) {
        if (strategy == null || fromGridLine == null) {
            return;
        }

        BigDecimal basePrice = strategy.getBasePrice();
        if (basePrice == null) {
            return;
        }

        BigDecimal smallStep = basePrice.multiply(GridPlanGenerator.SMALL_PERCENT);
        int startLevel = fromGridLine.getLevel() + 1;
        if (startLevel > GridPlanGenerator.TOTAL_GRID_COUNT) {
            return;
        }

        BigDecimal lastSmallBuyPrice = fromGridLine.getActualBuyPrice();
        BigDecimal lastMediumBuyPrice = null;
        BigDecimal secondMediumBuyPrice = null;
        int mediumCount = 0;

        List<GridLine> orderedGridLines = new ArrayList<>(strategy.getGridLines());
        orderedGridLines.sort((a, b) -> Integer.compare(a.getLevel(), b.getLevel()));

        for (GridLine gl : orderedGridLines) {
            if (gl.getLevel() < startLevel && gl.getGridType() == GridType.MEDIUM) {
                mediumCount++;
                BigDecimal mediumBuyPrice = gl.getActualBuyPrice() != null
                        ? gl.getActualBuyPrice()
                        : gl.getBuyPrice();
                lastMediumBuyPrice = mediumBuyPrice;
                if (mediumCount == 2) {
                    secondMediumBuyPrice = mediumBuyPrice;
                }
            }
        }

        for (GridLine gridLine : orderedGridLines) {
            if (gridLine.getLevel() < startLevel) {
                continue;
            }

            if (gridLine.getState() == GridLineState.BOUGHT || gridLine.getState() == GridLineState.SOLD) {
                if (gridLine.getGridType() == GridType.SMALL) {
                    lastSmallBuyPrice = gridLine.getActualBuyPrice() != null
                            ? gridLine.getActualBuyPrice()
                            : gridLine.getBuyPrice();
                }
                if (gridLine.getGridType() == GridType.MEDIUM) {
                    mediumCount++;
                    BigDecimal mediumBuyPrice = gridLine.getActualBuyPrice() != null
                            ? gridLine.getActualBuyPrice()
                            : gridLine.getBuyPrice();
                    lastMediumBuyPrice = mediumBuyPrice;
                    if (mediumCount == 2) {
                        secondMediumBuyPrice = mediumBuyPrice;
                    }
                }
                continue;
            }

            BigDecimal newBuyPrice;
            BigDecimal newSellPrice;

            if (gridLine.getGridType() == GridType.SMALL) {
                newBuyPrice = lastSmallBuyPrice.subtract(smallStep)
                        .setScale(8, RoundingMode.HALF_UP);
                newSellPrice = lastSmallBuyPrice;
                lastSmallBuyPrice = newBuyPrice;
            } else if (gridLine.getGridType() == GridType.MEDIUM) {
                mediumCount++;
                newBuyPrice = lastSmallBuyPrice;
                if (gridLine.getLevel() == 5) {
                    newSellPrice = basePrice;
                } else {
                    newSellPrice = lastMediumBuyPrice;
                }
                lastMediumBuyPrice = newBuyPrice;
                if (mediumCount == 2) {
                    secondMediumBuyPrice = newBuyPrice;
                }
            } else {
                newBuyPrice = lastSmallBuyPrice;
                if (gridLine.getLevel() == 10) {
                    newSellPrice = basePrice;
                } else {
                    newSellPrice = secondMediumBuyPrice;
                }
            }

            gridLine.setBuyPrice(newBuyPrice);
            gridLine.setSellPrice(newSellPrice);
            gridLine.setBuyTriggerPrice(newBuyPrice.add(new BigDecimal("0.02")));
            gridLine.setSellTriggerPrice(newSellPrice.subtract(new BigDecimal("0.02")));

            BigDecimal buyAmount = gridLine.getBuyAmount();
            BigDecimal buyQuantity = buyAmount.divide(newBuyPrice, 8, RoundingMode.DOWN);
            BigDecimal sellAmount = buyQuantity.multiply(newSellPrice)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal profit = sellAmount.subtract(buyAmount)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal profitRate = profit.divide(buyAmount, 6, RoundingMode.HALF_UP);

            gridLine.setBuyQuantity(buyQuantity);
            gridLine.setSellAmount(sellAmount);
            gridLine.setProfit(profit);
            gridLine.setProfitRate(profitRate);
        }
    }
}

