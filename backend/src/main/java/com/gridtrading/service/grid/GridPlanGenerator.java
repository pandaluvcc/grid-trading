package com.gridtrading.service.grid;

import com.gridtrading.domain.GridType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 固定模板网格计划生成器（19条，v2.0）。
 */
public class GridPlanGenerator {

    public static final int TOTAL_GRID_COUNT = 19;
    public static final BigDecimal SMALL_PERCENT = new BigDecimal("0.05");
    public static final BigDecimal MEDIUM_PERCENT = new BigDecimal("0.15");
    public static final BigDecimal LARGE_PERCENT = new BigDecimal("0.30");

    private static final BigDecimal TRIGGER_OFFSET = new BigDecimal("0.02");

    public static final GridType[] GRID_TEMPLATE = {
            GridType.SMALL,
            GridType.SMALL,
            GridType.SMALL,
            GridType.SMALL,
            GridType.MEDIUM,
            GridType.SMALL,
            GridType.SMALL,
            GridType.SMALL,
            GridType.MEDIUM,
            GridType.LARGE,
            GridType.SMALL,
            GridType.SMALL,
            GridType.SMALL,
            GridType.MEDIUM,
            GridType.SMALL,
            GridType.SMALL,
            GridType.SMALL,
            GridType.MEDIUM,
            GridType.LARGE
    };

    public List<GridPlanItem> generate(BigDecimal basePrice, BigDecimal amountPerGrid) {
        List<GridPlanItem> items = new ArrayList<>();
        if (basePrice == null || amountPerGrid == null) {
            return items;
        }

        BigDecimal smallStep = basePrice.multiply(SMALL_PERCENT);
        BigDecimal lastSmallBuyPrice = basePrice;
        BigDecimal lastMediumBuyPrice = null;
        BigDecimal secondMediumBuyPrice = null;
        int mediumCount = 0;

        for (int i = 0; i < TOTAL_GRID_COUNT; i++) {
            GridType gridType = GRID_TEMPLATE[i];
            int level = i + 1;

            BigDecimal buyPrice;
            BigDecimal sellPrice;

            if (gridType == GridType.SMALL) {
                if (level == 1) {
                    buyPrice = basePrice;
                    sellPrice = basePrice.add(smallStep).setScale(8, RoundingMode.HALF_UP);
                } else {
                    buyPrice = lastSmallBuyPrice.subtract(smallStep).setScale(8, RoundingMode.HALF_UP);
                    sellPrice = lastSmallBuyPrice;
                }
                lastSmallBuyPrice = buyPrice;
            } else if (gridType == GridType.MEDIUM) {
                mediumCount++;
                buyPrice = lastSmallBuyPrice;
                if (level == 5) {
                    sellPrice = basePrice;
                } else {
                    sellPrice = lastMediumBuyPrice;
                }
                lastMediumBuyPrice = buyPrice;
                if (mediumCount == 2) {
                    secondMediumBuyPrice = buyPrice;
                }
            } else {
                buyPrice = lastSmallBuyPrice;
                if (level == 10) {
                    sellPrice = basePrice;
                } else {
                    sellPrice = secondMediumBuyPrice;
                }
            }

            items.add(createItem(gridType, level, buyPrice, sellPrice, amountPerGrid));
        }

        return items;
    }

    private GridPlanItem createItem(GridType gridType,
                                    int level,
                                    BigDecimal buyPrice,
                                    BigDecimal sellPrice,
                                    BigDecimal buyAmount) {
        GridPlanItem item = new GridPlanItem();
        item.gridType = gridType;
        item.level = level;
        item.buyPrice = buyPrice;
        item.sellPrice = sellPrice;
        item.buyTriggerPrice = buyPrice.add(TRIGGER_OFFSET);
        item.sellTriggerPrice = sellPrice.subtract(TRIGGER_OFFSET);
        item.buyAmount = buyAmount;

        BigDecimal buyQuantity = buyAmount.divide(buyPrice, 8, RoundingMode.DOWN);
        item.buyQuantity = buyQuantity;

        BigDecimal sellAmount = buyQuantity.multiply(sellPrice).setScale(2, RoundingMode.DOWN);
        item.sellAmount = sellAmount;

        BigDecimal profit = sellAmount.subtract(buyAmount);
        item.profit = profit;

        item.profitRate = profit.divide(buyAmount, 6, RoundingMode.HALF_UP);
        return item;
    }

    public static class GridPlanItem {
        private GridType gridType;
        private int level;
        private BigDecimal buyPrice;
        private BigDecimal sellPrice;
        private BigDecimal buyTriggerPrice;
        private BigDecimal sellTriggerPrice;
        private BigDecimal buyAmount;
        private BigDecimal buyQuantity;
        private BigDecimal sellAmount;
        private BigDecimal profit;
        private BigDecimal profitRate;

        public GridType getGridType() {
            return gridType;
        }

        public int getLevel() {
            return level;
        }

        public BigDecimal getBuyPrice() {
            return buyPrice;
        }

        public BigDecimal getSellPrice() {
            return sellPrice;
        }

        public BigDecimal getBuyTriggerPrice() {
            return buyTriggerPrice;
        }

        public BigDecimal getSellTriggerPrice() {
            return sellTriggerPrice;
        }

        public BigDecimal getBuyAmount() {
            return buyAmount;
        }

        public BigDecimal getBuyQuantity() {
            return buyQuantity;
        }

        public BigDecimal getSellAmount() {
            return sellAmount;
        }

        public BigDecimal getProfit() {
            return profit;
        }

        public BigDecimal getProfitRate() {
            return profitRate;
        }
    }
}

