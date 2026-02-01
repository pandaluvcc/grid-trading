package com.gridtrading.controller;

import com.gridtrading.controller.dto.*;
import com.gridtrading.domain.GridLine;
import com.gridtrading.domain.GridLineState;
import com.gridtrading.domain.Strategy;
import com.gridtrading.domain.StrategyStatus;
import com.gridtrading.domain.TradeRecord;
import com.gridtrading.engine.GridEngine;
import com.gridtrading.repository.StrategyRepository;
import com.gridtrading.repository.TradeRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 策略管理 Controller
 */
@RestController
@RequestMapping("/api/strategies")
@CrossOrigin(origins = "*")
public class StrategyController {

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private GridEngine gridEngine;

    @Autowired
    private TradeRecordRepository tradeRecordRepository;

    /**
     * 获取所有策略列表
     */
    @GetMapping
    public List<StrategyResponse> getAllStrategies() {
        return strategyRepository.findAll().stream()
                .map(StrategyResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 创建新策略
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StrategyResponse createStrategy(@RequestBody CreateStrategyRequest request) {
        // 创建策略实体
        Strategy strategy = new Strategy();
        strategy.setName(request.getName());
        strategy.setSymbol("DEFAULT"); // 默认标的代码
        strategy.setBasePrice(request.getBasePrice());
        strategy.setGridPercent(request.getGridSpacing());
        
        // 将 gridCount 平均分配给向上和向下网格
        int halfCount = request.getGridCount() / 2;
        strategy.setGridCountDown(halfCount);
        strategy.setGridCountUp(request.getGridCount() - halfCount);
        
        strategy.setAmountPerGrid(request.getAmountPerGrid());
        
        // 计算最大投入资金
        BigDecimal maxCapital = request.getAmountPerGrid()
                .multiply(BigDecimal.valueOf(request.getGridCount()));
        strategy.setMaxCapital(maxCapital);
        strategy.setAvailableCash(maxCapital);
        
        // 设置初始状态为 RUNNING（运行中）
        strategy.setStatus(StrategyStatus.RUNNING);

        // 初始化网格线
        initGridLines(strategy);

        // 保存策略（级联保存 GridLine）
        Strategy savedStrategy = strategyRepository.save(strategy);
        
        return StrategyResponse.fromEntity(savedStrategy);
    }

    /**
     * 初始化网格线
     *
     * @param strategy 策略实体
     */
    private void initGridLines(Strategy strategy) {
        List<GridLine> gridLines = new ArrayList<>();

        BigDecimal basePrice = strategy.getBasePrice();
        BigDecimal gridPercent = strategy.getGridPercent();

        // 生成向下网格（level: -1, -2, -3, ...）
        for (int i = 1; i <= strategy.getGridCountDown(); i++) {
            int level = -i;

            // buyPrice = basePrice × (1 - gridPercent × |level|)
            BigDecimal buyPrice = basePrice.multiply(
                BigDecimal.ONE.subtract(
                    gridPercent.multiply(BigDecimal.valueOf(Math.abs(level)))
                )
            ).setScale(8, RoundingMode.HALF_UP);

            // sellPrice = buyPrice × (1 + gridPercent)
            BigDecimal sellPrice = buyPrice.multiply(
                BigDecimal.ONE.add(gridPercent)
            ).setScale(8, RoundingMode.HALF_UP);

            GridLine gridLine = new GridLine();
            gridLine.setStrategy(strategy);
            gridLine.setLevel(level);
            gridLine.setBuyPrice(buyPrice);
            gridLine.setSellPrice(sellPrice);
            gridLine.setState(GridLineState.WAIT_BUY);

            gridLines.add(gridLine);
        }

        // 生成向上网格（level: 1, 2, 3, ...）
        for (int i = 1; i <= strategy.getGridCountUp(); i++) {
            int level = i;

            // buyPrice = basePrice × (1 - gridPercent × level)
            BigDecimal buyPrice = basePrice.multiply(
                BigDecimal.ONE.subtract(
                    gridPercent.multiply(BigDecimal.valueOf(level))
                )
            ).setScale(8, RoundingMode.HALF_UP);

            // sellPrice = basePrice × (1 + gridPercent × level)
            BigDecimal sellPrice = basePrice.multiply(
                BigDecimal.ONE.add(
                    gridPercent.multiply(BigDecimal.valueOf(level))
                )
            ).setScale(8, RoundingMode.HALF_UP);

            GridLine gridLine = new GridLine();
            gridLine.setStrategy(strategy);
            gridLine.setLevel(level);
            gridLine.setBuyPrice(buyPrice);
            gridLine.setSellPrice(sellPrice);
            gridLine.setState(GridLineState.WAIT_BUY);

            gridLines.add(gridLine);
        }

        // 设置到策略实体（利用 cascade = ALL 自动保存）
        strategy.setGridLines(gridLines);
    }

    /**
     * 根据 ID 获取策略详情
     */
    @GetMapping("/{id}")
    public StrategyResponse getStrategy(@PathVariable Long id) {
        Strategy strategy = strategyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("策略不存在: " + id));
        return StrategyResponse.fromEntity(strategy);
    }

    /**
     * 执行一次 tick（价格更新）
     */
    @PostMapping("/{id}/tick")
    public TickResponse executeTick(@PathVariable Long id, @RequestBody TickRequest request) {
        // 记录执行前的时间，用于查询本次产生的交易记录
        LocalDateTime beforeExecution = LocalDateTime.now();
        
        // 执行价格更新
        gridEngine.processPrice(id, request.getPrice());
        
        // 重新加载策略获取最新状态
        Strategy strategy = strategyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("策略不存在: " + id));
        
        // 查询本次执行产生的交易记录（在执行时间之后创建的记录）
        List<TradeRecord> newTrades = tradeRecordRepository
                .findByStrategyIdAndTradeTimeAfter(id, beforeExecution);
        
        // 构建响应
        TickResponse response = new TickResponse();
        response.setStatus(strategy.getStatus());
        response.setCurrentPrice(strategy.getLastPrice());
        response.setPosition(strategy.getPosition());
        response.setAvailableCash(strategy.getAvailableCash());
        response.setInvestedAmount(strategy.getInvestedAmount());
        response.setRealizedProfit(strategy.getRealizedProfit());
        
        // 转换交易记录
        List<TradeRecordDto> tradeDtos = newTrades.stream()
                .map(TradeRecordDto::fromEntity)
                .collect(Collectors.toList());
        response.setTrades(tradeDtos);
        
        return response;
    }
}
