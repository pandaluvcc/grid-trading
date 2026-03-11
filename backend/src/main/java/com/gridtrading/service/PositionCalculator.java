package com.gridtrading.service;

import com.gridtrading.domain.Strategy;
import com.gridtrading.domain.TradeRecord;
import com.gridtrading.domain.TradeType;
import com.gridtrading.repository.TradeRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class PositionCalculator {

    private final TradeRecordRepository tradeRecordRepository;

    public PositionCalculator(TradeRecordRepository tradeRecordRepository) {
        this.tradeRecordRepository = tradeRecordRepository;
    }

    /**
     * 计算并更新策略的持仓相关字段
     * 在每次成交记录变更后调用
     */
    @Transactional
    public void calculateAndUpdate(Strategy strategy) {
        List<TradeRecord> records = tradeRecordRepository.findByStrategyIdOrderByTradeTimeAsc(strategy.getId());

        if (records == null || records.isEmpty()) {
            System.out.println("[PositionCalculator] 无成交记录，重置为0");
            resetToZero(strategy);
            return;
        }

        System.out.println("[PositionCalculator] 开始计算，策略ID=" + strategy.getId() + "，成交记录数=" + records.size());

        BigDecimal totalBuyAmount = BigDecimal.ZERO;
        BigDecimal totalBuyQuantity = BigDecimal.ZERO;
        BigDecimal totalSellAmount = BigDecimal.ZERO;
        BigDecimal totalSellQuantity = BigDecimal.ZERO;
        BigDecimal totalFee = BigDecimal.ZERO;
        LocalDateTime firstBuyTime = null;

        // 移动加权平均法计算持仓成本（买入均价）
        BigDecimal currentHoldingQuantity = BigDecimal.ZERO;
        BigDecimal currentHoldingCost = BigDecimal.ZERO; // 持仓总成本（不含卖出部分）
        BigDecimal avgBuyPrice = BigDecimal.ZERO;

        for (TradeRecord record : records) {
            BigDecimal amount = record.getAmount() != null ? record.getAmount() : BigDecimal.ZERO;
            BigDecimal fee = record.getFee() != null ? record.getFee() : BigDecimal.ZERO;
            BigDecimal price = record.getPrice() != null ? record.getPrice() : BigDecimal.ZERO;
            BigDecimal quantity = record.getQuantity() != null ? record.getQuantity() : BigDecimal.ZERO;

            totalFee = totalFee.add(fee);

            if (record.getType() == TradeType.BUY || record.getType() == TradeType.OPENING_BUY) {
                totalBuyAmount = totalBuyAmount.add(amount);
                totalBuyQuantity = totalBuyQuantity.add(quantity);
                if (firstBuyTime == null || record.getTradeTime().isBefore(firstBuyTime)) {
                    firstBuyTime = record.getTradeTime();
                }

                // 移动加权平均：买入时更新持仓成本
                // 新持仓成本 = 原持仓成本 + 本次买入金额 + 本次买入费用
                BigDecimal buyCost = amount.add(fee);
                currentHoldingCost = currentHoldingCost.add(buyCost);
                currentHoldingQuantity = currentHoldingQuantity.add(quantity);

                // 更新买入均价（仅在有持仓时计算）
                if (currentHoldingQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    avgBuyPrice = currentHoldingCost.divide(currentHoldingQuantity, 8, RoundingMode.HALF_UP);
                }

                System.out.println("[PositionCalculator] 买入记录: price=" + formatPrice(price) + ", quantity=" + formatQuantity(quantity) + ", amount=" + formatAmount(amount) + ", fee=" + formatAmount(fee));
            } else if (record.getType() == TradeType.SELL) {
                totalSellAmount = totalSellAmount.add(amount);
                totalSellQuantity = totalSellQuantity.add(quantity);

                // 移动加权平均：卖出时按当前均价减少持仓成本
                // 减少的成本 = 卖出数量 × 当前买入均价
                if (currentHoldingQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal sellCost = avgBuyPrice.multiply(quantity);
                    currentHoldingCost = currentHoldingCost.subtract(sellCost);
                    currentHoldingQuantity = currentHoldingQuantity.subtract(quantity);
                }

                System.out.println("[PositionCalculator] 卖出记录: price=" + formatPrice(price) + ", quantity=" + formatQuantity(quantity) + ", amount=" + formatAmount(amount) + ", fee=" + formatAmount(fee));
            }
        }

        BigDecimal currentPosition = totalBuyQuantity.subtract(totalSellQuantity);
        BigDecimal netInvestment = totalBuyAmount.subtract(totalSellAmount).add(totalFee);

        // 成本价 = (买入总金额 - 卖出总金额 + 总费用) / 当前持仓数量，保留3位小数
        BigDecimal costPrice = BigDecimal.ZERO;
        if (currentPosition.compareTo(BigDecimal.ZERO) > 0) {
            costPrice = netInvestment.divide(currentPosition, 3, RoundingMode.HALF_UP);
        }

        // 买入均价（持仓成本价）使用移动加权平均法计算结果，保留3位小数
        if (avgBuyPrice.compareTo(BigDecimal.ZERO) > 0) {
            avgBuyPrice = avgBuyPrice.setScale(3, RoundingMode.HALF_UP);
        }

        int holdingDays = 0;
        if (firstBuyTime != null) {
            holdingDays = (int) ChronoUnit.DAYS.between(firstBuyTime.toLocalDate(), LocalDateTime.now().toLocalDate());
        }

        BigDecimal lastPrice = strategy.getLastPrice() != null ? strategy.getLastPrice() : strategy.getBasePrice();

        // 持仓盈亏 = (现价 × 持仓数量) - 净投资，保留2位小数（避免精度损失）
        BigDecimal positionProfit = BigDecimal.ZERO;
        // 持仓盈亏百分比，保留3位小数
        BigDecimal positionProfitPercent = BigDecimal.ZERO;
        // 个股仓位比例，保留2位小数
        BigDecimal positionRatio = BigDecimal.ZERO;

        if (lastPrice != null && currentPosition.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal marketValue = lastPrice.multiply(currentPosition);
            positionProfit = marketValue.subtract(netInvestment).setScale(2, RoundingMode.HALF_UP);

            // 盈亏比例 = (现价 - 成本价)/成本价 × 100%，和券商显示逻辑完全一致
            if (costPrice.compareTo(BigDecimal.ZERO) > 0) {
                positionProfitPercent = lastPrice.subtract(costPrice)
                        .divide(costPrice, 8, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(3, RoundingMode.HALF_UP);
            }

            if (strategy.getMaxCapital() != null && strategy.getMaxCapital().compareTo(BigDecimal.ZERO) > 0) {
                positionRatio = marketValue.divide(strategy.getMaxCapital(), 6, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
            }
        }

        System.out.println("[PositionCalculator] 计算结果:");
        System.out.println("  - 买入总金额: " + formatAmount(totalBuyAmount));
        System.out.println("  - 买入总数量: " + formatQuantity(totalBuyQuantity));
        System.out.println("  - 卖出总金额: " + formatAmount(totalSellAmount));
        System.out.println("  - 卖出总数量: " + formatQuantity(totalSellQuantity));
        System.out.println("  - 总费用: " + formatAmount(totalFee));
        System.out.println("  - 当前持仓数量: " + formatQuantity(currentPosition));
        System.out.println("  - 成本价: " + formatPrice(costPrice));
        System.out.println("  - 买入均价: " + formatPrice(avgBuyPrice));
        System.out.println("  - 持股天数: " + holdingDays);
        System.out.println("  - 现价: " + formatPrice(lastPrice));
        System.out.println("  - 持仓盈亏: " + formatAmount(positionProfit));
        System.out.println("  - 持仓盈亏%: " + formatPercent(positionProfitPercent));
        System.out.println("  - 个股仓位: " + formatPercent(positionRatio));

        strategy.setTotalBuyAmount(totalBuyAmount.setScale(3, RoundingMode.HALF_UP));
        strategy.setTotalBuyQuantity(totalBuyQuantity.setScale(3, RoundingMode.HALF_UP));
        strategy.setTotalSellAmount(totalSellAmount.setScale(3, RoundingMode.HALF_UP));
        strategy.setTotalSellQuantity(totalSellQuantity.setScale(3, RoundingMode.HALF_UP));
        strategy.setTotalFee(totalFee.setScale(3, RoundingMode.HALF_UP));
        strategy.setCostPrice(costPrice.setScale(3, RoundingMode.HALF_UP));
        strategy.setAvgBuyPrice(avgBuyPrice.setScale(3, RoundingMode.HALF_UP));
        strategy.setHoldingDays(holdingDays);
        strategy.setFirstBuyTime(firstBuyTime);
        strategy.setPosition(currentPosition.setScale(3, RoundingMode.HALF_UP));
        strategy.setPositionProfit(positionProfit.setScale(3, RoundingMode.HALF_UP));
        strategy.setPositionProfitPercent(positionProfitPercent.setScale(3, RoundingMode.HALF_UP));
        strategy.setPositionRatio(positionRatio.setScale(3, RoundingMode.HALF_UP));
    }

    /**
     * 重置为0（无持仓时）
     */
    private void resetToZero(Strategy strategy) {
        strategy.setTotalBuyAmount(BigDecimal.ZERO);
        strategy.setTotalBuyQuantity(BigDecimal.ZERO);
        strategy.setTotalSellAmount(BigDecimal.ZERO);
        strategy.setTotalSellQuantity(BigDecimal.ZERO);
        strategy.setTotalFee(BigDecimal.ZERO);
        strategy.setCostPrice(BigDecimal.ZERO);
        strategy.setAvgBuyPrice(BigDecimal.ZERO);
        strategy.setHoldingDays(0);
        strategy.setFirstBuyTime(null);
        strategy.setPosition(BigDecimal.ZERO);
        strategy.setPositionProfit(BigDecimal.ZERO);
        strategy.setPositionProfitPercent(BigDecimal.ZERO);
        strategy.setPositionRatio(BigDecimal.ZERO);
    }

    private String formatAmount(BigDecimal value) {
        if (value == null) return "0.00";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatQuantity(BigDecimal value) {
        if (value == null) return "0";
        return value.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatPrice(BigDecimal value) {
        if (value == null) return "0.000";
        return value.setScale(3, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) return "0.00";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 当现价变更时，重新计算持仓盈亏相关字段
     */
    @Transactional
    public void updateByLastPrice(Strategy strategy, BigDecimal newLastPrice) {
        strategy.setLastPrice(newLastPrice);

        BigDecimal currentPosition = strategy.getPosition();
        BigDecimal totalBuyAmount = strategy.getTotalBuyAmount() != null ? strategy.getTotalBuyAmount() : BigDecimal.ZERO;
        BigDecimal totalSellAmount = strategy.getTotalSellAmount() != null ? strategy.getTotalSellAmount() : BigDecimal.ZERO;
        BigDecimal totalFee = strategy.getTotalFee() != null ? strategy.getTotalFee() : BigDecimal.ZERO;
        BigDecimal netInvestment = totalBuyAmount.subtract(totalSellAmount).add(totalFee);

        if (currentPosition == null || currentPosition.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("[PositionCalculator] updateByLastPrice: 无持仓，跳过计算");
            return;
        }

        BigDecimal marketValue = newLastPrice.multiply(currentPosition);
        BigDecimal positionProfit = marketValue.subtract(netInvestment).setScale(2, RoundingMode.HALF_UP);
        strategy.setPositionProfit(positionProfit);

        BigDecimal costPrice = strategy.getCostPrice() != null ? strategy.getCostPrice() : BigDecimal.ZERO;
        // 盈亏比例 = (现价 - 成本价)/成本价 × 100%，和券商显示逻辑完全一致
        if (costPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal positionProfitPercent = newLastPrice.subtract(costPrice)
                    .divide(costPrice, 8, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(3, RoundingMode.HALF_UP);
            strategy.setPositionProfitPercent(positionProfitPercent);
        }

        if (strategy.getMaxCapital() != null && strategy.getMaxCapital().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal positionRatio = marketValue.divide(strategy.getMaxCapital(), 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
            strategy.setPositionRatio(positionRatio);
        }

        System.out.println("[PositionCalculator] updateByLastPrice: newLastPrice=" + newLastPrice + 
            ", positionProfit=" + strategy.getPositionProfit() + 
            ", positionProfitPercent=" + strategy.getPositionProfitPercent() +
            ", positionRatio=" + strategy.getPositionRatio());
    }
}
