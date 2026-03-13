package com.gridtrading.service.ocr;

import com.gridtrading.controller.dto.OcrMatchStatus;
import com.gridtrading.controller.dto.OcrRecognizeResponse;
import com.gridtrading.controller.dto.OcrTradeRecord;
import com.gridtrading.domain.*;
import com.gridtrading.engine.GridEngine;
import com.gridtrading.repository.GridLineRepository;
import com.gridtrading.repository.StrategyRepository;
import com.gridtrading.repository.TradeRecordRepository;
import com.gridtrading.constants.GridConstants;
import com.gridtrading.service.GridService;
import com.gridtrading.service.PositionCalculator;
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
    private final GridService gridService;

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
            GridService gridService,
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
        this.gridService = gridService;
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
                System.out.println("[OCR原始文本] 文件: " + file.getOriginalFilename());
                System.out.println("[OCR原始文本] 内容:\n" + rawText);
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
        // records = mergeSplitBuys(records);  // 禁用合并，保留所有交易记录
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

        int buyIndex = 0;
        Deque<GridLine> openBuys = new ArrayDeque<>();
        // 跟踪最后一次买入的网格和价格（用于相同价格连续买入匹配到同一网格）
        GridLine lastBuyGrid = null;
        BigDecimal lastBuyPrice = null;

        System.out.println("[OCR导入] 开始按时间顺序匹配记录，共 " + records.size() + " 条记录");
        for (OcrTradeRecord record : records) {
            if (record == null || record.getType() == null || record.getPrice() == null) {
                continue;
            }

            GridLine gridLine = null;
            boolean isConsecutiveBuy = false; // 标记是否是相同价格连续买入（不增加买入次数）
            if (record.getType().isBuy()) {
                // 判断是否是相同价格的连续买入
                boolean isSamePriceAsLast = lastBuyPrice != null && lastBuyPrice.compareTo(record.getPrice()) == 0;

                if (isSamePriceAsLast && lastBuyGrid != null) {
                    // 相同价格，匹配到上一次的网格
                    gridLine = lastBuyGrid;
                    isConsecutiveBuy = true; // 标记为连续买入，不增加buyCount
                    String typeDesc = record.getType() == TradeType.OPENING_BUY ? "建仓-买入" : "买入";
                    System.out.println("[OCR导入] " + typeDesc + "记录: 时间=" + record.getTradeTime() +
                        " 价格=" + record.getPrice() + " → 匹配网格 " + gridLine.getLevel() + " (相同价格连续买入，合并计数)");
                    // 同样加入openBuys，确保每条买入都有对应的卖出匹配
                    openBuys.push(gridLine);
                } else {
                    // 优先查找已经完成至少一轮买卖（已全部卖出）且买入价匹配的网格
                    GridLine matchedSoldGrid = orderedGridLines.stream()
                            .filter(gl -> gl.getState() == GridLineState.WAIT_BUY
                                    && gl.getBuyCount() > 0 // 必须曾经买入过（不是未使用的初始网格）
                                    && gl.getSellCount() >= gl.getBuyCount() // 已经全部卖出，可重新买入
                                    && gl.getBuyPrice() != null
                                    && gl.getBuyPrice().compareTo(record.getPrice()) == 0)
                            .findFirst()
                            .orElse(null);

                    if (matchedSoldGrid != null) {
                        // 找到已卖出的匹配网格，重新买入该网格
                        gridLine = matchedSoldGrid;
                        String typeDesc = record.getType() == TradeType.OPENING_BUY ? "建仓-买入" : "买入";
                        System.out.println("[OCR导入] " + typeDesc + "记录: 时间=" + record.getTradeTime() +
                            " 价格=" + record.getPrice() + " → 匹配网格 " + gridLine.getLevel() + " (重新买入已卖出网格)");
                        openBuys.push(gridLine);
                        lastBuyGrid = gridLine;
                        lastBuyPrice = record.getPrice();
                    } else {
                        // 没有匹配的已卖出网格，按顺序取下一个新网格
                        if (buyIndex < orderedGridLines.size()) {
                            gridLine = orderedGridLines.get(buyIndex);
                            String typeDesc = record.getType() == TradeType.OPENING_BUY ? "建仓-买入" : "买入";
                            System.out.println("[OCR导入] " + typeDesc + "记录: 时间=" + record.getTradeTime() +
                                " 价格=" + record.getPrice() + " → 匹配网格 " + gridLine.getLevel());
                            buyIndex++;
                            openBuys.push(gridLine);
                            lastBuyGrid = gridLine;
                            lastBuyPrice = record.getPrice();
                        } else {
                            System.out.println("[OCR导入] 买入记录超出网格范围: " + record.getTradeTime() + " 价格=" + record.getPrice());
                            lastBuyGrid = null;
                            lastBuyPrice = null;
                            continue;
                        }
                    }
                }
            } else if (record.getType() == TradeType.SELL) {
                if (!openBuys.isEmpty()) {
                    gridLine = openBuys.pop();
                    System.out.println("[OCR导入] 卖出记录: 时间=" + record.getTradeTime() +
                        " 价格=" + record.getPrice() + " → 匹配网格 " + gridLine.getLevel());
                    // 卖出后重置连续买入跟踪
                    lastBuyGrid = null;
                    lastBuyPrice = null;
                } else {
                    System.out.println("[OCR导入] 没有可匹配的买入网格用于卖出: " + record.getTradeTime() + " 价格=" + record.getPrice());
                    continue;
                }
            }

            if (gridLine == null) {
                continue;
            }
            normalizeRecordAmounts(record);
            applyRecordToGridLine(gridLine, record, strategy, isConsecutiveBuy);

            TradeRecord tradeRecord = buildTradeRecord(strategy, gridLine, record);
            tradeRecordRepository.save(tradeRecord);
        }

        // 计算每个网格的真实累计收益
        calculateActualProfits(strategy);

        // ✅ 计算持仓相关字段（成本价、买入均价、持仓盈亏等）
        positionCalculator.calculateAndUpdate(strategy);

        // ✅ 设置lastPrice：优先使用截图中的现价，如果没有则使用最新交易价格
        BigDecimal currentPrice = findCurrentPrice(records);
        if (currentPrice != null) {
            strategy.setLastPrice(currentPrice);
            System.out.println("[OCR导入] 使用截图中的现价: " + currentPrice);
        } else {
            BigDecimal latestPrice = findLatestTradePrice(records);
            if (latestPrice != null) {
                strategy.setLastPrice(latestPrice);
                System.out.println("[OCR导入] 使用最新交易价格: " + latestPrice);
            }
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

        List<MatchLine> matchLines = buildMatchLines(gridLines, null, opening != null);

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
        // 优先查找建仓买入
        for (OcrTradeRecord record : records) {
            if (record.getType() == TradeType.OPENING_BUY && record.getPrice() != null) {
                return record;
            }
        }
        // 其次查找普通买入
        for (OcrTradeRecord record : records) {
            if (record.getType().isBuy() && record.getPrice() != null) {
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

        BigDecimal smallGap = basePrice.multiply(GridConstants.SMALL_PROFIT_RATE);
        BigDecimal mediumGap = basePrice.multiply(GridConstants.MEDIUM_PROFIT_RATE);
        BigDecimal largeGap = basePrice.multiply(GridConstants.LARGE_PROFIT_RATE);

        strategy.setSmallGap(smallGap);
        strategy.setMediumGap(mediumGap);
        strategy.setLargeGap(largeGap);

        strategy.setGridCountDown(GridConstants.TOTAL_GRID_COUNT);
        strategy.setGridCountUp(0);
        strategy.setGridPercent(GridConstants.SMALL_PROFIT_RATE);

        BigDecimal maxCapital = amountPerGrid.multiply(BigDecimal.valueOf(GridConstants.TOTAL_GRID_COUNT));
        strategy.setMaxCapital(maxCapital);
        strategy.setAvailableCash(maxCapital);
        strategy.setStatus(StrategyStatus.RUNNING);
        strategy.setGridModelVersion("v2.0");
        strategy.setGridSummary("小网13/中网4/大网2");

        // 生成网格线（使用与 StrategyService 相同的逻辑）
        generateGridLinesForOcr(strategy, basePrice, amountPerGrid);

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

    /**
     * 为 OCR 导入生成网格线（与 StrategyService 相同的逻辑）
     */
    private void generateGridLinesForOcr(Strategy strategy, BigDecimal basePrice, BigDecimal amountPerGrid) {
        BigDecimal decreaseFactor = BigDecimal.ONE.subtract(GridConstants.SMALL_PROFIT_RATE);

        BigDecimal lastSmallBuyPrice = basePrice;
        BigDecimal lastMediumBuyPrice = null;
        BigDecimal secondMediumBuyPrice = null;
        int mediumCount = 0;

        for (int i = 0; i < GridConstants.TOTAL_GRID_COUNT; i++) {
            GridType gridType = GridConstants.getGridType(i + 1);
            int level = i + 1;

            BigDecimal buyPrice;
            BigDecimal sellPrice;

            if (gridType == GridType.SMALL) {
                if (level == 1) {
                    buyPrice = basePrice;
                    sellPrice = basePrice.multiply(BigDecimal.ONE.add(GridConstants.SMALL_PROFIT_RATE))
                            .setScale(3, RoundingMode.HALF_UP);
                } else {
                    buyPrice = lastSmallBuyPrice.multiply(decreaseFactor)
                            .setScale(3, RoundingMode.DOWN);
                    sellPrice = lastSmallBuyPrice.setScale(3, RoundingMode.HALF_UP);
                }
                lastSmallBuyPrice = buyPrice;

            } else if (gridType == GridType.MEDIUM) {
                mediumCount++;
                buyPrice = lastSmallBuyPrice;

                if (level == 5) {
                    sellPrice = basePrice.setScale(3, RoundingMode.HALF_UP);
                } else {
                    sellPrice = lastMediumBuyPrice != null ? lastMediumBuyPrice.setScale(3, RoundingMode.HALF_UP) : basePrice;
                }
                lastMediumBuyPrice = buyPrice;

                if (mediumCount == 2) {
                    secondMediumBuyPrice = buyPrice;
                }

            } else { // LARGE
                buyPrice = lastSmallBuyPrice;

                if (level == 10) {
                    sellPrice = basePrice.setScale(3, RoundingMode.HALF_UP);
                } else {
                    sellPrice = secondMediumBuyPrice != null ? secondMediumBuyPrice.setScale(3, RoundingMode.HALF_UP) : basePrice;
                }
            }

            GridLine line = new GridLine();
            line.setStrategy(strategy);
            line.setGridType(gridType);
            line.setLevel(level);
            line.setBuyPrice(buyPrice.setScale(3, RoundingMode.DOWN));
            line.setSellPrice(sellPrice);
            line.setBuyTriggerPrice(GridConstants.calculateBuyTriggerPrice(buyPrice));
            line.setSellTriggerPrice(GridConstants.calculateSellTriggerPrice(sellPrice));
            line.setBuyAmount(amountPerGrid);

            BigDecimal buyQuantity = amountPerGrid.divide(line.getBuyPrice(), 8, RoundingMode.DOWN);
            line.setBuyQuantity(buyQuantity);

            BigDecimal sellAmount = buyQuantity.multiply(line.getSellPrice()).setScale(2, RoundingMode.DOWN);
            line.setSellAmount(sellAmount);

            BigDecimal profit = sellAmount.subtract(amountPerGrid);
            line.setProfit(profit);

            BigDecimal profitRate = profit.divide(amountPerGrid, 6, RoundingMode.HALF_UP);
            line.setProfitRate(profitRate);

            line.setState(GridLineState.WAIT_BUY);
            strategy.getGridLines().add(line);
        }
    }

    private void applyRecordToGridLine(GridLine gridLine, OcrTradeRecord record, Strategy strategy, boolean isConsecutiveBuy) {
        if (gridLine == null || record == null || record.getType() == null || record.getPrice() == null) {
            return;
        }
        if (record.getType().isBuy()) {
            applyBuyRecord(gridLine, record, strategy, isConsecutiveBuy);
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

    private void applyBuyRecord(GridLine gridLine, OcrTradeRecord record, Strategy strategy, boolean isConsecutiveBuy) {
        BigDecimal price = record.getPrice();
        gridLine.setActualBuyPrice(price);
        gridLine.setBuyPrice(price);
        gridLine.setBuyTriggerPrice(price.add(new BigDecimal("0.002")));

        // 多次买入时累加金额和数量，而不是覆盖
        if (record.getAmount() != null) {
            BigDecimal currentAmount = gridLine.getBuyAmount() != null ? gridLine.getBuyAmount() : BigDecimal.ZERO;
            gridLine.setBuyAmount(currentAmount.add(record.getAmount()));
        }
        if (record.getQuantity() != null) {
            BigDecimal currentQuantity = gridLine.getBuyQuantity() != null ? gridLine.getBuyQuantity() : BigDecimal.ZERO;
            gridLine.setBuyQuantity(currentQuantity.add(record.getQuantity()));
        }

        // 增加买入次数统计（连续同价买入不增加计数，只累加金额数量）
        if (!isConsecutiveBuy) {
            gridLine.setBuyCount(gridLine.getBuyCount() + 1);
            System.out.println("[OCR-BUY] 网格" + gridLine.getLevel() + " buyCount -> " + gridLine.getBuyCount());
        } else {
            System.out.println("[OCR-BUY] 网格" + gridLine.getLevel() + " 连续同价买入，buyCount保持不变: " + gridLine.getBuyCount());
        }

        // ✅ 重新计算当前网格的卖出价（基于最新买入价和现有规则）
        gridEngine.updateCurrentGridSellPriceAfterBuy(strategy, gridLine);
        System.out.println("[OCR-BUY] 网格" + gridLine.getLevel() + " 更新卖出价为: " + gridLine.getSellPrice());

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

        // ✅ 修复：如果是小网，同步更新后续的中网和大网价格，保持同组网格价格一致
        if (gridLine.getGridType() == GridType.SMALL) {
            List<GridLine> allGridLines = new ArrayList<>(strategy.getGridLines());
            allGridLines.sort(Comparator.comparingInt(GridLine::getLevel));

            int currentLevel = gridLine.getLevel();
            // 遍历后续网格，直到遇到下一个小网
            for (GridLine gl : allGridLines) {
                if (gl.getLevel() <= currentLevel) {
                    continue;
                }
                if (gl.getGridType() == GridType.SMALL) {
                    break; // 遇到下一个小网，停止更新
                }
                // 更新中网/大网的买入价和卖出价
                gl.setBuyPrice(price);
                gl.setBuyTriggerPrice(price.add(new BigDecimal("0.002")));

                // 重新计算卖出价
                BigDecimal glProfitRate = gl.getProfitRate();
                if (glProfitRate == null || glProfitRate.compareTo(BigDecimal.ZERO) <= 0) {
                    if (gl.getGridType() == GridType.MEDIUM) {
                        glProfitRate = new BigDecimal("0.15");
                    } else {
                        glProfitRate = new BigDecimal("0.30");
                    }
                }
                BigDecimal glNewSellPrice = price.multiply(BigDecimal.ONE.add(glProfitRate))
                    .setScale(3, RoundingMode.HALF_UP);
                gl.setSellPrice(glNewSellPrice);
                gl.setSellTriggerPrice(glNewSellPrice.subtract(new BigDecimal("0.002")));

                System.out.println("[同步更新] 网格" + gl.getLevel() + " " + gl.getGridType() +
                    ": 买" + price + " → 卖" + glNewSellPrice + " (" + glProfitRate.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP) + "%)");

                recalcLineTotals(gl, strategy);
            }
        }

        recalcLineTotals(gridLine, strategy);
    }

    private void applySellRecord(GridLine gridLine, OcrTradeRecord record, Strategy strategy) {
        BigDecimal price = record.getPrice();
        gridLine.setActualSellPrice(price);

        // 增加卖出次数统计
        gridLine.setSellCount(gridLine.getSellCount() + 1);
        System.out.println("[OCR-SELL] 网格" + gridLine.getLevel() + " sellCount -> " + gridLine.getSellCount());

        // 卖出完成后，重置卖出价为建议卖出价（按买入价重新计算）
        // 因为新一轮还没买入，需要按原计算逻辑以买入价去计算卖出价
        BigDecimal newSellPrice = gridService.calculateSuggestedSellPrice(strategy, gridLine);
        if (newSellPrice != null) {
            gridLine.setSellPrice(newSellPrice);
            gridLine.setSellTriggerPrice(newSellPrice.subtract(new BigDecimal("0.002")));
            System.out.println("[OCR-SELL] 网格" + gridLine.getLevel() + " 重置卖出价为: " + newSellPrice);
        }

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
                profitRate = GridConstants.SMALL_PROFIT_RATE;  // 5%
                break;
            case MEDIUM:
                profitRate = GridConstants.MEDIUM_PROFIT_RATE;  // 15%
                break;
            case LARGE:
                profitRate = GridConstants.LARGE_PROFIT_RATE;  // 30%
                break;
            default:
                profitRate = GridConstants.SMALL_PROFIT_RATE;
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
                if (record.getType().isBuy()) {  // 包含 BUY 和 OPENING_BUY
                    buyRecords.add(record);
                } else if (record.getType() == TradeType.SELL) {
                    sellRecords.add(record);
                }
            }

            // 配对计算收益：按FIFO轮次配对（先买先卖），只计算已完成的买卖对的收益
            // 未卖出的持仓不计算到实际收益中（属于浮动盈亏）
            int pairCount = Math.min(buyRecords.size(), sellRecords.size());
            if (pairCount == 0) {
                continue;
            }

            BigDecimal totalActualProfit = BigDecimal.ZERO;

            // 按轮次配对计算：每一轮卖出对应最早的一笔买入
            // 实际收益 = (实际卖出价 - 实际买入价) × 实际交易数量
            for (int i = 0; i < pairCount; i++) {
                TradeRecord buyRecord = buyRecords.get(i);
                TradeRecord sellRecord = sellRecords.get(i);

                BigDecimal buyPrice = buyRecord.getPrice();
                BigDecimal sellPrice = sellRecord.getPrice();
                // 使用卖出记录的数量（实际卖出数量）
                BigDecimal quantity = sellRecord.getQuantity() != null ? sellRecord.getQuantity() : BigDecimal.ZERO;

                // 单轮收益 = (卖出价 - 买入价) × 数量
                BigDecimal roundProfit = sellPrice.subtract(buyPrice).multiply(quantity);
                totalActualProfit = totalActualProfit.add(roundProfit);
            }

            // 设置真实累计收益（所有轮次收益总和）
            BigDecimal newProfit = totalActualProfit.setScale(2, RoundingMode.HALF_UP);

            // 计算预计收益：(卖出价 - 买入价) × 数量
            // 没有实际卖出价用建议卖出价，没有实际买入价用建议买入价
            BigDecimal expectedProfit = BigDecimal.ZERO;
            BigDecimal sellPrice = gridLine.getActualSellPrice() != null
                ? gridLine.getActualSellPrice()
                : gridLine.getSellPrice();
            BigDecimal buyPrice = gridLine.getActualBuyPrice() != null
                ? gridLine.getActualBuyPrice()
                : gridLine.getBuyPrice();
            BigDecimal quantity = gridLine.getBuyQuantity() != null
                ? gridLine.getBuyQuantity()
                : BigDecimal.ZERO;

            if (sellPrice != null && buyPrice != null && quantity != null) {
                expectedProfit = sellPrice.subtract(buyPrice).multiply(quantity)
                    .setScale(2, RoundingMode.HALF_UP);
            }

            // ✅ 优化：只在收益变化时才记录日志和标记更新
            BigDecimal oldProfit = gridLine.getActualProfit();
            boolean profitChanged = oldProfit == null || oldProfit.compareTo(newProfit) != 0;
            boolean expectedChanged = gridLine.getExpectedProfit() == null || gridLine.getExpectedProfit().compareTo(expectedProfit) != 0;

            if (profitChanged || expectedChanged) {
                gridLine.setActualProfit(newProfit);
                gridLine.setExpectedProfit(expectedProfit);
                gridLinesToUpdate.add(gridLine);

                // 只输出变化的网格收益信息
                if (pairCount > 0 || expectedProfit.compareTo(BigDecimal.ZERO) != 0) {
                    System.out.println(String.format(
                        "[收益更新] 网格%d 完成%d轮交易，已实现收益=%.2f元，预计收益=%.2f元",
                        gridLine.getLevel(), pairCount, newProfit, expectedProfit
                    ));
                }
            }
        }

        // ✅ 批量保存更新后的网格
        if (!gridLinesToUpdate.isEmpty()) {
            gridLineRepository.saveAll(gridLinesToUpdate);
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

    /**
     * 按时间顺序匹配记录（基于数量的智能匹配）
     *
     * 核心逻辑：
     * 1. 从建仓记录获取基础交易数量（baseQuantity）
     * 2. 累计买入数量，当达到baseQuantity时分配一个网格
     * 3. 支持一次买入跨多网格，也支持多次买入合并为一网格
     * 4. 卖出同样基于数量匹配（FIFO：先买先卖）
     */
    private void matchSequentialRecords(List<OcrTradeRecord> records,
                                        List<MatchLine> matchLines,
                                        Map<Integer, GridLine> gridLineByLevel,
                                        List<TradeRecord> existingRecords) {
        // 1. 从建仓记录获取基础交易数量
        BigDecimal baseQuantity = findBaseQuantity(records);
        System.out.println("[OCR匹配] 基础交易数量: " + baseQuantity);

        int buyIndex = 0;
        BigDecimal accumulatedBuyQty = BigDecimal.ZERO;  // 累计买入数量（用于分配网格）
        List<MatchLine> openBuys = new ArrayList<>();    // 已买入但未卖完的网格列表（FIFO顺序）

        // 记录每个网格的剩余持仓数量
        Map<Integer, BigDecimal> gridRemainingQty = new HashMap<>();

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

            if (record.getType().isBuy()) {
                // 买入处理：基于数量分配网格
                BigDecimal recordQty = record.getQuantity();
                if (recordQty == null || recordQty.compareTo(BigDecimal.ZERO) <= 0) {
                    record.setMatchStatus(OcrMatchStatus.INVALID);
                    record.setMatchMessage("invalid quantity");
                    continue;
                }

                // 累计买入数量
                accumulatedBuyQty = accumulatedBuyQty.add(recordQty);
                String typeDesc = record.getType() == TradeType.OPENING_BUY ? "建仓-买入" : "买入";
                System.out.println("[OCR匹配] " + typeDesc + ": 价格=" + record.getPrice() + " 数量=" + recordQty +
                                 " 累计=" + accumulatedBuyQty + " 基础=" + baseQuantity);

                // 计算应该分配多少个网格
                int gridsToAllocate = 0;
                if (baseQuantity != null && baseQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    // 计算累计数量可以分配多少个完整网格
                    int totalGrids = accumulatedBuyQty.divide(baseQuantity, 0, RoundingMode.DOWN).intValue();
                    gridsToAllocate = totalGrids - openBuys.size();
                } else {
                    // 没有基础数量，每条记录一个网格
                    gridsToAllocate = 1;
                }

                // 分配网格
                MatchLine matched = null;
                for (int i = 0; i < gridsToAllocate && buyIndex < matchLines.size(); i++) {
                    matched = matchLines.get(buyIndex);
                    buyIndex++;
                    openBuys.add(matched);  // 添加到列表末尾（FIFO）
                    gridRemainingQty.put(matched.level, baseQuantity);
                    System.out.println("[OCR匹配]   -> 分配网格 " + matched.level);
                }

                if (matched == null && gridsToAllocate == 0) {
                    // 这条记录的数量不足以分配新网格，但属于当前正在累积的网格
                    // 找到最近分配的网格（列表最后一个）
                    if (!openBuys.isEmpty()) {
                        matched = openBuys.get(openBuys.size() - 1);
                    }
                }

                if (matched == null) {
                    markUnmatched(record, "no grid line left for buy");
                    continue;
                }

                GridLine actual = gridLineByLevel.get(matched.level);
                if (actual == null) {
                    markUnmatched(record, "grid line missing");
                    continue;
                }

                BigDecimal expected = matched.buyPrice;
                boolean outOfRange = isOutOfRange(record.getPrice(), expected);

                record.setMatchedGridLineId(actual.getId());
                record.setMatchedLevel(matched.level);
                record.setMatchStatus(OcrMatchStatus.MATCHED);
                record.setForcedMatch(outOfRange);
                record.setOutOfRange(outOfRange);
                record.setMatchMessage(outOfRange ? "price out of range" : "matched");

            } else if (record.getType() == TradeType.SELL) {
                // 卖出处理：基于数量匹配已买入的网格（FIFO：先买先卖）
                BigDecimal recordQty = record.getQuantity();
                if (recordQty == null || recordQty.compareTo(BigDecimal.ZERO) <= 0) {
                    record.setMatchStatus(OcrMatchStatus.INVALID);
                    record.setMatchMessage("invalid quantity");
                    continue;
                }

                System.out.println("[OCR匹配] 卖出: 价格=" + record.getPrice() + " 数量=" + recordQty);

                // 找到第一个有剩余持仓的网格（FIFO）
                MatchLine matched = null;
                for (MatchLine openGrid : openBuys) {
                    BigDecimal remaining = gridRemainingQty.get(openGrid.level);
                    if (remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0) {
                        matched = openGrid;
                        break;
                    }
                }

                if (matched == null) {
                    markUnmatched(record, "no open buy to close");
                    continue;
                }

                // 更新剩余持仓
                BigDecimal remaining = gridRemainingQty.get(matched.level);
                if (remaining != null) {
                    remaining = remaining.subtract(recordQty);
                    gridRemainingQty.put(matched.level, remaining);
                    System.out.println("[OCR匹配]   -> 网格" + matched.level + " 剩余数量=" + remaining);

                    // 如果剩余数量<=0，从openBuys中移除
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                        openBuys.remove(matched);
                        System.out.println("[OCR匹配]   -> 网格" + matched.level + " 已清仓");
                    }
                }

                GridLine actual = gridLineByLevel.get(matched.level);
                if (actual == null) {
                    markUnmatched(record, "grid line missing");
                    continue;
                }

                BigDecimal expected = matched.sellPrice;
                boolean outOfRange = isOutOfRange(record.getPrice(), expected);

                record.setMatchedGridLineId(actual.getId());
                record.setMatchedLevel(matched.level);
                record.setMatchStatus(OcrMatchStatus.MATCHED);
                record.setForcedMatch(outOfRange);
                record.setOutOfRange(outOfRange);
                record.setMatchMessage(outOfRange ? "price out of range" : "matched");
            }
        }
    }

    /**
     * 从建仓记录获取基础交易数量
     */
    private BigDecimal findBaseQuantity(List<OcrTradeRecord> records) {
        // 优先从建仓买入类型获取
        for (OcrTradeRecord record : records) {
            if (record != null && record.getType() == TradeType.OPENING_BUY && record.getQuantity() != null) {
                System.out.println("[OCR匹配] 从建仓买入获取基础数量: " + record.getQuantity());
                return record.getQuantity();
            }
        }
        // 其次从opening标记获取
        for (OcrTradeRecord record : records) {
            if (record != null && record.isOpening() && record.getQuantity() != null) {
                return record.getQuantity();
            }
        }
        // 如果没有建仓记录，尝试从第一条买入记录获取
        for (OcrTradeRecord record : records) {
            if (record != null && record.getType().isBuy() && record.getQuantity() != null) {
                System.out.println("[OCR匹配] 未找到建仓记录，使用第一条买入数量作为基础: " + record.getQuantity());
                return record.getQuantity();
            }
        }
        return null;
    }

    private OcrTradeRecord findOpeningRecord(List<OcrTradeRecord> records) {
        // 优先查找建仓买入类型
        for (OcrTradeRecord record : records) {
            if (record != null && record.getType() == TradeType.OPENING_BUY) {
                return record;
            }
        }
        // 其次查找opening标记
        for (OcrTradeRecord record : records) {
            if (record != null && record.isOpening()) {
                return record;
            }
        }
        return null;
    }

    private List<MatchLine> buildMatchLines(List<GridLine> gridLines,
                                            List<Object> planItems,  // 不再使用，保留参数签名以兼容
                                            boolean resetState) {
        List<MatchLine> result = new ArrayList<>();

        gridLines.sort(Comparator.comparing(GridLine::getLevel));
        for (GridLine gridLine : gridLines) {
            MatchLine line = new MatchLine();
            line.level = gridLine.getLevel();
            line.buyPrice = gridLine.getBuyPrice();
            line.sellPrice = gridLine.getSellPrice();
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

        System.out.println("[合并买入] 开始处理，原始记录数: " + records.size());

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

        System.out.println("[合并买入] 处理完成，合并后记录数: " + merged.size());
        return merged;
    }

    private boolean canMerge(OcrTradeRecord left, OcrTradeRecord right) {
        // 必须都是买入类型（包括建仓买入）
        if (!left.getType().isBuy() || !right.getType().isBuy()) {
            return false;
        }
        // 必须交易价格相同
        if (left.getPrice() == null || right.getPrice() == null) {
            return false;
        }
        if (left.getPrice().compareTo(right.getPrice()) != 0) {
            return false;
        }
        // 放宽限制：只要价格相同就可以合并（不再限制时间窗口）
        // 因为用户可能在同一天不同时间补仓相同价格
        return true;
    }

    private void mergeInto(OcrTradeRecord base, OcrTradeRecord extra) {
        System.out.println("[合并买入] 合并记录: " + base.getTradeTime() + " 数量=" + base.getQuantity() + " + " + extra.getQuantity());
        base.setQuantity(addNullable(base.getQuantity(), extra.getQuantity()));
        base.setAmount(addNullable(base.getAmount(), extra.getAmount()));
        base.setFee(addNullable(base.getFee(), extra.getFee()));
        base.setOpening(base.isOpening() || extra.isOpening());
        base.setClosing(base.isClosing() || extra.isClosing());

        // 如果任一记录是建仓买入，合并后也标记为建仓买入
        if (base.getType() == TradeType.OPENING_BUY || extra.getType() == TradeType.OPENING_BUY) {
            base.setType(TradeType.OPENING_BUY);
        }

        if (base.getAmount() == null && base.getQuantity() != null && base.getPrice() != null) {
            base.setAmount(base.getQuantity().multiply(base.getPrice()));
        }
        if (base.getQuantity() == null && base.getAmount() != null && base.getPrice() != null) {
            base.setQuantity(base.getAmount().divide(base.getPrice(), 8, java.math.RoundingMode.DOWN));
        }
        System.out.println("[合并买入] 合并后: 数量=" + base.getQuantity() + " 金额=" + base.getAmount());
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

        // 优先处理东方财富标准格式：持仓明细下一行是名称，再下一行是代码
        for (int i = 0; i < lines.length - 2; i++) {
            if (lines[i] != null && lines[i].trim().contains("持仓明细")) {
                String candidateName = lines[i+1] != null ? lines[i+1].trim() : null;
                String candidateSymbol = lines[i+2] != null ? lines[i+2].trim() : null;
                if (candidateName != null && isLikelyName(candidateName)
                    && candidateSymbol != null && candidateSymbol.matches("\\d{6}")) {
                    name = candidateName;
                    symbol = candidateSymbol;
                    break;
                }
            }
        }

        if (name == null || symbol == null) {
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
    private BigDecimal findCurrentPrice(List<OcrTradeRecord> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        
        return records.stream()
            .filter(r -> r != null && r.getCurrentPrice() != null)
            .map(OcrTradeRecord::getCurrentPrice)
            .findFirst()
            .orElse(null);
    }

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
