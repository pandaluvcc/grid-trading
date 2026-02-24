package com.gridtrading.controller;

import com.gridtrading.controller.dto.*;
import com.gridtrading.domain.GridLine;
import com.gridtrading.domain.GridLineState;
import com.gridtrading.domain.GridType;
import com.gridtrading.domain.Strategy;
import com.gridtrading.domain.StrategyStatus;
import com.gridtrading.domain.TradeRecord;
import com.gridtrading.engine.GridEngine;
import com.gridtrading.repository.StrategyRepository;
import com.gridtrading.repository.TradeRecordRepository;
import com.gridtrading.repository.GridLineRepository;
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

    // ==================== 固定模板网格常量 ====================
    private static final int TOTAL_GRID_COUNT = 19;
    
    // 网格类型百分比（固定值）
    private static final BigDecimal SMALL_PERCENT = new BigDecimal("0.05");   // 5%
    private static final BigDecimal MEDIUM_PERCENT = new BigDecimal("0.15");  // 15%
    private static final BigDecimal LARGE_PERCENT = new BigDecimal("0.30");   // 30%
    
    // 固定步长（用于计划阶段价格联动）
    private static final BigDecimal SMALL_STEP = new BigDecimal("0.05");   // 小网步长
    private static final BigDecimal MEDIUM_STEP = new BigDecimal("0.15");  // 中网步长
    private static final BigDecimal LARGE_STEP = new BigDecimal("0.30");   // 大网步长
    
    // 固定的19条网格顺序（level从1到19，按顺序排列）
    private static final GridType[] GRID_TEMPLATE = {
        GridType.SMALL,   // 1
        GridType.SMALL,   // 2
        GridType.SMALL,   // 3
        GridType.SMALL,   // 4
        GridType.MEDIUM,  // 5
        GridType.SMALL,   // 6
        GridType.SMALL,   // 7
        GridType.SMALL,   // 8
        GridType.MEDIUM,  // 9
        GridType.LARGE,   // 10
        GridType.SMALL,   // 11
        GridType.SMALL,   // 12
        GridType.SMALL,   // 13
        GridType.MEDIUM,  // 14
        GridType.SMALL,   // 15
        GridType.SMALL,   // 16
        GridType.SMALL,   // 17
        GridType.MEDIUM,  // 18
        GridType.LARGE    // 19
    };

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private GridLineRepository gridLineRepository;

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
     * 创建新策略（固定模板网格）
     * 支持两种模式：
     * - 按金额：传入 amountPerGrid
     * - 按数量：传入 quantityPerGrid（根据 basePrice 计算 amountPerGrid）
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StrategyResponse createStrategy(@RequestBody CreateStrategyRequest request) {
        // 参数校验
        if (request.getSymbol() == null || request.getSymbol().isEmpty()) {
            throw new IllegalArgumentException("symbol 不能为空");
        }
        if (request.getBasePrice() == null || request.getBasePrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("basePrice 必须大于 0");
        }
        
        // 计算 amountPerGrid（支持按金额或按数量）
        BigDecimal amountPerGrid;
        BigDecimal quantityPerGrid = null;
        
        if (request.getQuantityPerGrid() != null && request.getQuantityPerGrid().compareTo(BigDecimal.ZERO) > 0) {
            // 按数量模式：amountPerGrid = basePrice × quantityPerGrid
            quantityPerGrid = request.getQuantityPerGrid();
            amountPerGrid = request.getBasePrice().multiply(quantityPerGrid);
        } else if (request.getAmountPerGrid() != null && request.getAmountPerGrid().compareTo(BigDecimal.ZERO) > 0) {
            // 按金额模式
            amountPerGrid = request.getAmountPerGrid();
        } else {
            throw new IllegalArgumentException("amountPerGrid 或 quantityPerGrid 必须提供一个且大于 0");
        }

        // 创建策略实体
        Strategy strategy = new Strategy();
        strategy.setName(request.getName() != null ? request.getName() : "固定模板网格策略");
        strategy.setSymbol(request.getSymbol());
        strategy.setBasePrice(request.getBasePrice());
        strategy.setAmountPerGrid(amountPerGrid);
        
        // 固定网格参数（基于百分比计算价差）
        BigDecimal smallGap = request.getBasePrice().multiply(SMALL_PERCENT);
        BigDecimal mediumGap = request.getBasePrice().multiply(MEDIUM_PERCENT);
        BigDecimal largeGap = request.getBasePrice().multiply(LARGE_PERCENT);
        
        strategy.setSmallGap(smallGap);
        strategy.setMediumGap(mediumGap);
        strategy.setLargeGap(largeGap);

        // 固定19条网格
        strategy.setGridCountDown(TOTAL_GRID_COUNT);
        strategy.setGridCountUp(0);

        // 兼容旧字段（使用小网百分比）
        strategy.setGridPercent(SMALL_PERCENT);

        // 计算最大投入资金（19条网格）
        BigDecimal maxCapital = amountPerGrid.multiply(BigDecimal.valueOf(TOTAL_GRID_COUNT));
        strategy.setMaxCapital(maxCapital);
        strategy.setAvailableCash(maxCapital);

        // 设置初始状态为 RUNNING
        strategy.setStatus(StrategyStatus.RUNNING);

        // 设置网格模型版本和摘要
        strategy.setGridModelVersion("v2.0");
        strategy.setGridSummary("小网13/中网4/大网2");

        // 生成固定19条网格计划
        generateGridLines(strategy);

        // 保存策略（级联保存 GridLine）
        Strategy savedStrategy = strategyRepository.save(strategy);

        return StrategyResponse.fromEntity(savedStrategy);
    }

    /**
     * 生成网格计划（固定模板：19条网格 v2.0 - 锚点回撤式）
     * <p>
     * 核心规则（严格执行）：
     * 1. 小网：连续阶梯，sellPrice = 上一条小网的 buyPrice
     * 2. 中网：锚点反弹，sellPrice = 上一个中网的 buyPrice（第1个中网除外，卖回basePrice）
     * 3. 大网：特殊锚点反弹规则：
     *    - 第1个大网(Level 10)：sellPrice = basePrice
     *    - 第2个大网(Level 19)：sellPrice = 第2个中网(Level 9)的买入价
     * 4. ❗买入价计算（差值递减，禁止乘法）：
     *    - 小网：buyPrice = lastSmallBuyPrice - (basePrice × 5%)
     *    - 中网/大网：buyPrice = lastSmallBuyPrice（继承最新小网价格）
     *
     * @param strategy 策略实体（必须已设置 basePrice 和 amountPerGrid）
     */
    private void generateGridLines(Strategy strategy) {
        List<GridLine> gridLines = new ArrayList<>();

        BigDecimal basePrice = strategy.getBasePrice();
        BigDecimal amountPerGrid = strategy.getAmountPerGrid();
        
        // 计算基准价差（用于差值递减）
        BigDecimal smallStep = basePrice.multiply(SMALL_PERCENT);  // 5%
        
        // 追踪变量
        BigDecimal lastSmallBuyPrice = basePrice;  // 最新小网买入价
        BigDecimal lastMediumBuyPrice = null;      // 上一个中网买入价（用于后续中网卖出锚点）
        
        // 记录第2个中网的买入价（专门用于第2个大网）
        BigDecimal secondMediumBuyPrice = null;
        int mediumCount = 0;  // 中网计数器
        
        // 按照固定模板生成19条网格
        for (int i = 0; i < TOTAL_GRID_COUNT; i++) {
            GridType gridType = GRID_TEMPLATE[i];
            int level = i + 1; // level: 1, 2, 3, ..., 19
            
            BigDecimal buyPrice;
            BigDecimal sellPrice;
            
            if (gridType == GridType.SMALL) {
                // === 小网：连续阶梯 ===
                if (level == 1) {
                    // 第1条小网：buyPrice = basePrice
                    buyPrice = basePrice;
                    // sellPrice = basePrice + smallStep
                    sellPrice = basePrice.add(smallStep)
                            .setScale(8, RoundingMode.HALF_UP);
                } else {
                    // 后续小网：buyPrice = lastSmallBuyPrice - smallStep（差值递减）
                    buyPrice = lastSmallBuyPrice.subtract(smallStep)
                            .setScale(8, RoundingMode.HALF_UP);
                    // sellPrice = 上一条小网.buyPrice（阶梯回撤）
                    sellPrice = lastSmallBuyPrice;
                }
                
                // 更新最新小网买入价
                lastSmallBuyPrice = buyPrice;
                
            } else if (gridType == GridType.MEDIUM) {
                // === 中网：锚点反弹 ===
                mediumCount++;
                
                // buyPrice = 当前最新小网.buyPrice（继承，不再递减）
                buyPrice = lastSmallBuyPrice;
                
                // sellPrice = 锚点
                if (level == 5) {
                    // 第1个中网（第5条）：卖回 basePrice
                    sellPrice = basePrice;
                } else {
                    // 后续中网：卖回上一个中网的 buyPrice
                    sellPrice = lastMediumBuyPrice;
                }
                
                // 记录当前中网买入价，供后续中网使用
                lastMediumBuyPrice = buyPrice;
                
                // 记录第2个中网的买入价（Level 9）
                if (mediumCount == 2) {
                    secondMediumBuyPrice = buyPrice;
                }
                
            } else { // GridType.LARGE
                // === 大网：特殊锚点反弹 ===
                // buyPrice = 当前最新小网.buyPrice（继承，不再递减）
                buyPrice = lastSmallBuyPrice;
                
                // sellPrice 特殊规则
                if (level == 10) {
                    // 第1个大网：卖回 basePrice
                    sellPrice = basePrice;
                } else {
                    // 第2个大网（Level 19）：卖回第2个中网(Level 9)的买入价
                    sellPrice = secondMediumBuyPrice;
                }
            }
            
            // 创建网格线
            GridLine gridLine = createGridLine(
                    strategy,
                    gridType,
                    level,
                    buyPrice,
                    sellPrice,
                    amountPerGrid
            );
            gridLines.add(gridLine);
        }

        // 设置到策略实体（利用 cascade = ALL 自动保存）
        strategy.setGridLines(gridLines);
    }
    
    /**
     * 根据网格类型获取对应的百分比
     */
    private BigDecimal getPercentByGridType(GridType gridType) {
        switch (gridType) {
            case SMALL:
                return SMALL_PERCENT;
            case MEDIUM:
                return MEDIUM_PERCENT;
            case LARGE:
                return LARGE_PERCENT;
            default:
                throw new IllegalArgumentException("未知的网格类型: " + gridType);
        }
    }

    /**
     * 创建单条 GridLine（完整计算所有字段）
     *
     * @param strategy      所属策略
     * @param gridType      网格类型
     * @param level         档位编号
     * @param buyPrice      买入价
     * @param sellPrice     卖出价
     * @param buyAmount     买入金额
     * @return 完整的 GridLine 实体
     */
    private GridLine createGridLine(
            Strategy strategy,
            GridType gridType,
            int level,
            BigDecimal buyPrice,
            BigDecimal sellPrice,
            BigDecimal buyAmount
    ) {
        GridLine gridLine = new GridLine();

        // 基本信息
        gridLine.setStrategy(strategy);
        gridLine.setGridType(gridType);
        gridLine.setLevel(level);
        gridLine.setState(GridLineState.WAIT_BUY);

        // 价格信息
        gridLine.setBuyPrice(buyPrice);
        gridLine.setSellPrice(sellPrice);
        // 触发价计算：买入触发价 = 买入价 + 0.02，卖出触发价 = 卖出价 - 0.02
        gridLine.setBuyTriggerPrice(buyPrice.add(new BigDecimal("0.02")));
        gridLine.setSellTriggerPrice(sellPrice.subtract(new BigDecimal("0.02")));

        // 买入金额
        gridLine.setBuyAmount(buyAmount);

        // 买入数量 = 买入金额 / 买入价格
        BigDecimal buyQuantity = buyAmount.divide(buyPrice, 8, RoundingMode.DOWN);
        gridLine.setBuyQuantity(buyQuantity);

        // 卖出金额 = 买入数量 × 卖出价格
        BigDecimal sellAmount = buyQuantity.multiply(sellPrice).setScale(2, RoundingMode.DOWN);
        gridLine.setSellAmount(sellAmount);

        // 毛利润 = 卖出金额 - 买入金额
        BigDecimal profit = sellAmount.subtract(buyAmount);
        gridLine.setProfit(profit);

        // 利润率 = 毛利润 / 买入金额
        BigDecimal profitRate = profit.divide(buyAmount, 6, RoundingMode.HALF_UP);
        gridLine.setProfitRate(profitRate);

        return gridLine;
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

    /**
     * 获取策略详情（完整信息）
     */
    @GetMapping("/{id}/detail")
    public StrategyDetailDTO getStrategyDetail(@PathVariable Long id) {
        Strategy strategy = strategyRepository.findByIdWithGridLines(id)
                .orElseThrow(() -> new RuntimeException("策略不存在: " + id));
        
        StrategyDetailDTO dto = StrategyDetailDTO.fromEntity(strategy);
        
        // 计算预计收益（所有网格的收益总和）
        BigDecimal expectedProfit = strategy.getGridLines().stream()
                .map(gl -> gl.getProfit() != null ? gl.getProfit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setExpectedProfit(expectedProfit);
        
        return dto;
    }

    /**
     * 更新网格计划买入价（计划阶段调整）
     * 功能：修改买入价后，自动重新计算该网格及所有后续网格
     */
    @PutMapping("/grid-lines/{gridLineId}/update-plan-buy-price")
    public void updatePlanBuyPrice(
            @PathVariable Long gridLineId,
            @RequestParam BigDecimal newBuyPrice
    ) {
        if (newBuyPrice == null || newBuyPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("买入价必须大于 0");
        }

        // 查询网格线
        GridLine gridLine = gridLineRepository.findById(gridLineId)
                .orElseThrow(() -> new RuntimeException("网格线不存在: " + gridLineId));

        // 验证状态：只有等待买入状态才能修改计划买入价
        if (gridLine.getState() != GridLineState.WAIT_BUY) {
            throw new IllegalArgumentException("只有等待买入状态的网格才能修改计划买入价");
        }

        // 查询策略及所有网格线
        Strategy strategy = gridLine.getStrategy();
        strategy = strategyRepository.findByIdWithGridLines(strategy.getId())
                .orElseThrow(() -> new RuntimeException("策略不存在"));

        // 如果修改的是第1格，更新 base_price
        if (gridLine.getLevel() == 1) {
            strategy.setBasePrice(newBuyPrice);
        }

        // 重新计算从当前网格开始的所有后续网格
        recalculatePlanGrids(strategy, gridLine.getLevel(), newBuyPrice);

        // 保存策略（级联保存所有网格线）
        strategyRepository.save(strategy);
    }

    /**
     * 重新计算计划阶段的网格（使用固定步长）
     * @param strategy 策略实体
     * @param fromLevel 从哪一格开始重新计算（包含该格）
     * @param startBuyPrice 起始买入价（修改格的新买入价）
     */
    private void recalculatePlanGrids(Strategy strategy, int fromLevel, BigDecimal startBuyPrice) {
        BigDecimal basePrice = strategy.getBasePrice();
        List<GridLine> gridLines = strategy.getGridLines();
        
        // 按 level 排序
        gridLines.sort((a, b) -> a.getLevel().compareTo(b.getLevel()));
        
        // 找到需要重新计算的网格（fromLevel 到 19）
        GridLine previousSmallGrid = null;  // 上一个小网（用于小网向上回溯）
        GridLine previousMediumGrid = null; // 上一个中网（用于中网向上回溯）
        GridLine previousLargeGrid = null;  // 上一个大网（用于大网向上回溯）
        
        // 先找到 fromLevel 之前最近的小网、中网、大网（用于回溯）
        for (GridLine gl : gridLines) {
            if (gl.getLevel() < fromLevel) {
                if (gl.getGridType() == GridType.SMALL) {
                    previousSmallGrid = gl;
                } else if (gl.getGridType() == GridType.MEDIUM) {
                    previousMediumGrid = gl;
                } else if (gl.getGridType() == GridType.LARGE) {
                    previousLargeGrid = gl;
                }
            }
        }
        
        // 重新计算 fromLevel 到第19格
        for (GridLine gl : gridLines) {
            if (gl.getLevel() < fromLevel) {
                continue; // 跳过之前的网格
            }
            
            BigDecimal buyPrice;
            BigDecimal sellPrice;
            
            if (gl.getLevel() == fromLevel) {
                // 当前修改的网格，使用新的买入价
                buyPrice = startBuyPrice;
            } else {
                // 后续网格，根据类型回溯计算
                if (gl.getGridType() == GridType.SMALL) {
                    // 小网：向上找最近的小网，buyPrice = 上一小网 - 0.05
                    if (previousSmallGrid != null) {
                        buyPrice = previousSmallGrid.getBuyPrice().subtract(SMALL_STEP);
                    } else {
                        // 如果没有前置小网（理论上不会发生），使用 base_price
                        buyPrice = basePrice.subtract(SMALL_STEP.multiply(new BigDecimal(gl.getLevel() - 1)));
                    }
                } else if (gl.getGridType() == GridType.MEDIUM) {
                    // 中网：向上找最近的中网，buyPrice = 上一中网 - 0.15
                    if (previousMediumGrid != null) {
                        buyPrice = previousMediumGrid.getBuyPrice().subtract(MEDIUM_STEP);
                    } else {
                        // 如果没有前置中网，使用 base_price - 0.15
                        buyPrice = basePrice.subtract(MEDIUM_STEP);
                    }
                } else { // LARGE
                    // 大网：向上找最近的大网，buyPrice = 上一大网 - 0.30
                    if (previousLargeGrid != null) {
                        buyPrice = previousLargeGrid.getBuyPrice().subtract(LARGE_STEP);
                    } else {
                        // 如果没有前置大网，使用 base_price - 0.30
                        buyPrice = basePrice.subtract(LARGE_STEP);
                    }
                }
            }
            
            // 计算卖出价（使用固定步长加法）
            if (gl.getGridType() == GridType.SMALL) {
                sellPrice = buyPrice.add(SMALL_STEP);
            } else if (gl.getGridType() == GridType.MEDIUM) {
                sellPrice = buyPrice.add(MEDIUM_STEP);
            } else { // LARGE
                sellPrice = buyPrice.add(LARGE_STEP);
            }
            
            // 更新网格线的价格
            gl.setBuyPrice(buyPrice.setScale(2, RoundingMode.HALF_UP));
            gl.setSellPrice(sellPrice.setScale(2, RoundingMode.HALF_UP));
            
            // 重新计算触发价
            gl.setBuyTriggerPrice(gl.getBuyPrice().add(new BigDecimal("0.02")));
            gl.setSellTriggerPrice(gl.getSellPrice().subtract(new BigDecimal("0.02")));
            
            // 重新计算其他字段
            BigDecimal buyAmount = gl.getBuyAmount();
            BigDecimal buyQuantity = buyAmount.divide(gl.getBuyPrice(), 8, RoundingMode.DOWN);
            gl.setBuyQuantity(buyQuantity);
            
            BigDecimal sellAmount = buyQuantity.multiply(gl.getSellPrice()).setScale(2, RoundingMode.DOWN);
            gl.setSellAmount(sellAmount);
            
            BigDecimal profit = sellAmount.subtract(buyAmount);
            gl.setProfit(profit);
            
            BigDecimal profitRate = profit.divide(buyAmount, 6, RoundingMode.HALF_UP);
            gl.setProfitRate(profitRate);
            
            // 更新回溯变量
            if (gl.getGridType() == GridType.SMALL) {
                previousSmallGrid = gl;
            } else if (gl.getGridType() == GridType.MEDIUM) {
                previousMediumGrid = gl;
            } else if (gl.getGridType() == GridType.LARGE) {
                previousLargeGrid = gl;
            }
        }
    }

    /**
     * 更新网格实际买入价并重算后续网格
     */
    @PutMapping("/grid-lines/actual-buy-price")
    public void updateActualBuyPrice(@RequestBody UpdateActualBuyPriceRequest request) {
        if (request.getGridLineId() == null) {
            throw new IllegalArgumentException("gridLineId 不能为空");
        }
        if (request.getActualBuyPrice() == null || request.getActualBuyPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("actualBuyPrice 必须大于 0");
        }

        // 查询目标网格线
        Strategy strategy = strategyRepository.findByIdWithGridLines(
            strategyRepository.findAll().stream()
                .filter(s -> s.getGridLines().stream().anyMatch(gl -> gl.getId().equals(request.getGridLineId())))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("网格线不存在"))
                .getId()
        ).orElseThrow(() -> new RuntimeException("策略不存在"));

        GridLine targetGridLine = strategy.getGridLines().stream()
                .filter(gl -> gl.getId().equals(request.getGridLineId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("网格线不存在: " + request.getGridLineId()));

        // 验证状态：只有已买入状态才能修改
        if (targetGridLine.getState() != GridLineState.BOUGHT) {
            throw new IllegalArgumentException("只有已买入状态的网格才能修改实际买入价");
        }

        // 更新实际买入价
        targetGridLine.setActualBuyPrice(request.getActualBuyPrice());

        // 重算后续网格（从当前level+1开始）
        recalculateSubsequentGrids(strategy, targetGridLine);

        // 保存策略（级联保存所有网格线）
        strategyRepository.save(strategy);
    }

    /**
     * 重算后续网格（从指定网格之后的网格开始）
     */
    private void recalculateSubsequentGrids(Strategy strategy, GridLine fromGridLine) {
        BigDecimal basePrice = strategy.getBasePrice();
        BigDecimal smallStep = basePrice.multiply(SMALL_PERCENT);

        int startLevel = fromGridLine.getLevel() + 1;
        if (startLevel > TOTAL_GRID_COUNT) {
            return; // 已经是最后一格，无需重算
        }

        // 使用actualBuyPrice作为新的起点
        BigDecimal lastSmallBuyPrice = fromGridLine.getActualBuyPrice();
        BigDecimal lastMediumBuyPrice = null;
        
        // 记录第2个中网的买入价（专门用于第2个大网）
        BigDecimal secondMediumBuyPrice = null;
        int mediumCount = 0;

        // 重新扫描找到最近的中网买入价和第2个中网的买入价
        for (GridLine gl : strategy.getGridLines()) {
            if (gl.getLevel() < startLevel && gl.getGridType() == GridType.MEDIUM) {
                mediumCount++;
                BigDecimal mediumBuyPrice = gl.getActualBuyPrice() != null ? 
                    gl.getActualBuyPrice() : gl.getBuyPrice();
                lastMediumBuyPrice = mediumBuyPrice;
                
                // 记录第2个中网(Level 9)的买入价
                if (mediumCount == 2) {
                    secondMediumBuyPrice = mediumBuyPrice;
                }
            }
        }

        // 从startLevel开始重算后续网格
        for (GridLine gridLine : strategy.getGridLines()) {
            if (gridLine.getLevel() < startLevel) {
                continue; // 跳过前面的网格
            }

            // 跳过已成交的网格
            if (gridLine.getState() == GridLineState.BOUGHT || gridLine.getState() == GridLineState.SOLD) {
                // 如果是小网，更新lastSmallBuyPrice
                if (gridLine.getGridType() == GridType.SMALL) {
                    lastSmallBuyPrice = gridLine.getActualBuyPrice() != null ? 
                        gridLine.getActualBuyPrice() : gridLine.getBuyPrice();
                }
                // 如果是中网，更新lastMediumBuyPrice和第2个中网价格
                if (gridLine.getGridType() == GridType.MEDIUM) {
                    mediumCount++;
                    BigDecimal mediumBuyPrice = gridLine.getActualBuyPrice() != null ? 
                        gridLine.getActualBuyPrice() : gridLine.getBuyPrice();
                    lastMediumBuyPrice = mediumBuyPrice;
                    if (mediumCount == 2) {
                        secondMediumBuyPrice = mediumBuyPrice;
                    }
                }
                continue;
            }

            // 重算未成交网格
            BigDecimal newBuyPrice;
            BigDecimal newSellPrice;

            if (gridLine.getGridType() == GridType.SMALL) {
                // 小网：差值递减
                newBuyPrice = lastSmallBuyPrice.subtract(smallStep)
                        .setScale(8, RoundingMode.HALF_UP);
                newSellPrice = lastSmallBuyPrice;
                lastSmallBuyPrice = newBuyPrice;

            } else if (gridLine.getGridType() == GridType.MEDIUM) {
                mediumCount++;
                
                // 中网：继承最新小网价格
                newBuyPrice = lastSmallBuyPrice;
                // 锚点逻辑
                if (gridLine.getLevel() == 5) {
                    newSellPrice = basePrice;
                } else {
                    newSellPrice = lastMediumBuyPrice;
                }
                lastMediumBuyPrice = newBuyPrice;
                
                // 记录第2个中网的买入价
                if (mediumCount == 2) {
                    secondMediumBuyPrice = newBuyPrice;
                }

            } else { // LARGE
                // 大网：继承最新小网价格
                newBuyPrice = lastSmallBuyPrice;
                
                // 特殊锚点规则
                if (gridLine.getLevel() == 10) {
                    // 第1个大网：卖回 basePrice
                    newSellPrice = basePrice;
                } else {
                    // 第2个大网（Level 19）：卖回第2个中网(Level 9)的买入价
                    newSellPrice = secondMediumBuyPrice;
                }
            }

            // 更新价格
            gridLine.setBuyPrice(newBuyPrice);
            gridLine.setSellPrice(newSellPrice);
            // 触发价计算：买入触发价 = 买入价 + 0.02，卖出触发价 = 卖出价 - 0.02
            gridLine.setBuyTriggerPrice(newBuyPrice.add(new BigDecimal("0.02")));
            gridLine.setSellTriggerPrice(newSellPrice.subtract(new BigDecimal("0.02")));

            // 重算数量和金额
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

    /**
     * 获取网格计划表
     * <p>
     * 直接读取 GridLine 中已计算好的字段，无需重新计算
     */
    @GetMapping("/{id}/grid-plans")
    public GridPlanResponse getGridPlans(@PathVariable Long id) {
        Strategy strategy = strategyRepository.findByIdWithGridLines(id)
                .orElseThrow(() -> new RuntimeException("策略不存在: " + id));

        GridPlanResponse response = new GridPlanResponse();

        // 策略基础信息
        GridPlanResponse.StrategyInfo strategyInfo = new GridPlanResponse.StrategyInfo();
        strategyInfo.setName(strategy.getName());
        strategyInfo.setSymbol(strategy.getSymbol());
        strategyInfo.setBasePrice(strategy.getBasePrice());
        response.setStrategy(strategyInfo);

        // 网格计划列表（直接使用实体中已计算好的字段）
        List<GridPlanResponse.GridPlanItem> gridPlans = strategy.getGridLines().stream()
                .map(gridLine -> {
                    GridPlanResponse.GridPlanItem item = new GridPlanResponse.GridPlanItem();
                    item.setId(gridLine.getId());
                    item.setGridType(gridLine.getGridType());
                    item.setLevel(gridLine.getLevel());
                    item.setBuyPrice(gridLine.getBuyPrice());
                    item.setSellPrice(gridLine.getSellPrice());
                    item.setBuyTriggerPrice(gridLine.getBuyTriggerPrice());
                    item.setSellTriggerPrice(gridLine.getSellTriggerPrice());
                    item.setQuantity(gridLine.getBuyQuantity());
                    item.setBuyAmount(gridLine.getBuyAmount());
                    item.setSellAmount(gridLine.getSellAmount());
                    item.setProfit(gridLine.getProfit());
                    item.setProfitRate(gridLine.getProfitRate());
                    item.setState(gridLine.getState());

                    return item;
                })
                .sorted((a, b) -> {
                    // 按买入价从高到低排序
                    int priceCompare = b.getBuyPrice().compareTo(a.getBuyPrice());
                    if (priceCompare != 0) {
                        return priceCompare;
                    }
                    // 同一买入价，按网格类型排序（SMALL < MEDIUM < LARGE）
                    return a.getGridType().compareTo(b.getGridType());
                })
                .collect(Collectors.toList());

        response.setGridPlans(gridPlans);
        return response;
    }
}
