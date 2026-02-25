package com.gridtrading.service.ocr;

import com.gridtrading.controller.dto.OcrMatchStatus;
import com.gridtrading.controller.dto.OcrRecognizeResponse;
import com.gridtrading.controller.dto.OcrTradeRecord;
import com.gridtrading.domain.*;
import com.gridtrading.repository.GridLineRepository;
import com.gridtrading.repository.StrategyRepository;
import com.gridtrading.repository.TradeRecordRepository;
import com.gridtrading.service.grid.GridPlanGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * OCR识别与匹配服务
 */
@Service
public class OcrService {

    private final BaiduOcrClient baiduOcrClient;
    private final EastMoneyParser eastMoneyParser;
    private final StrategyRepository strategyRepository;
    private final GridLineRepository gridLineRepository;
    private final TradeRecordRepository tradeRecordRepository;

    private final BigDecimal tolerancePercent;
    private final long timeWindowSeconds;

    public OcrService(
            BaiduOcrClient baiduOcrClient,
            EastMoneyParser eastMoneyParser,
            StrategyRepository strategyRepository,
            GridLineRepository gridLineRepository,
            TradeRecordRepository tradeRecordRepository,
            @Value("${ocr.match.tolerance-percent:0.005}") BigDecimal tolerancePercent,
                @Value("${ocr.match.time-window-seconds:30}") long timeWindowSeconds
    ) {
        this.baiduOcrClient = baiduOcrClient;
        this.eastMoneyParser = eastMoneyParser;
        this.strategyRepository = strategyRepository;
        this.gridLineRepository = gridLineRepository;
        this.tradeRecordRepository = tradeRecordRepository;
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

    private MatchLine findNextByState(List<MatchLine> lines, TradeType type) {
        if (type == TradeType.BUY) {
            return findFirstByState(lines, GridLineState.WAIT_BUY);
        }
        return findFirstByState(lines, GridLineState.BOUGHT, GridLineState.WAIT_SELL);
    }

    private MatchLine findFirstByState(List<MatchLine> lines, GridLineState... states) {
        for (MatchLine line : lines) {
            for (GridLineState state : states) {
                if (line.state == state) {
                    return line;
                }
            }
        }
        return null;
    }

    private MatchLine findLastByState(List<MatchLine> lines, GridLineState... states) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            MatchLine line = lines.get(i);
            for (GridLineState state : states) {
                if (line.state == state) {
                    return line;
                }
            }
        }
        return null;
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
            if (type == TradeType.SELL && line.state != GridLineState.BOUGHT && line.state != GridLineState.WAIT_SELL) {
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

    private void advanceState(MatchLine line, TradeType type) {
        if (line == null || type == null) {
            return;
        }
        if (type == TradeType.BUY) {
            line.state = GridLineState.BOUGHT;
        } else {
            line.state = GridLineState.WAIT_BUY;
        }
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

    private static class MatchLine {
        private int level;
        private BigDecimal buyPrice;
        private BigDecimal sellPrice;
        private GridLineState state;
    }
}
