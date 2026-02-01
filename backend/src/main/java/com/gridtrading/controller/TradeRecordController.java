package com.gridtrading.controller;

import com.gridtrading.domain.Strategy;
import com.gridtrading.domain.TradeRecord;
import com.gridtrading.repository.StrategyRepository;
import com.gridtrading.repository.TradeRecordRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 成交记录 Controller
 */
@RestController
@RequestMapping("/api")
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
}
