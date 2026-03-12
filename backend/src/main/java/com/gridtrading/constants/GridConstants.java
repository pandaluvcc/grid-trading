package com.gridtrading.constants;

import com.gridtrading.domain.GridType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 网格交易常量定义
 * <p>
 * 统一管理所有网格相关的常量，避免魔法数字散落在代码各处
 */
public final class GridConstants {

    private GridConstants() {
        // 工具类，禁止实例化
    }

    // ==================== 网格基础配置 ====================

    /**
     * 总网格数量（固定 19 条）
     */
    public static final int TOTAL_GRID_COUNT = 19;

    /**
     * 触发价格偏移量（买入触发价 = 买入价 + 偏移量，卖出触发价 = 卖出价 - 偏移量）
     */
    public static final BigDecimal TRIGGER_OFFSET = new BigDecimal("0.002");

    // ==================== 网格收益率 ====================

    /**
     * 小网收益率：5%
     */
    public static final BigDecimal SMALL_PROFIT_RATE = new BigDecimal("0.05");

    /**
     * 中网收益率：15%
     */
    public static final BigDecimal MEDIUM_PROFIT_RATE = new BigDecimal("0.15");

    /**
     * 大网收益率：30%
     */
    public static final BigDecimal LARGE_PROFIT_RATE = new BigDecimal("0.30");

    // ==================== 网格价格递减因子 ====================

    /**
     * 小网买入价递减因子：每下一网 = 上一网 × 0.95
     */
    public static final BigDecimal DECREASE_FACTOR = new BigDecimal("0.95");

    // ==================== 网格类型模板（固定 19 条） ====================

    /**
     * 网格类型模板
     * 索引 0-18 对应 Level 1-19
     */
    public static final GridType[] GRID_TEMPLATE = {
            GridType.SMALL,   // Level 1
            GridType.SMALL,   // Level 2
            GridType.SMALL,   // Level 3
            GridType.SMALL,   // Level 4
            GridType.MEDIUM,  // Level 5
            GridType.SMALL,   // Level 6
            GridType.SMALL,   // Level 7
            GridType.SMALL,   // Level 8
            GridType.MEDIUM,  // Level 9
            GridType.LARGE,   // Level 10
            GridType.SMALL,   // Level 11
            GridType.SMALL,   // Level 12
            GridType.SMALL,   // Level 13
            GridType.MEDIUM,  // Level 14
            GridType.SMALL,   // Level 15
            GridType.SMALL,   // Level 16
            GridType.SMALL,   // Level 17
            GridType.MEDIUM,  // Level 18
            GridType.LARGE    // Level 19
    };

    /**
     * 根据层级获取网格类型
     *
     * @param level 网格层级（1-19）
     * @return 网格类型
     */
    public static GridType getGridType(int level) {
        if (level < 1 || level > TOTAL_GRID_COUNT) {
            throw new IllegalArgumentException("网格层级必须在 1-" + TOTAL_GRID_COUNT + " 之间，当前: " + level);
        }
        return GRID_TEMPLATE[level - 1];
    }

    /**
     * 根据网格类型获取收益率
     *
     * @param gridType 网格类型
     * @return 收益率
     */
    public static BigDecimal getProfitRate(GridType gridType) {
        switch (gridType) {
            case SMALL:
                return SMALL_PROFIT_RATE;
            case MEDIUM:
                return MEDIUM_PROFIT_RATE;
            case LARGE:
                return LARGE_PROFIT_RATE;
            default:
                return SMALL_PROFIT_RATE;
        }
    }

    /**
     * 计算买入触发价
     *
     * @param buyPrice 买入价
     * @return 买入触发价
     */
    public static BigDecimal calculateBuyTriggerPrice(BigDecimal buyPrice) {
        return buyPrice.add(TRIGGER_OFFSET).setScale(3, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 计算卖出触发价
     *
     * @param sellPrice 卖出价
     * @return 卖出触发价
     */
    public static BigDecimal calculateSellTriggerPrice(BigDecimal sellPrice) {
        return sellPrice.subtract(TRIGGER_OFFSET).setScale(3, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 计算最低卖出价（成本 + 目标收益率）
     *
     * @param buyPrice 买入价
     * @param gridType 网格类型
     * @return 最低卖出价
     */
    public static BigDecimal calculateMinSellPrice(BigDecimal buyPrice, GridType gridType) {
        BigDecimal profitRate = getProfitRate(gridType);
        return buyPrice.multiply(BigDecimal.ONE.add(profitRate)).setScale(3, java.math.RoundingMode.HALF_UP);
    }
}
