package com.gridtrading.service.ocr;

import com.gridtrading.domain.GridLine;
import com.gridtrading.domain.GridLineState;
import com.gridtrading.domain.Strategy;
import com.gridtrading.repository.GridLineRepository;
import com.gridtrading.repository.StrategyRepository;
import com.gridtrading.repository.TradeRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OcrStrategyImportTest {

    @Test
    void createsStrategyAndAppliesFirstRecords() throws Exception {
        String rawText = "中概互联 513050\n中概互联网ETF易方达\n"
                + "买入 2024-01-01 10:00 价格 1.710 数量 1000 金额 1710\n"
                + "买入 2024-01-01 10:05 价格 1.625 数量 1000 金额 1625\n"
                + "卖出 2024-01-01 10:10 价格 1.544 数量 1000 金额 1544\n"
                + "买入 2024-01-01 10:15 价格 1.467 数量 1000 金额 1467";

        BaiduOcrClient baiduOcrClient = Mockito.mock(BaiduOcrClient.class);
        Mockito.when(baiduOcrClient.recognize(Mockito.any())).thenReturn(rawText);

        StrategyRepository strategyRepository = Mockito.mock(StrategyRepository.class);
        GridLineRepository gridLineRepository = Mockito.mock(GridLineRepository.class);
        TradeRecordRepository tradeRecordRepository = Mockito.mock(TradeRecordRepository.class);
        Mockito.when(strategyRepository.save(Mockito.any())).thenAnswer(invocation -> {
            Strategy strategy = invocation.getArgument(0);
            if (strategy.getId() == null) {
                strategy.setId(1L);
            }
            return strategy;
        });

        OcrService service = new OcrService(
                baiduOcrClient,
                new EastMoneyParser(),
                strategyRepository,
                gridLineRepository,
                tradeRecordRepository,
                new BigDecimal("0.005"),
                30
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "trade.png",
                "image/png",
                "fake".getBytes()
        );

        Strategy strategy = service.createStrategyFromOcr(List.of(file), "EASTMONEY", null, null);

        assertNotNull(strategy.getId());
        assertEquals(19, strategy.getGridLines().size());
        assertEquals("中概互联网ETF易方达", strategy.getName());
        assertEquals("513050", strategy.getSymbol());

        GridLine line1 = strategy.getGridLines().stream()
                .filter(line -> line.getLevel() == 1)
                .findFirst()
                .orElseThrow();
        GridLine line2 = strategy.getGridLines().stream()
                .filter(line -> line.getLevel() == 2)
                .findFirst()
                .orElseThrow();
        GridLine line3 = strategy.getGridLines().stream()
                .filter(line -> line.getLevel() == 3)
                .findFirst()
                .orElseThrow();
        GridLine line4 = strategy.getGridLines().stream()
                .filter(line -> line.getLevel() == 4)
                .findFirst()
                .orElseThrow();
        GridLine line5 = strategy.getGridLines().stream()
                .filter(line -> line.getLevel() == 5)
                .findFirst()
                .orElseThrow();

        assertEquals(0, line1.getBuyPrice().compareTo(new BigDecimal("1.710")));
        assertEquals(0, line2.getBuyPrice().compareTo(new BigDecimal("1.625")));
        assertEquals(0, line3.getBuyPrice().compareTo(new BigDecimal("1.53900000")));
        assertEquals(0, line4.getBuyPrice().compareTo(new BigDecimal("1.467")));
        assertEquals(GridLineState.BOUGHT, line4.getState());
        assertEquals(GridLineState.WAIT_BUY, line5.getState());
    }
}
