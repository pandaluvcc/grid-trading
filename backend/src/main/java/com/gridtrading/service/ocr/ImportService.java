package com.gridtrading.service.ocr;

import com.gridtrading.controller.dto.BatchImportRequest;
import com.gridtrading.controller.dto.OcrMatchStatus;
import com.gridtrading.controller.dto.OcrTradeRecord;
import com.gridtrading.domain.GridLine;
import com.gridtrading.domain.GridLineState;
import com.gridtrading.domain.Strategy;
import com.gridtrading.domain.TradeRecord;
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

        Strategy strategy = strategyRepository.findById(request.getStrategyId())
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

                GridLine gridLine = gridLineRepository.findById(record.getMatchedGridLineId())
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

                updateGridLine(gridLine, record);
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

    private void updateGridLine(GridLine gridLine, OcrTradeRecord record) {
        switch (record.getType()) {
            case BUY -> {
                gridLine.setActualBuyPrice(record.getPrice());
                if (gridLine.getState() == GridLineState.WAIT_BUY) {
                    gridLine.setState(GridLineState.BOUGHT);
                }
            }
            case SELL -> {
                gridLine.setActualSellPrice(record.getPrice());
                if (gridLine.getState() == GridLineState.BOUGHT || gridLine.getState() == GridLineState.WAIT_SELL) {
                    gridLine.setState(GridLineState.WAIT_BUY);
                }
            }
        }
        gridLineRepository.save(gridLine);
    }
}

