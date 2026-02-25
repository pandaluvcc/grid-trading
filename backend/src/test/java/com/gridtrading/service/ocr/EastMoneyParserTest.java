package com.gridtrading.service.ocr;

import com.gridtrading.controller.dto.OcrTradeRecord;
import com.gridtrading.domain.TradeType;
import com.gridtrading.service.ocr.EastMoneyParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EastMoneyParserTest {

    private final EastMoneyParser parser = new EastMoneyParser();

    @Test
    void parse_shouldExtractBasicFields() {
        String raw = "买入 2026-02-12 10:32:13 数量 1000 金额 1394.00 价格 1.394 费用 0.35";
        List<OcrTradeRecord> records = parser.parse(raw);

        assertEquals(1, records.size());
        OcrTradeRecord record = records.get(0);
        assertEquals(TradeType.BUY, record.getType());
        assertEquals(LocalDateTime.of(2026, 2, 12, 10, 32, 13), record.getTradeTime());
        assertEquals(new BigDecimal("1000"), record.getQuantity());
        assertEquals(new BigDecimal("1394.00"), record.getAmount());
        assertEquals(new BigDecimal("1.394"), record.getPrice());
        assertEquals(new BigDecimal("0.35"), record.getFee());
    }

    @Test
    void parse_shouldHandleMissingSeconds() {
        String raw = "卖出 2026-02-12 10:32 数量 100 金额 100.00 价格 1.0";
        List<OcrTradeRecord> records = parser.parse(raw);

        assertEquals(1, records.size());
        OcrTradeRecord record = records.get(0);
        assertEquals(TradeType.SELL, record.getType());
        assertNotNull(record.getTradeTime());
        assertEquals(LocalDateTime.of(2026, 2, 12, 10, 32, 0), record.getTradeTime());
    }

    @Test
    void parse_shouldHandleUnlabeledLine() {
        String raw = "建仓-买入 2026-02-12 10:32:13 1.394 1000 1394.00 0.35";
        List<OcrTradeRecord> records = parser.parse(raw);

        assertEquals(1, records.size());
        OcrTradeRecord record = records.get(0);
        assertEquals(TradeType.BUY, record.getType());
        assertEquals(new BigDecimal("1.394"), record.getPrice());
        assertEquals(new BigDecimal("1000"), record.getQuantity());
        assertEquals(new BigDecimal("1394.00"), record.getAmount());
        assertEquals(new BigDecimal("0.35"), record.getFee());
    }

    @Test
    void parse_shouldHandleLineSplitDateAndLabels() {
        String raw = String.join("\n",
                "持仓明细",
                "交易记录",
                "买入",
                "2026-02-12",
                "10:32:13",
                "数量",
                "1000",
                "金额",
                "1394.00",
                "价格",
                "1.394",
                "费用",
                "0.35",
                "卖出",
                "2026-01-06",
                "09:54:03",
                "数量",
                "1000",
                "金额",
                "1544.00",
                "价格",
                "1.544",
                "费用",
                "0.39",
                "行情"
        );

        List<OcrTradeRecord> records = parser.parse(raw);
        assertEquals(2, records.size());
        assertEquals(TradeType.BUY, records.get(0).getType());
        assertEquals(new BigDecimal("1000"), records.get(0).getQuantity());
        assertEquals(new BigDecimal("1394.00"), records.get(0).getAmount());
        assertEquals(new BigDecimal("1.394"), records.get(0).getPrice());
        assertEquals(TradeType.SELL, records.get(1).getType());
        assertEquals(new BigDecimal("1544.00"), records.get(1).getAmount());
        assertEquals(new BigDecimal("1.544"), records.get(1).getPrice());
    }
}
