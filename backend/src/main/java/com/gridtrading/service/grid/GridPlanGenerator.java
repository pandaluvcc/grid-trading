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

    /**
     * 网格计算模式
     */
    public enum CalculationMode {
        /** 价格锁定模式（原有逻辑，卖出价关联其他网格） */
        PRICE_LOCK,
        /** 独立计算模式（卖出价 = 买入价 × (1 + 比例)） */
        INDEPENDENT
    }

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

    /**
     * 生成网格计划（默认使用价格锁定模式，保持向后兼容）
     */
    public List<GridPlanItem> generate(BigDecimal basePrice, BigDecimal amountPerGrid) {
        return generate(basePrice, amountPerGrid, CalculationMode.PRICE_LOCK);
    }

    /**
     * 生成网格计划（支持选择计算模式）
     * 
     * @param basePrice 基准价格
     * @param amountPerGrid 每格金额
     * @param mode 计算模式
     * @return 网格计划列表
     */
    public List<GridPlanItem> generate(BigDecimal basePrice, BigDecimal amountPerGrid, CalculationMode mode) {
        if (mode == CalculationMode.INDEPENDENT) {
            return generateIndependent(basePrice, amountPerGrid);
        } else {
            return generatePriceLock(basePrice, amountPerGrid);
        }
    }

    /**
     * 价格锁定模式（原有逻辑）
     */
    private List<GridPlanItem> generatePriceLock(BigDecimal basePrice, BigDecimal amountPerGrid) {
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

    /**
     * 独立计算模式（等比递减：买入价按固定比例递减，卖出价 = 买入价 × (1 + 比例)）
     */
    private List<GridPlanItem> generateIndependent(BigDecimal basePrice, BigDecimal amountPerGrid) {
        List<GridPlanItem> items = new ArrayList<>();
        if (basePrice == null || amountPerGrid == null) {
            return items;
        }

        BigDecimal lastSmallBuyPrice = basePrice;
        BigDecimal lastMediumBuyPrice = null;
        BigDecimal secondMediumBuyPrice = null;
        int mediumCount = 0;

        for (int i = 0; i < TOTAL_GRID_COUNT; i++) {
            GridType gridType = GRID_TEMPLATE[i];
            int level = i + 1;

            BigDecimal buyPrice;
            BigDecimal sellPrice;

            // 买入价计算（等比递减：小网每次减去当前价格的5%）
            if (gridType == GridType.SMALL) {
                if (level == 1) {
                    buyPrice = basePrice;
                } else {
                    // 等比递减：buyPrice = lastBuyPrice × (1 - 5%)
                    buyPrice = lastSmallBuyPrice.multiply(BigDecimal.ONE.subtract(SMALL_PERCENT))
                            .setScale(8, RoundingMode.HALF_UP);
                }
                lastSmallBuyPrice = buyPrice;
            } else if (gridType == GridType.MEDIUM) {
                mediumCount++;
                buyPrice = lastSmallBuyPrice;  // 中网不递减，与上一个小网同价
                lastMediumBuyPrice = buyPrice;
                if (mediumCount == 2) {
                    secondMediumBuyPrice = buyPrice;
                }
            } else {
                buyPrice = lastSmallBuyPrice;  // 大网不递减
            }

            // 独立计算卖出价：根据网格类型固定收益率
            BigDecimal profitRate;
            switch (gridType) {
                case SMALL:
                    profitRate = SMALL_PERCENT;
                    break;
                case MEDIUM:
                    profitRate = MEDIUM_PERCENT;
                    break;
                case LARGE:
                    profitRate = LARGE_PERCENT;
                    break;
                default:
                    profitRate = SMALL_PERCENT;
            }

            // 卖出价 = 买入价 × (1 + 利润率)
            sellPrice = buyPrice.multiply(BigDecimal.ONE.add(profitRate)).setScale(8, RoundingMode.HALF_UP);

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

