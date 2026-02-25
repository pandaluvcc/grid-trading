package com.gridtrading.service.ocr;

import com.gridtrading.controller.dto.OcrTradeRecord;
import com.gridtrading.domain.TradeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for EastMoney OCR text.
 */
@Component
public class EastMoneyParser {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{4}[-/\\. ]\\d{2}[-/\\. ]\\d{2}\\s+\\d{2}:\\d{2}(?::\\d{2})?)");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}[-/\\. ]\\d{2}[-/\\. ]\\d{2})");
    private static final Pattern TIME_ONLY_PATTERN = Pattern.compile("(\\d{2}:\\d{2}(?::\\d{2})?)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)");

    private static final Pattern QUANTITY_PATTERN = Pattern.compile("(数量|成交数量|成交量|成交股数)[:\\s]*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(金额|成交金额|成交额)[:\\s]*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern PRICE_PATTERN = Pattern.compile("(价格|成交价|单价)[:\\s]*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern FEE_PATTERN = Pattern.compile("(费用|手续费|佣金|过户费|印花税)[:\\s]*([0-9]+(?:\\.[0-9]+)?)");

    private static final DateTimeFormatter DATETIME_WITH_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter DATETIME_WITHOUT_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    public List<OcrTradeRecord> parse(String rawText) {
        List<OcrTradeRecord> records = new ArrayList<>();
        if (rawText == null || rawText.trim().isEmpty()) {
            return records;
        }

        String normalized = normalize(rawText);
        String[] lines = normalized.split("\\r?\\n");

        Builder current = null;
        boolean inTradeSection = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.startsWith("FILE:")) {
                if (current != null && current.hasCoreFields()) {
                    records.add(current.build());
                }
                current = null;
                inTradeSection = false;
                continue;
            }

            if (!inTradeSection) {
                if (trimmed.contains("交易记录") || trimmed.contains("成交记录") || trimmed.contains("成交明细")) {
                    inTradeSection = true;
                } else if (hasTypeHint(trimmed)) {
                    inTradeSection = true;
                    current = new Builder();
                    current.applyLine(trimmed);
                }
                if (!inTradeSection) {
                    continue;
                }
                if (current != null) {
                    continue;
                }
            }

            if (isTradeSectionEnd(trimmed)) {
                if (current != null && current.hasCoreFields()) {
                    records.add(current.build());
                }
                current = null;
                inTradeSection = false;
                continue;
            }

            if (hasTypeHint(trimmed)) {
                if (current != null && current.hasCoreFields()) {
                    records.add(current.build());
                }
                current = new Builder();
                current.applyLine(trimmed);
                continue;
            }

            if (current != null) {
                current.applyLine(trimmed);
            }
        }

        if (current != null && current.hasCoreFields()) {
            records.add(current.build());
        }

        return records;
    }

    private boolean isTradeSectionEnd(String line) {
        return line.contains("行情")
                || line.equalsIgnoreCase("K")
                || line.contains(">|")
                || line.contains("|>");
    }

    private boolean hasTypeHint(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return line.contains("买入") || line.contains("卖出") || upper.contains("BUY") || upper.contains("SELL");
    }

    private boolean hasTimeHint(String line) {
        return TIME_PATTERN.matcher(line).find();
    }

    private String normalize(String text) {
        String normalized = text
                .replace('，', ',')
                .replace('：', ':')
                .replace('－', '-')
                .replace('／', '/')
                .replace('．', '.')
                .replace("元", "")
                .replace("　", " ");

        normalized = normalized.replaceAll("(\\d{4}-\\d{2}-\\d{2})(\\d{2}:\\d{2}:\\d{2})", "$1 $2");
        normalized = normalized.replaceAll("(\\d{4}-\\d{2}-\\d{2})(\\d{2}:\\d{2})", "$1 $2");
        normalized = normalized.replaceAll("[\\t]+", " ");
        normalized = normalized.replaceAll("[ ]{2,}", " ");
        normalized = normalized.replaceAll(",", "");
        return normalized;
    }

    private static class Builder {
        private TradeType type;
        private LocalDateTime tradeTime;
        private BigDecimal quantity;
        private BigDecimal amount;
        private BigDecimal price;
        private BigDecimal fee;

        private PendingField pendingField;
        private String datePart;
        private String timePart;

        private boolean opening;
        private boolean closing;

        void applyLine(String line) {
            if (line.contains("建仓")) {
                opening = true;
            }
            if (line.contains("清仓")) {
                closing = true;
            }

            if (type == null) {
                String upper = line.toUpperCase(Locale.ROOT);
                if (line.contains("卖出") || upper.contains("SELL")) {
                    type = TradeType.SELL;
                } else if (line.contains("买入") || upper.contains("BUY")) {
                    type = TradeType.BUY;
                }
            }

            if (pendingField != null) {
                BigDecimal value = parseNumber(line);
                if (value != null) {
                    applyPending(value);
                    pendingField = null;
                }
            }

            if (tradeTime == null) {
                LocalDateTime direct = parseDateTime(line);
                if (direct != null) {
                    tradeTime = direct;
                } else {
                    String maybeDate = parseDatePart(line);
                    String maybeTime = parseTimePart(line);
                    if (maybeDate != null) {
                        datePart = maybeDate;
                    }
                    if (maybeTime != null) {
                        timePart = maybeTime;
                    }
                    if (datePart != null && timePart != null) {
                        tradeTime = parseDateTime(datePart + " " + timePart);
                    }
                }
            }

            if (quantity == null) {
                quantity = parseNumberWithPattern(line, QUANTITY_PATTERN);
            }

            if (amount == null) {
                amount = parseNumberWithPattern(line, AMOUNT_PATTERN);
            }

            if (price == null) {
                price = parseNumberWithPattern(line, PRICE_PATTERN);
            }

            if (fee == null) {
                fee = parseNumberWithPattern(line, FEE_PATTERN);
            }

            if (line.contains("数量") && quantity == null) {
                pendingField = PendingField.QUANTITY;
            } else if (line.contains("金额") && amount == null) {
                pendingField = PendingField.AMOUNT;
            } else if ((line.contains("价格") || line.contains("成交价") || line.contains("单价")) && price == null) {
                pendingField = PendingField.PRICE;
            } else if ((line.contains("费用") || line.contains("手续费") || line.contains("佣金")) && fee == null) {
                pendingField = PendingField.FEE;
            }

            if (type != null && !isDateOrTimeLine(line) && hasInlineNumbers(line)) {
                String stripped = stripTime(line);
                List<BigDecimal> numbers = parseNumbers(stripped);
                applyHeuristicNumbers(numbers);
            }
        }

        private void applyPending(BigDecimal value) {
            switch (pendingField) {
                case QUANTITY -> quantity = value;
                case AMOUNT -> amount = value;
                case PRICE -> price = value;
                case FEE -> fee = value;
            }
        }

        boolean hasCoreFields() {
            return tradeTime != null || quantity != null || amount != null || price != null || fee != null;
        }

        OcrTradeRecord build() {
            OcrTradeRecord record = new OcrTradeRecord();
            record.setType(type);
            record.setTradeTime(tradeTime);
            record.setQuantity(quantity);
            record.setAmount(amount);
            record.setPrice(price);
            record.setFee(fee);
            record.setOpening(opening);
            record.setClosing(closing);
            return record;
        }

        private LocalDateTime parseDateTime(String line) {
            Matcher matcher = TIME_PATTERN.matcher(line);
            if (!matcher.find()) {
                return null;
            }
            String raw = matcher.group(1)
                    .replace('/', '-')
                    .replace('.', '-')
                    .replace(' ', ' ');
            try {
                if (raw.length() == 16) {
                    return LocalDateTime.parse(raw, DATETIME_WITHOUT_SECONDS);
                }
                return LocalDateTime.parse(raw, DATETIME_WITH_SECONDS);
            } catch (Exception ex) {
                return null;
            }
        }

        private String parseDatePart(String line) {
            Matcher matcher = DATE_PATTERN.matcher(line);
            if (!matcher.find()) {
                return null;
            }
            return matcher.group(1).replace('/', '-').replace('.', '-');
        }

        private String parseTimePart(String line) {
            Matcher matcher = TIME_ONLY_PATTERN.matcher(line);
            if (!matcher.find()) {
                return null;
            }
            return matcher.group(1);
        }

        private BigDecimal parseNumber(String line) {
            Matcher matcher = NUMBER_PATTERN.matcher(line);
            if (!matcher.find()) {
                return null;
            }
            return new BigDecimal(matcher.group(1));
        }

        private BigDecimal parseNumberWithPattern(String line, Pattern pattern) {
            Matcher matcher = pattern.matcher(line);
            if (!matcher.find()) {
                return null;
            }
            return new BigDecimal(matcher.group(2));
        }

        private boolean hasNoLabels(String line) {
            return !line.contains("数量") && !line.contains("金额") && !line.contains("价格")
                    && !line.contains("费用") && !line.contains("手续费") && !line.contains("佣金")
                    && !line.contains("成交额") && !line.contains("成交价") && !line.contains("成交量");
        }

        private boolean hasInlineNumbers(String line) {
            return (quantity == null || amount == null || price == null || fee == null) && hasNoLabels(line);
        }

        private BigDecimal selectLikelyPrice(List<BigDecimal> numbers) {
            BigDecimal candidate = null;
            for (BigDecimal value : numbers) {
                if (value.scale() > 0) {
                    if (candidate == null || value.compareTo(candidate) < 0) {
                        candidate = value;
                    }
                }
            }
            return candidate != null ? candidate : smallest(numbers);
        }

        private BigDecimal smallest(List<BigDecimal> numbers) {
            BigDecimal min = numbers.get(0);
            for (BigDecimal value : numbers) {
                if (value.compareTo(min) < 0) {
                    min = value;
                }
            }
            return min;
        }

        private BigDecimal selectLikelyQuantity(List<BigDecimal> numbers, BigDecimal price) {
            BigDecimal candidate = null;
            for (BigDecimal value : numbers) {
                if (value.scale() == 0) {
                    if (candidate == null || value.compareTo(candidate) > 0) {
                        candidate = value;
                    }
                }
            }
            if (candidate != null) {
                return candidate;
            }
            return largest(numbers, price);
        }

        private BigDecimal selectLikelyAmount(List<BigDecimal> numbers, BigDecimal price, BigDecimal quantity) {
            for (BigDecimal value : numbers) {
                if (price != null && value.compareTo(price) == 0) {
                    continue;
                }
                if (quantity != null && value.compareTo(quantity) == 0) {
                    continue;
                }
                if (value.scale() <= 2) {
                    return value;
                }
            }
            return middle(numbers, price, quantity);
        }

        private BigDecimal middle(List<BigDecimal> numbers, BigDecimal first, BigDecimal second) {
            for (BigDecimal value : numbers) {
                if (first != null && value.compareTo(first) == 0) {
                    continue;
                }
                if (second != null && value.compareTo(second) == 0) {
                    continue;
                }
                return value;
            }
            return null;
        }

        private BigDecimal selectLikelyFee(List<BigDecimal> numbers, BigDecimal price, BigDecimal quantity, BigDecimal amount) {
            BigDecimal candidate = null;
            for (BigDecimal value : numbers) {
                if (isSame(value, price) || isSame(value, quantity) || isSame(value, amount)) {
                    continue;
                }
                if (candidate == null || value.compareTo(candidate) < 0) {
                    candidate = value;
                }
            }
            return candidate;
        }

        private boolean isSame(BigDecimal value, BigDecimal other) {
            return value != null && other != null && value.compareTo(other) == 0;
        }

        private BigDecimal largest(List<BigDecimal> numbers, BigDecimal excluded) {
            BigDecimal max = null;
            for (BigDecimal value : numbers) {
                if (excluded != null && value.compareTo(excluded) == 0) {
                    continue;
                }
                if (max == null || value.compareTo(max) > 0) {
                    max = value;
                }
            }
            return max != null ? max : numbers.get(0);
        }

        private boolean isDateOrTimeLine(String line) {
            return TIME_PATTERN.matcher(line).find()
                    || DATE_PATTERN.matcher(line).find()
                    || TIME_ONLY_PATTERN.matcher(line).find();
        }

        private List<BigDecimal> parseNumbers(String line) {
            List<BigDecimal> numbers = new ArrayList<>();
            Matcher matcher = NUMBER_PATTERN.matcher(line);
            while (matcher.find()) {
                numbers.add(new BigDecimal(matcher.group(1)));
            }
            return numbers;
        }

        private String stripTime(String line) {
            Matcher matcher = TIME_PATTERN.matcher(line);
            if (matcher.find()) {
                return matcher.replaceAll("").trim();
            }
            return line;
        }

        private void applyHeuristicNumbers(List<BigDecimal> numbers) {
            if (numbers == null || numbers.size() < 3) {
                return;
            }

            BigDecimal likelyPrice = price != null ? price : selectLikelyPrice(numbers);
            BigDecimal likelyQuantity = quantity != null ? quantity : selectLikelyQuantity(numbers, likelyPrice);
            BigDecimal likelyAmount = amount != null ? amount : selectLikelyAmount(numbers, likelyPrice, likelyQuantity);

            if (price == null) {
                price = likelyPrice;
            }
            if (quantity == null) {
                quantity = likelyQuantity;
            }
            if (amount == null) {
                amount = likelyAmount;
            }

            if (fee == null && numbers.size() >= 4) {
                fee = selectLikelyFee(numbers, price, quantity, amount);
            }
        }

        private enum PendingField {
            QUANTITY,
            AMOUNT,
            PRICE,
            FEE
        }
    }
}

