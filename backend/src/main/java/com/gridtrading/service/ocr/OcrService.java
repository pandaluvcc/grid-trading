package com.gridtrading.service.ocr;

import com.gridtrading.controller.dto.OcrMatchStatus;
import com.gridtrading.controller.dto.OcrRecognizeResponse;
import com.gridtrading.controller.dto.OcrTradeRecord;
import com.gridtrading.domain.*;
import com.gridtrading.engine.GridEngine;
import com.gridtrading.repository.GridLineRepository;
import com.gridtrading.repository.StrategyRepository;
import com.gridtrading.repository.TradeRecordRepository;
import com.gridtrading.service.PositionCalculator;
import com.gridtrading.service.grid.GridPlanGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR识别与匹配服务
 */
@Service
public class OcrService {

    private static final Pattern SYMBOL_PATTERN = Pattern.compile("(证券代码|基金代码|股票代码|代码)[:\\s]*([A-Za-z0-9./-]+)");
    private static final Pattern NAME_PATTERN = Pattern.compile("(证券名称|基金名称|股票名称|名称|证券简称|基金简称|股票简称)[:\\s]*([\\u4e00-\\u9fa5A-Za-z0-9·._-]{2,})");
    private static final Pattern CODE_WITH_NAME_PATTERN = Pattern.compile("(\\d{6})\\s*([\\u4e00-\\u9fa5A-Za-z0-9·._-]{2,})");
    private static final Pattern NAME_WITH_CODE_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9·._-]{2,})\\s*(\\d{6})");
    private static final Pattern CODE_ONLY_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

    private final BaiduOcrClient baiduOcrClient;
    private final EastMoneyParser eastMoneyParser;
    private final StrategyRepository strategyRepository;
    private final GridLineRepository gridLineRepository;
    private final TradeRecordRepository tradeRecordRepository;
    private final GridEngine gridEngine;
    private final PositionCalculator positionCalculator;

    private final BigDecimal tolerancePercent;
    private final long timeWindowSeconds;

    public OcrService(
            BaiduOcrClient baiduOcrClient,
            EastMoneyParser eastMoneyParser,
            StrategyRepository strategyRepository,
            GridLineRepository gridLineRepository,
            TradeRecordRepository tradeRecordRepository,
            GridEngine gridEngine,
            PositionCalculator positionCalculator,
            @Value("${ocr.match.tolerance-percent:0.005}") BigDecimal tolerancePercent,
                @Value("${ocr.match.time-window-seconds:30}") long timeWindowSeconds
    ) {
        this.baiduOcrClient = baiduOcrClient;
        this.eastMoneyParser = eastMoneyParser;
        this.strategyRepository = strategyRepository;
        this.gridLineRepository = gridLineRepository;
        this.tradeRecordRepository = tradeRecordRepository;
        this.gridEngine = gridEngine;
        this.positionCalculator = positionCalculator;
        this.tolerancePercent = tolerancePercent;
        this.timeWindowSeconds = timeWindowSeconds;
    }

    public OcrRecognizeResponse recognizeAndParse(MultipartFile file, Long strategyId, String brokerType) {
        if (file == null) {
            return OcrRecognizeResponse.error("file is empty");
        }
        List<MultipartFile> files = new ArrayList<>();
        files.add(file);
        return recognizeAndParse(files, strategyId, brokerType);
    }

    public OcrRecognizeResponse recognizeAndParse(List<MultipartFile> files, Long strategyId, String brokerType) {
        if (files == null || files.isEmpty()) {
            return OcrRecognizeResponse.error("files is empty");
        }
        if (files.size() > 5) {
            return OcrRecognizeResponse.error("max 5 files per batch");
        }
        if (strategyId == null) {
            return OcrRecognizeResponse.error("strategyId is required");
        }
        if (brokerType == null || brokerType.trim().isEmpty()) {
            return OcrRecognizeResponse.error("brokerType is required");
        }

        Strategy strategy = strategyRepository.findById(strategyId)
                .orElseThrow(() -> new IllegalArgumentException("策略不存在"));

        StringBuilder rawTextBuilder = new StringBuilder();
        List<OcrTradeRecord> records = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                return OcrRecognizeResponse.error("file is empty");
            }

            String rawText;
            try {
                rawText = baiduOcrClient.recognize(file);
            } catch (IOException ex) {
                String name = file.getOriginalFilename();
                return OcrRecognizeResponse.error("OCR failed" + (name != null ? (": " + name) : "") + ": " + ex.getMessage());
            }

            if (rawTextBuilder.length() > 0) {
                rawTextBuilder.append("\n---\n");
            }
            String filename = file.getOriginalFilename();
            if (filename != null && !filename.trim().isEmpty()) {
                rawTextBuilder.append("FILE: ").append(filename).append("\n");
            }
            rawTextBuilder.append(rawText);

            records.addAll(parseByBroker(rawText, brokerType));
        }

        records = dedupeRecords(records);
        records = mergeSplitBuys(records);
        matchRecords(records, strategy, null);

        return OcrRecognizeResponse.success(rawTextBuilder.toString(), records);
    }

    public OcrRecognizeResponse rematch(List<OcrTradeRecord> records, Long strategyId) {
        if (strategyId == null) {
            return OcrRecognizeResponse.error("strategyId is required");
        }
        Strategy strategy = strategyRepository.findById(strategyId)
                .orElseThrow(() -> new IllegalArgumentException("策略不存在"));
        if (records == null) {
            records = Collections.emptyList();
        }
        matchRecords(records, strategy, "rematch");
        return OcrRecognizeResponse.success("", records);
    }

    public Strategy createStrategyFromOcr(List<MultipartFile> files,
                                          String brokerType,
                                          String name,
                                          String symbol,
                                          String gridCalculationMode) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("files is empty");
        }
        if (files.size() > 5) {
            throw new IllegalArgumentException("max 5 files per batch");
        }
        if (brokerType == null || brokerType.trim().isEmpty()) {
            throw new IllegalArgumentException("brokerType is required");
        }

        List<OcrTradeRecord> records = new ArrayList<>();
        StringBuilder rawTextBuilder = new StringBuilder();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            try {
                String rawText = baiduOcrClient.recognize(file);
                if (rawTextBuilder.length() > 0) {
                    rawTextBuilder.append("\n");
                }
                rawTextBuilder.append(rawText);
                records.addAll(parseByBroker(rawText, brokerType));
            } catch (IOException ex) {
                String filename = file.getOriginalFilename();
                throw new IllegalArgumentException("OCR failed" + (filename != null ? (": " + filename) : "") + ": " + ex.getMessage());
            }
        }

        records = dedupeRecords(records);
        records = mergeSplitBuys(records);
        records = sortRecords(records);
        records = filterUsable(records);

        if (records.isEmpty()) {
            throw new IllegalArgumentException("no valid trade records");
        }

        OcrTradeRecord baseRecord = findBaseRecord(records);
        BigDecimal basePrice = baseRecord.getPrice();
        BigDecimal amountPerGrid = resolveAmountPerGrid(baseRecord);
        BigDecimal quantityPerGrid = baseRecord.getQuantity();
        if (basePrice == null || amountPerGrid == null) {
            throw new IllegalArgumentException("basePrice or amountPerGrid not found");
        }

    String[] extracted = extractNameAndSymbol(rawTextBuilder.toString());
    String resolvedName = name != null && !name.trim().isEmpty() ? name : extracted[0];
    String resolvedSymbol = symbol != null && !symbol.trim().isEmpty() ? symbol : extracted[1];

    // 使用传入的网格计算模式，默认为独立计算模式
    String calculationMode = gridCalculationMode != null && !gridCalculationMode.trim().isEmpty() 
            ? gridCalculationMode : "INDEPENDENT";

    Strategy strategy = buildStrategy(resolvedName, resolvedSymbol, basePrice, amountPerGrid, quantityPerGrid, calculationMode);
        strategy = strategyRepository.save(strategy);

        List<GridLine> orderedGridLines = new ArrayList<>(strategy.getGridLines());
        orderedGridLines.sort(Comparator.comparing(GridLine::getLevel));

        GridLine lastMatchedLine = null;
        for (OcrTradeRecord record : records) {
            if (record == null || record.getType() == null || record.getPrice() == null) {
                continue;
            }
            GridLine gridLine = findBestMatchLineForCreate(orderedGridLines, record);
            if (gridLine == null) {
                continue;
            }
            normalizeRecordAmounts(record);
            applyRecordToGridLine(gridLine, record, strategy);

            TradeRecord tradeRecord = buildTradeRecord(strategy, gridLine, record);
            tradeRecordRepository.save(tradeRecord);

            lastMatchedLine = gridLine;
        }

        // ✅ 统一调用GridEngine更新网格sellPrice（包含收益率保护）
        System.out.println("[OCR导入] 更新已导入网格的sellPrice并触发级联更新");
        for (GridLine gridLine : orderedGridLines) {
            // 只更新已买入的网格
            if (gridLine.getActualBuyPrice() != null && gridLine.getState() == GridLineState.BOUGHT) {
                // 调用GridEngine的统一方法更新sellPrice
                gridEngine.updateCurrentGridSellPriceAfterBuy(strategy, gridLine);
            }
        }

        // 触发级联更新（更新后续未导入的网格）
        if (lastMatchedLine != null && lastMatchedLine.getLevel() < orderedGridLines.size()) {
            System.out.println("[OCR导入] 触发级联更新后续网格");
            BigDecimal actualBuyPrice = lastMatchedLine.getActualBuyPrice() != null ?
                lastMatchedLine.getActualBuyPrice() : lastMatchedLine.getBuyPrice();
            gridEngine.recalculateSubsequentGridsAfterManualBuy(strategy, lastMatchedLine, actualBuyPrice);
        }

        // 计算每个网格的真实累计收益
        calculateActualProfits(strategy);

        // ✅ 计算持仓相关字段（成本价、买入均价、持仓盈亏等）
        positionCalculator.calculateAndUpdate(strategy);

        // ✅ 设置lastPrice为最新交易记录的价格
        BigDecimal latestPrice = findLatestTradePrice(records);
        if (latestPrice != null) {
            strategy.setLastPrice(latestPrice);
            System.out.println("[OCR导入] 设置lastPrice: " + latestPrice);
        }

        return strategyRepository.save(strategy);
    }

    private List<OcrTradeRecord> parseByBroker(String rawText, String brokerType) {
        if ("EASTMONEY".equalsIgnoreCase(brokerType)) {
            return eastMoneyParser.parse(rawText);
        }
        throw new IllegalArgumentException("Unsupported brokerType: " + brokerType);
    }

    private void matchRecords(List<OcrTradeRecord> records, Strategy strategy, String source) {
        if (records == null || records.isEmpty()) {
            return;
        }

        List<GridLine> gridLines = gridLineRepository.findByStrategyId(strategy.getId());
        if (gridLines == null || gridLines.isEmpty()) {
            for (OcrTradeRecord record : records) {
                markUnmatched(record, "no grid lines");
            }
            return;
        }

        List<TradeRecord> existingRecords = tradeRecordRepository.findByStrategyId(strategy.getId());

        Map<Integer, GridLine> gridLineByLevel = new HashMap<>();
        for (GridLine gridLine : gridLines) {
            gridLineByLevel.put(gridLine.getLevel(), gridLine);
        }

        OcrTradeRecord opening = findOpeningRecord(records);
        GridPlanGenerator planGenerator = null;
        List<GridPlanGenerator.GridPlanItem> planItems = null;
        if (opening != null && opening.getPrice() != null) {
            planGenerator = new GridPlanGenerator();
            planItems = planGenerator.generate(opening.getPrice(), strategy.getAmountPerGrid());
        }

        List<MatchLine> matchLines = buildMatchLines(gridLines, planItems, opening != null);

        List<OcrTradeRecord> sorted = new ArrayList<>(records);
        sorted.sort((a, b) -> {
            if (a == null && b == null) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            LocalDateTime ta = a.getTradeTime();
            LocalDateTime tb = b.getTradeTime();
            if (ta == null && tb == null) {
                return 0;
            }
            if (ta == null) {
                return 1;
            }
            if (tb == null) {
                return -1;
            }
            return ta.toInstant(ZoneOffset.UTC).compareTo(tb.toInstant(ZoneOffset.UTC));
        });

        boolean singleRecord = sorted.size() == 1;
        if (singleRecord) {
            matchSingleRecord(sorted, matchLines, gridLineByLevel, existingRecords);
            return;
        }

        matchSequentialRecords(sorted, matchLines, gridLineByLevel, existingRecords);
    }

    private List<OcrTradeRecord> sortRecords(List<OcrTradeRecord> records) {
        List<OcrTradeRecord> sorted = new ArrayList<>(records);
        sorted.sort((a, b) -> {
            if (a == null && b == null) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            LocalDateTime ta = a.getTradeTime();
            LocalDateTime tb = b.getTradeTime();
            if (ta == null && tb == null) {
                return 0;
            }
            if (ta == null) {
                return 1;
            }
            if (tb == null) {
                return -1;
            }
            return ta.toInstant(ZoneOffset.UTC).compareTo(tb.toInstant(ZoneOffset.UTC));
        });
        return sorted;
    }

    private List<OcrTradeRecord> filterUsable(List<OcrTradeRecord> records) {
        List<OcrTradeRecord> usable = new ArrayList<>();
        for (OcrTradeRecord record : records) {
            if (record == null || record.getType() == null || record.getPrice() == null) {
                continue;
            }
            usable.add(record);
        }
        return usable;
    }

    private OcrTradeRecord findBaseRecord(List<OcrTradeRecord> records) {
        for (OcrTradeRecord record : records) {
            if (record.getType() == TradeType.BUY && record.getPrice() != null) {
                return record;
            }
        }
        return records.get(0);
    }

    private BigDecimal resolveAmountPerGrid(OcrTradeRecord record) {
        if (record == null || record.getPrice() == null) {
            return null;
        }
        BigDecimal amount = record.getAmount();
        BigDecimal quantity = record.getQuantity();
        if (amount == null && quantity != null) {
            amount = quantity.multiply(record.getPrice()).setScale(2, RoundingMode.DOWN);
            record.setAmount(amount);
        }
        return amount;
    }

    private Strategy buildStrategy(String name,
                                   String symbol,
                                   BigDecimal basePrice,
                                   BigDecimal amountPerGrid,
                                   BigDecimal quantityPerGrid,
                                   String gridCalculationMode) {
        Strategy strategy = new Strategy();
        String safeName = name != null && !name.trim().isEmpty()
                ? name.trim()
                : "OCR导入策略-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        String safeSymbol = symbol != null && !symbol.trim().isEmpty()
                ? symbol.trim()
                : "OCR-IMPORT";

        strategy.setName(safeName);
        strategy.setSymbol(safeSymbol);
        strategy.setBasePrice(basePrice);
        strategy.setAmountPerGrid(amountPerGrid);
        strategy.setQuantityPerGrid(quantityPerGrid);
        strategy.setGridCalculationMode(gridCalculationMode);

        BigDecimal smallGap = basePrice.multiply(GridPlanGenerator.SMALL_PERCENT);
        BigDecimal mediumGap = basePrice.multiply(GridPlanGenerator.MEDIUM_PERCENT);
        BigDecimal largeGap = basePrice.multiply(GridPlanGenerator.LARGE_PERCENT);

        strategy.setSmallGap(smallGap);
        strategy.setMediumGap(mediumGap);
        strategy.setLargeGap(largeGap);

        strategy.setGridCountDown(GridPlanGenerator.TOTAL_GRID_COUNT);
        strategy.setGridCountUp(0);
        strategy.setGridPercent(GridPlanGenerator.SMALL_PERCENT);

        BigDecimal maxCapital = amountPerGrid.multiply(BigDecimal.valueOf(GridPlanGenerator.TOTAL_GRID_COUNT));
        strategy.setMaxCapital(maxCapital);
        strategy.setAvailableCash(maxCapital);
        strategy.setStatus(StrategyStatus.RUNNING);
        strategy.setGridModelVersion("v2.0");
        strategy.setGridSummary("小网13/中网4/大网2");

        // 根据模式选择生成方法
        GridPlanGenerator generator = new GridPlanGenerator();
        GridPlanGenerator.CalculationMode mode = "INDEPENDENT".equals(gridCalculationMode)
                ? GridPlanGenerator.CalculationMode.INDEPENDENT
                : GridPlanGenerator.CalculationMode.PRICE_LOCK;
        List<GridPlanGenerator.GridPlanItem> items = generator.generate(basePrice, amountPerGrid, mode);
        
        for (GridPlanGenerator.GridPlanItem item : items) {
            GridLine line = new GridLine();
            line.setStrategy(strategy);
            line.setGridType(item.getGridType());
            line.setLevel(item.getLevel());
            line.setBuyPrice(item.getBuyPrice());
            line.setSellPrice(item.getSellPrice());
            line.setBuyTriggerPrice(item.getBuyTriggerPrice());
            line.setSellTriggerPrice(item.getSellTriggerPrice());
            line.setBuyAmount(item.getBuyAmount());
            line.setBuyQuantity(item.getBuyQuantity());
            line.setSellAmount(item.getSellAmount());
            line.setProfit(item.getProfit());
            line.setProfitRate(item.getProfitRate());
            line.setState(GridLineState.WAIT_BUY);
            strategy.getGridLines().add(line);
        }

        return strategy;
    }

    private void normalizeRecordAmounts(OcrTradeRecord record) {
        if (record == null || record.getPrice() == null) {
            return;
        }
        BigDecimal amount = record.getAmount();
        BigDecimal quantity = record.getQuantity();
        if (quantity == null && amount != null) {
            quantity = amount.divide(record.getPrice(), 8, RoundingMode.DOWN);
            record.setQuantity(quantity);
        } else if (amount == null && quantity != null) {
            amount = quantity.multiply(record.getPrice()).setScale(2, RoundingMode.DOWN);
            record.setAmount(amount);
        }
    }

    private void applyRecordToGridLine(GridLine gridLine, OcrTradeRecord record, Strategy strategy) {
        if (gridLine == null || record == null || record.getType() == null || record.getPrice() == null) {
            return;
        }
        if (record.getType() == TradeType.BUY) {
            applyBuyRecord(gridLine, record, strategy);
            gridLine.setState(GridLineState.BOUGHT);
        } else {
            applySellRecord(gridLine, record, strategy);
            gridLine.setState(GridLineState.WAIT_BUY);
        }
    }

    private TradeRecord buildTradeRecord(Strategy strategy, GridLine gridLine, OcrTradeRecord record) {
        TradeRecord entity = new TradeRecord();
        entity.setStrategy(strategy);
        entity.setGridLine(gridLine);
        entity.setType(record.getType());
        entity.setPrice(record.getPrice());
        BigDecimal quantity = record.getQuantity();
        BigDecimal amount = record.getAmount();
        if (quantity == null) {
            quantity = gridLine.getBuyQuantity();
        }
        if (amount == null && quantity != null) {
            amount = quantity.multiply(record.getPrice()).setScale(2, RoundingMode.DOWN);
        }
        if (amount == null) {
            amount = gridLine.getBuyAmount();
        }
        entity.setQuantity(quantity);
        entity.setAmount(amount);
        entity.setFee(record.getFee());
        entity.setTradeTime(record.getTradeTime() != null ? record.getTradeTime() : LocalDateTime.now());
        return entity;
    }

    private void applyBuyRecord(GridLine gridLine, OcrTradeRecord record, Strategy strategy) {
        BigDecimal price = record.getPrice();
        gridLine.setActualBuyPrice(price);
        gridLine.setBuyPrice(price);
        gridLine.setBuyTriggerPrice(price.add(new BigDecimal("0.002")));

        if (record.getAmount() != null) {
            gridLine.setBuyAmount(record.getAmount());
        }
        if (record.getQuantity() != null) {
            gridLine.setBuyQuantity(record.getQuantity());
        }
        
        // 增加买入次数统计
        gridLine.setBuyCount(gridLine.getBuyCount() + 1);
        System.out.println("[OCR-BUY] 网格" + gridLine.getLevel() + " buyCount -> " + gridLine.getBuyCount());
        
        // ✅ 关键修复：当实际买入价高于建议价时，重新按实际买入价计算该网格的卖出价，确保收益率
        BigDecimal profitRate = gridLine.getProfitRate();
        if (profitRate == null || profitRate.compareTo(BigDecimal.ZERO) <= 0) {
            // 根据网格类型设置默认收益率
            if (gridLine.getGridType() == GridType.SMALL) {
                profitRate = new BigDecimal("0.05");
            } else if (gridLine.getGridType() == GridType.MEDIUM) {
                profitRate = new BigDecimal("0.15");
            } else {
                profitRate = new BigDecimal("0.30");
            }
        }

        // 按实际买入价重新计算卖出价，保证收益率
        BigDecimal newSellPrice = price.multiply(BigDecimal.ONE.add(profitRate))
            .setScale(3, RoundingMode.HALF_UP);
        gridLine.setSellPrice(newSellPrice);
        gridLine.setSellTriggerPrice(newSellPrice.subtract(new BigDecimal("0.002")));

        System.out.println("[独立计算] 网格" + gridLine.getLevel() + " " + gridLine.getGridType() +
            ": 买" + price + " → 卖" + newSellPrice + " (" + profitRate.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP) + "%)");

        recalcLineTotals(gridLine, strategy);
    }

    private void applySellRecord(GridLine gridLine, OcrTradeRecord record, Strategy strategy) {
        BigDecimal price = record.getPrice();
        gridLine.setActualSellPrice(price);
        gridLine.setSellPrice(price); // ✅ 直接使用真实成交价
        gridLine.setSellTriggerPrice(price.subtract(new BigDecimal("0.002")));

        // 增加卖出次数统计
        gridLine.setSellCount(gridLine.getSellCount() + 1);
        System.out.println("[OCR-SELL] 网格" + gridLine.getLevel() + " sellCount -> " + gridLine.getSellCount());
        
        recalcLineTotals(gridLine, strategy);
    }

    private void recalcLineTotals(GridLine gridLine, Strategy strategy) {
        BigDecimal buyPrice = gridLine.getBuyPrice();
        BigDecimal sellPrice = gridLine.getSellPrice();
        BigDecimal buyAmount = gridLine.getBuyAmount();
        BigDecimal buyQuantity = gridLine.getBuyQuantity();

        if (buyQuantity == null && buyAmount != null && buyPrice != null) {
            buyQuantity = buyAmount.divide(buyPrice, 8, RoundingMode.DOWN);
            gridLine.setBuyQuantity(buyQuantity);
        }
        if (buyAmount == null && buyQuantity != null && buyPrice != null) {
            buyAmount = buyQuantity.multiply(buyPrice).setScale(2, RoundingMode.DOWN);
            gridLine.setBuyAmount(buyAmount);
        }
        if (buyQuantity == null || sellPrice == null) {
            return;
        }

        BigDecimal sellAmount = buyQuantity.multiply(sellPrice).setScale(2, RoundingMode.DOWN);
        gridLine.setSellAmount(sellAmount);
        if (buyAmount != null && buyAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profit = sellAmount.subtract(buyAmount).setScale(2, RoundingMode.HALF_UP);
            BigDecimal profitRate = profit.divide(buyAmount, 6, RoundingMode.HALF_UP);
            gridLine.setProfit(profit);
            gridLine.setProfitRate(profitRate);
        }
    }

    /**
     * 强制按固定比例重新计算卖出价（独立计算模式专用）
     */
    private void forceRecalcSellPrice(GridLine gridLine) {
        BigDecimal buyPrice = gridLine.getBuyPrice();
        if (buyPrice == null) {
            return;
        }
        
        // 根据网格类型确定利润率
        BigDecimal profitRate;
        switch (gridLine.getGridType()) {
            case SMALL:
                profitRate = GridPlanGenerator.SMALL_PERCENT;  // 5%
                break;
            case MEDIUM:
                profitRate = GridPlanGenerator.MEDIUM_PERCENT;  // 15%
                break;
            case LARGE:
                profitRate = GridPlanGenerator.LARGE_PERCENT;  // 30%
                break;
            default:
                profitRate = GridPlanGenerator.SMALL_PERCENT;
        }
        
        // 卖出价 = 买入价 × (1 + 利润率)
        BigDecimal sellPrice = buyPrice.multiply(BigDecimal.ONE.add(profitRate)).setScale(8, RoundingMode.HALF_UP);
        gridLine.setSellPrice(sellPrice);
        
        System.out.println(String.format(
            "[独立计算] 网格%d %s: 买%.3f → 卖%.3f (%.1f%%)",
            gridLine.getLevel(), gridLine.getGridType(), buyPrice, sellPrice, 
            profitRate.multiply(new BigDecimal("100"))
        ));
    }

    /**
     * 计算每个网格的真实累计收益
     * 从实际的交易记录中配对买卖，计算扣除手续费后的真实收益
     * ✅ 优化：批量查询所有交易记录，避免N+1查询问题
     * ✅ 优化：使用JOIN FETCH预加载GridLine，避免懒加载导致的额外查询
     * ✅ 优化：收集需要更新的网格，批量保存
     */
    private void calculateActualProfits(Strategy strategy) {
        List<GridLine> gridLines = strategy.getGridLines();
        if (gridLines == null || gridLines.isEmpty()) {
            return;
        }

        // ✅ 性能优化：一次性查询所有交易记录并JOIN FETCH GridLine，避免N+1查询
        List<TradeRecord> allTradeRecords = tradeRecordRepository
                .findByStrategyIdWithGridLineOrderByTradeTimeAsc(strategy.getId());

        if (allTradeRecords == null || allTradeRecords.isEmpty()) {
            return;
        }

        // 按gridLineId分组
        Map<Long, List<TradeRecord>> recordsByGridLine = new HashMap<>();
        for (TradeRecord record : allTradeRecords) {
            Long gridLineId = record.getGridLine().getId();
            recordsByGridLine.computeIfAbsent(gridLineId, k -> new ArrayList<>()).add(record);
        }

        // ✅ 优化：收集需要更新的网格
        List<GridLine> gridLinesToUpdate = new ArrayList<>();

        // 遍历每个网格计算收益
        for (GridLine gridLine : gridLines) {
            List<TradeRecord> tradeRecords = recordsByGridLine.get(gridLine.getId());

            if (tradeRecords == null || tradeRecords.isEmpty()) {
                continue;
            }

            // 分离买入和卖出记录
            List<TradeRecord> buyRecords = new ArrayList<>();
            List<TradeRecord> sellRecords = new ArrayList<>();
            
            for (TradeRecord record : tradeRecords) {
                if (record.getType() == TradeType.BUY) {
                    buyRecords.add(record);
                } else if (record.getType() == TradeType.SELL) {
                    sellRecords.add(record);
                }
            }

            // 配对计算收益：第1次买配第1次卖，第2次买配第2次卖...
            int pairCount = Math.min(buyRecords.size(), sellRecords.size());
            if (pairCount == 0) {
                continue;
            }

            BigDecimal totalActualProfit = BigDecimal.ZERO;
            
            for (int i = 0; i < pairCount; i++) {
                TradeRecord buyRecord = buyRecords.get(i);
                TradeRecord sellRecord = sellRecords.get(i);
                
                // 计算该轮收益 = 卖出金额 - 买入金额 - 手续费
                BigDecimal buyAmount = buyRecord.getAmount() != null ? buyRecord.getAmount() : BigDecimal.ZERO;
                BigDecimal sellAmount = sellRecord.getAmount() != null ? sellRecord.getAmount() : BigDecimal.ZERO;
                BigDecimal buyFee = buyRecord.getFee() != null ? buyRecord.getFee() : BigDecimal.ZERO;
                BigDecimal sellFee = sellRecord.getFee() != null ? sellRecord.getFee() : BigDecimal.ZERO;
                
                BigDecimal pairProfit = sellAmount.subtract(buyAmount).subtract(buyFee).subtract(sellFee);
                totalActualProfit = totalActualProfit.add(pairProfit);
            }

            // 设置真实累计收益
            BigDecimal oldProfit = gridLine.getActualProfit();
            BigDecimal newProfit = totalActualProfit.setScale(2, RoundingMode.HALF_UP);

            // ✅ 优化：只在收益变化时才记录日志和标记更新
            if (oldProfit == null || oldProfit.compareTo(newProfit) != 0) {
                gridLine.setActualProfit(newProfit);
                gridLinesToUpdate.add(gridLine);

                // 只输出变化的网格收益信息
                if (pairCount > 0) {
                    System.out.println(String.format(
                        "[真实收益] 网格%d 完成%d轮交易，累计收益=%.2f元",
                        gridLine.getLevel(), pairCount, newProfit
                    ));
                }
            }
        }

        // ✅ 优化：批量保存（由JPA的@Transactional和Hibernate的一级缓存自动优化）
        // 实际上save时会在事务提交时批量flush
        if (!gridLinesToUpdate.isEmpty()) {
            System.out.println(String.format("[收益计算] 共更新%d个网格的实际收益", gridLinesToUpdate.size()));
        }
    }

    /**
     * 更新所有已导入网格的sellPrice（基于阶梯回撤规则）
     */

    private GridLine findBestMatchLineForCreate(List<GridLine> gridLines, OcrTradeRecord record) {
        if (gridLines == null || gridLines.isEmpty() || record == null || record.getPrice() == null) {
            return null;
        }
        TradeType type = record.getType();
        BigDecimal price = record.getPrice();

        GridLine best = null;
        BigDecimal bestDiff = null;

        for (GridLine line : gridLines) {
            if (type == TradeType.BUY && line.getState() != GridLineState.WAIT_BUY) {
                continue;
            }
            if (type == TradeType.SELL && line.getState() != GridLineState.BOUGHT) {
                continue;
            }
            BigDecimal target = type == TradeType.BUY ? line.getBuyPrice() : line.getSellPrice();
            if (target == null) {
                continue;
            }
            BigDecimal diff = target.subtract(price).abs();
            if (best == null || diff.compareTo(bestDiff) < 0) {
                best = line;
                bestDiff = diff;
            }
        }

        if (best == null && type == TradeType.BUY) {
            for (GridLine line : gridLines) {
                if (line.getState() == GridLineState.WAIT_BUY) {
                    return line;
                }
            }
        }
        if (best == null && type == TradeType.SELL) {
            for (int i = gridLines.size() - 1; i >= 0; i--) {
                GridLine line = gridLines.get(i);
                if (line.getState() == GridLineState.BOUGHT) {
                    return line;
                }
            }
        }

        return best;
    }

    private void matchSingleRecord(List<OcrTradeRecord> records,
                                   List<MatchLine> matchLines,
                                   Map<Integer, GridLine> gridLineByLevel,
                                   List<TradeRecord> existingRecords) {
        for (OcrTradeRecord record : records) {
            if (record == null) {
                continue;
            }
            if (record.getType() == null || record.getPrice() == null || record.getTradeTime() == null) {
                record.setMatchStatus(OcrMatchStatus.INVALID);
                record.setMatchMessage("missing type/price/time");
                continue;
            }

            if (isDuplicate(record, existingRecords)) {
                record.setMatchStatus(OcrMatchStatus.DUPLICATE);
                record.setMatchMessage("duplicated trade");
                record.setForcedMatch(false);
                record.setOutOfRange(false);
                continue;
            }

            MatchLine matched = findClosestByPrice(matchLines, record);
            if (matched == null) {
                markUnmatched(record, "no grid line match");
                continue;
            }

            GridLine actual = gridLineByLevel.get(matched.level);
            if (actual == null) {
                markUnmatched(record, "grid line missing");
                continue;
            }

            BigDecimal expected = record.getType() == TradeType.BUY ? matched.buyPrice : matched.sellPrice;
            boolean outOfRange = isOutOfRange(record.getPrice(), expected);

            record.setMatchedGridLineId(actual.getId());
            record.setMatchedLevel(matched.level);
            record.setMatchStatus(OcrMatchStatus.MATCHED);
            record.setForcedMatch(outOfRange);
            record.setOutOfRange(outOfRange);
            record.setMatchMessage(outOfRange ? "price out of range" : "matched");
        }
    }

    private void matchSequentialRecords(List<OcrTradeRecord> records,
                                        List<MatchLine> matchLines,
                                        Map<Integer, GridLine> gridLineByLevel,
                                        List<TradeRecord> existingRecords) {
        int buyIndex = 0;
        Deque<MatchLine> openBuys = new ArrayDeque<>();

        for (OcrTradeRecord record : records) {
            if (record == null) {
                continue;
            }
            if (record.getType() == null || record.getPrice() == null || record.getTradeTime() == null) {
                record.setMatchStatus(OcrMatchStatus.INVALID);
                record.setMatchMessage("missing type/price/time");
                continue;
            }

            if (isDuplicate(record, existingRecords)) {
                record.setMatchStatus(OcrMatchStatus.DUPLICATE);
                record.setMatchMessage("duplicated trade");
                record.setForcedMatch(false);
                record.setOutOfRange(false);
                continue;
            }

            MatchLine matched = null;
            if (record.getType() == TradeType.BUY) {
                if (buyIndex < matchLines.size()) {
                    matched = matchLines.get(buyIndex);
                    buyIndex++;
                    openBuys.push(matched);
                }
            } else if (record.getType() == TradeType.SELL) {
                if (!openBuys.isEmpty()) {
                    matched = openBuys.pop();
                }
            }

            if (matched == null) {
                markUnmatched(record, record.getType() == TradeType.BUY
                        ? "no grid line left for buy"
                        : "no open buy to close");
                continue;
            }

            GridLine actual = gridLineByLevel.get(matched.level);
            if (actual == null) {
                markUnmatched(record, "grid line missing");
                continue;
            }

            BigDecimal expected = record.getType() == TradeType.BUY ? matched.buyPrice : matched.sellPrice;
            boolean outOfRange = isOutOfRange(record.getPrice(), expected);

            record.setMatchedGridLineId(actual.getId());
            record.setMatchedLevel(matched.level);
            record.setMatchStatus(OcrMatchStatus.MATCHED);
            record.setForcedMatch(outOfRange);
            record.setOutOfRange(outOfRange);
            record.setMatchMessage(outOfRange ? "price out of range" : "matched");
        }
    }

    private OcrTradeRecord findOpeningRecord(List<OcrTradeRecord> records) {
        for (OcrTradeRecord record : records) {
            if (record != null && record.isOpening()) {
                return record;
            }
        }
        return null;
    }

    private List<MatchLine> buildMatchLines(List<GridLine> gridLines,
                                            List<GridPlanGenerator.GridPlanItem> planItems,
                                            boolean resetState) {
        List<MatchLine> result = new ArrayList<>();
        Map<Integer, GridPlanGenerator.GridPlanItem> planByLevel = new HashMap<>();
        if (planItems != null) {
            for (GridPlanGenerator.GridPlanItem item : planItems) {
                planByLevel.put(item.getLevel(), item);
            }
        }

        gridLines.sort(Comparator.comparing(GridLine::getLevel));
        for (GridLine gridLine : gridLines) {
            GridPlanGenerator.GridPlanItem item = planByLevel.get(gridLine.getLevel());
            MatchLine line = new MatchLine();
            line.level = gridLine.getLevel();
            line.buyPrice = item != null ? item.getBuyPrice() : gridLine.getBuyPrice();
            line.sellPrice = item != null ? item.getSellPrice() : gridLine.getSellPrice();
            if (resetState) {
                line.state = GridLineState.WAIT_BUY;
            } else {
                line.state = gridLine.getState();
            }
            result.add(line);
        }
        return result;
    }

    private MatchLine findClosestByPrice(List<MatchLine> lines, OcrTradeRecord record) {
        BigDecimal price = record.getPrice();
        TradeType type = record.getType();
        MatchLine best = null;
        BigDecimal bestDiff = null;
        for (MatchLine line : lines) {
            if (type == TradeType.BUY && line.state != GridLineState.WAIT_BUY) {
                continue;
            }
            if (type == TradeType.SELL && line.state != GridLineState.BOUGHT) {
                continue;
            }
            BigDecimal target = type == TradeType.BUY ? line.buyPrice : line.sellPrice;
            if (target == null) {
                continue;
            }
            BigDecimal diff = target.subtract(price).abs();
            if (best == null || diff.compareTo(bestDiff) < 0) {
                best = line;
                bestDiff = diff;
            }
        }
        return best;
    }

    private boolean isOutOfRange(BigDecimal actual, BigDecimal expected) {
        if (actual == null || expected == null) {
            return false;
        }
        BigDecimal tolerance = expected.multiply(tolerancePercent).abs();
        return actual.subtract(expected).abs().compareTo(tolerance) > 0;
    }

    private void markUnmatched(OcrTradeRecord record, String message) {
        if (record == null) {
            return;
        }
        record.setMatchStatus(OcrMatchStatus.UNMATCHED);
        record.setMatchMessage(message);
        record.setMatchedGridLineId(null);
        record.setMatchedLevel(null);
        record.setForcedMatch(false);
        record.setOutOfRange(false);
    }

    private List<OcrTradeRecord> dedupeRecords(List<OcrTradeRecord> records) {
        if (records == null || records.isEmpty()) {
            return records;
        }
        Map<String, OcrTradeRecord> unique = new LinkedHashMap<>();
        for (OcrTradeRecord record : records) {
            String key = buildRecordKey(record);
            unique.putIfAbsent(key, record);
        }
        return new ArrayList<>(unique.values());
    }

    private List<OcrTradeRecord> mergeSplitBuys(List<OcrTradeRecord> records) {
        if (records == null || records.isEmpty()) {
            return records;
        }

        List<OcrTradeRecord> sorted = new ArrayList<>(records);
        sorted.sort((a, b) -> {
            if (a == null && b == null) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            LocalDateTime ta = a.getTradeTime();
            LocalDateTime tb = b.getTradeTime();
            if (ta == null && tb == null) {
                return 0;
            }
            if (ta == null) {
                return 1;
            }
            if (tb == null) {
                return -1;
            }
            return ta.toInstant(ZoneOffset.UTC).compareTo(tb.toInstant(ZoneOffset.UTC));
        });

        List<OcrTradeRecord> merged = new ArrayList<>();
        OcrTradeRecord current = null;

        for (OcrTradeRecord record : sorted) {
            if (record == null) {
                continue;
            }
            if (current == null) {
                current = record;
                continue;
            }

            if (!canMerge(current, record)) {
                merged.add(current);
                current = record;
                continue;
            }

            mergeInto(current, record);
        }

        if (current != null) {
            merged.add(current);
        }

        return merged;
    }

    private boolean canMerge(OcrTradeRecord left, OcrTradeRecord right) {
        if (left.getType() != TradeType.BUY || right.getType() != TradeType.BUY) {
            return false;
        }
        if (left.getPrice() == null || right.getPrice() == null) {
            return false;
        }
        if (left.getTradeTime() == null || right.getTradeTime() == null) {
            return false;
        }
        if (left.getPrice().compareTo(right.getPrice()) != 0) {
            return false;
        }
        return left.getTradeTime().toLocalDate().equals(right.getTradeTime().toLocalDate());
    }

    private void mergeInto(OcrTradeRecord base, OcrTradeRecord extra) {
        base.setQuantity(addNullable(base.getQuantity(), extra.getQuantity()));
        base.setAmount(addNullable(base.getAmount(), extra.getAmount()));
        base.setFee(addNullable(base.getFee(), extra.getFee()));
        base.setOpening(base.isOpening() || extra.isOpening());
        base.setClosing(base.isClosing() || extra.isClosing());

        if (base.getAmount() == null && base.getQuantity() != null && base.getPrice() != null) {
            base.setAmount(base.getQuantity().multiply(base.getPrice()));
        }
        if (base.getQuantity() == null && base.getAmount() != null && base.getPrice() != null) {
            base.setQuantity(base.getAmount().divide(base.getPrice(), 8, java.math.RoundingMode.DOWN));
        }
    }

    private BigDecimal addNullable(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return null;
        }
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.add(right);
    }

    private String buildRecordKey(OcrTradeRecord record) {
        if (record == null) {
            return "null";
        }
        String type = record.getType() != null ? record.getType().name() : "";
        String time = record.getTradeTime() != null ? record.getTradeTime().toString() : "";
        String price = record.getPrice() != null ? record.getPrice().toPlainString() : "";
        String quantity = record.getQuantity() != null ? record.getQuantity().toPlainString() : "";
        String amount = record.getAmount() != null ? record.getAmount().toPlainString() : "";
        String fee = record.getFee() != null ? record.getFee().toPlainString() : "";
        return type + "|" + time + "|" + price + "|" + quantity + "|" + amount + "|" + fee;
    }

    private boolean isDuplicate(OcrTradeRecord record, List<TradeRecord> existingRecords) {
        if (existingRecords == null || existingRecords.isEmpty()) {
            return false;
        }
        for (TradeRecord existing : existingRecords) {
            if (existing.getType() != record.getType()) {
                continue;
            }
            if (!isPriceClose(existing.getPrice(), record.getPrice())) {
                continue;
            }
            if (!isQuantityClose(existing.getQuantity(), record.getQuantity())) {
                continue;
            }
            if (!isAmountClose(existing.getAmount(), record.getAmount())) {
                continue;
            }
            if (!isTimeClose(existing.getTradeTime(), record.getTradeTime())) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean isTimeClose(LocalDateTime left, LocalDateTime right) {
        if (left == null || right == null) {
            return false;
        }
        Duration diff = Duration.between(left, right).abs();
        return diff.getSeconds() <= timeWindowSeconds;
    }

    private boolean isPriceClose(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return false;
        }
        BigDecimal tolerance = left.multiply(tolerancePercent).abs();
        return left.subtract(right).abs().compareTo(tolerance) <= 0;
    }

    private boolean isQuantityClose(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return true;
        }
        return left.compareTo(right) == 0;
    }

    private boolean isAmountClose(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return true;
        }
        BigDecimal tolerance = left.multiply(tolerancePercent).abs();
        return left.subtract(right).abs().compareTo(tolerance) <= 0;
    }

    private String[] extractNameAndSymbol(String rawText) {
        String name = null;
        String symbol = null;
        if (rawText == null || rawText.isBlank()) {
            return new String[] { null, null };
        }
        String normalized = rawText
                .replace('：', ':')
                .replace('，', ',')
                .replace("\t", " ");
        String[] lines = normalized.split("\\r?\\n");

        List<String> nonEmptyLines = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                nonEmptyLines.add(trimmed);
            }
        }

        Integer codeLineIndex = null;
        String shortName = null;
        if (!nonEmptyLines.isEmpty()) {
            for (int i = 0; i < nonEmptyLines.size(); i++) {
                String line = nonEmptyLines.get(i);
                Matcher codeMatcher = CODE_ONLY_PATTERN.matcher(line);
                if (codeMatcher.find()) {
                    symbol = codeMatcher.group(1).trim();
                    String candidate = line.replace(symbol, "")
                            .replaceAll("[()（）\"'\\[\\]{}:：,，\\-]", " ")
                            .trim();
                    if (isLikelyName(candidate)) {
                        shortName = candidate;
                    }
                    codeLineIndex = i;
                    break;
                }
            }

            if (codeLineIndex != null && codeLineIndex + 1 < nonEmptyLines.size()) {
                String nextLine = nonEmptyLines.get(codeLineIndex + 1);
                if (!nextLine.matches(".*\\d{6}.*") && isLikelyName(nextLine)) {
                    name = nextLine.trim();
                }
            }
            if (name == null && shortName != null) {
                name = shortName;
            }
        }

        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (symbol == null) {
                Matcher symbolMatcher = SYMBOL_PATTERN.matcher(trimmed);
                if (symbolMatcher.find()) {
                    symbol = symbolMatcher.group(2).trim();
                }
            }
            if (name == null) {
                Matcher nameMatcher = NAME_PATTERN.matcher(trimmed);
                if (nameMatcher.find()) {
                    name = nameMatcher.group(2).trim();
                }
            }
            if (name != null && symbol != null) {
                break;
            }
        }
        if (symbol == null || name == null) {
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (symbol == null) {
                    Matcher codeMatcher = CODE_WITH_NAME_PATTERN.matcher(trimmed);
                    if (codeMatcher.find()) {
                        symbol = codeMatcher.group(1).trim();
                        if (name == null) {
                            name = codeMatcher.group(2).trim();
                        }
                    }
                }
                if (symbol == null || name == null) {
                    Matcher nameCodeMatcher = NAME_WITH_CODE_PATTERN.matcher(trimmed);
                    if (nameCodeMatcher.find()) {
                        if (name == null) {
                            name = nameCodeMatcher.group(1).trim();
                        }
                        if (symbol == null) {
                            symbol = nameCodeMatcher.group(2).trim();
                        }
                    }
                }
                if (name != null && symbol != null) {
                    break;
                }
            }
        }
        if (symbol == null) {
            Matcher codeMatcher = CODE_ONLY_PATTERN.matcher(normalized);
            if (codeMatcher.find()) {
                symbol = codeMatcher.group(1).trim();
            }
        }
        if (symbol != null && name == null) {
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                if (!line.contains(symbol)) {
                    continue;
                }
                String candidate = line.replace(symbol, "")
                        .replaceAll("[()（）\"'\\[\\]{}:：,，\\-]", " ")
                        .trim();
                if (isLikelyName(candidate)) {
                    name = candidate;
                    break;
                }
            }
        }
        return new String[] { name, symbol };
    }

    private boolean isLikelyName(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.length() < 2) {
            return false;
        }
        if (trimmed.matches("\\d+")) {
            return false;
        }
        return trimmed.matches(".*[A-Za-z\\u4e00-\\u9fa5].*");
    }

    private static class MatchLine {
        private int level;
        private BigDecimal buyPrice;
        private BigDecimal sellPrice;
        private GridLineState state;
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
