package com.gridtrading.controller;

import com.gridtrading.domain.Strategy;
import com.gridtrading.domain.TradeRecord;
import com.gridtrading.repository.StrategyRepository;
import com.gridtrading.repository.TradeRecordRepository;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 成交记录 Controller
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TradeRecordController {

    private final TradeRecordRepository tradeRecordRepository;
    private final StrategyRepository strategyRepository;

    public TradeRecordController(TradeRecordRepository tradeRecordRepository,
                                  StrategyRepository strategyRepository) {
        this.tradeRecordRepository = tradeRecordRepository;
        this.strategyRepository = strategyRepository;
    }

    /**
     * 获取策略的成交记录
     * GET /api/strategies/{id}/trades
     */
    @GetMapping("/strategies/{id}/trades")
    public List<TradeRecord> getStrategyTrades(@PathVariable Long id) {
        Strategy strategy = strategyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("策略不存在"));
        return tradeRecordRepository.findByStrategyOrderByTradeTimeDesc(strategy);
    }

    /**
     * 更新成交记录的手续费
     * PUT /api/trades/{id}/fee
     */
    @PutMapping("/trades/{id}/fee")
    public TradeRecord updateTradeFee(@PathVariable Long id, @RequestBody Map<String, BigDecimal> request) {
        BigDecimal fee = request.get("fee");
        if (fee == null || fee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("手续费必须大于等于0");
        }
        
        TradeRecord record = tradeRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("成交记录不存在"));
        
        record.setFee(fee);
        return tradeRecordRepository.save(record);
    }

    /**
     * 获取策略的累计手续费
     * GET /api/strategies/{id}/total-fee
     */
    @GetMapping("/strategies/{id}/total-fee")
    public Map<String, BigDecimal> getStrategyTotalFee(@PathVariable Long id) {
        Strategy strategy = strategyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("策略不存在"));
        
        List<TradeRecord> records = tradeRecordRepository.findByStrategyOrderByTradeTimeDesc(strategy);
        BigDecimal totalFee = records.stream()
                .map(r -> r.getFee() != null ? r.getFee() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return Map.of("totalFee", totalFee);
    }
}
