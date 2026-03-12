package com.gridtrading.service;

import com.gridtrading.domain.Strategy;
import com.gridtrading.domain.TradeRecord;
import com.gridtrading.domain.TradeType;
import com.gridtrading.repository.TradeRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 持仓计算服务
 * <p>
 * 负责计算和更新策略的持仓相关字段
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PositionCalculator {

    private final TradeRecordRepository tradeRecordRepository;

    /**
     * 计算并更新策略的持仓相关字段
     * 在每次成交记录变更后调用
     */
    @Transactional
    public void calculateAndUpdate(Strategy strategy) {
        List<TradeRecord> records = tradeRecordRepository.findByStrategyIdOrderByTradeTimeAsc(strategy.getId());

        if (records == null || records.isEmpty()) {
            log.debug("[PositionCalculator] 无成交记录，重置为0");
            resetToZero(strategy);
            return;
        }

        log.debug("[PositionCalculator] 开始计算，策略ID={}，成交记录数={}", strategy.getId(), records.size());

        BigDecimal totalBuyAmount = BigDecimal.ZERO;
        BigDecimal totalBuyQuantity = BigDecimal.ZERO;
        BigDecimal totalSellAmount = BigDecimal.ZERO;
        BigDecimal totalSellQuantity = BigDecimal.ZERO;
        BigDecimal totalFee = BigDecimal.ZERO;
        LocalDateTime firstBuyTime = null;

        // 移动加权平均法计算持仓成本
        BigDecimal currentHoldingQuantity = BigDecimal.ZERO;
        BigDecimal currentHoldingCost = BigDecimal.ZERO;
        BigDecimal avgBuyPrice = BigDecimal.ZERO;

        for (TradeRecord record : records) {
            BigDecimal amount = record.getAmount() != null ? record.getAmount() : BigDecimal.ZERO;
            BigDecimal fee = record.getFee() != null ? record.getFee() : BigDecimal.ZERO;
            BigDecimal quantity = record.getQuantity() != null ? record.getQuantity() : BigDecimal.ZERO;

            totalFee = totalFee.add(fee);

            if (record.getType() == TradeType.BUY || record.getType() == TradeType.OPENING_BUY) {
                totalBuyAmount = totalBuyAmount.add(amount);
                totalBuyQuantity = totalBuyQuantity.add(quantity);
                if (firstBuyTime == null || record.getTradeTime().isBefore(firstBuyTime)) {
                    firstBuyTime = record.getTradeTime();
                }

                // 移动加权平均：买入时更新持仓成本
                BigDecimal buyCost = amount.add(fee);
                currentHoldingCost = currentHoldingCost.add(buyCost);
                currentHoldingQuantity = currentHoldingQuantity.add(quantity);

                if (currentHoldingQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    avgBuyPrice = currentHoldingCost.divide(currentHoldingQuantity, 8, RoundingMode.HALF_UP);
                }

                log.trace("买入记录: price={}, quantity={}, amount={}, fee={}",
                        record.getPrice(), quantity, amount, fee);

            } else if (record.getType() == TradeType.SELL) {
                totalSellAmount = totalSellAmount.add(amount);
                totalSellQuantity = totalSellQuantity.add(quantity);

                // 移动加权平均：卖出时按当前均价减少持仓成本
                if (currentHoldingQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal sellCost = avgBuyPrice.multiply(quantity);
                    currentHoldingCost = currentHoldingCost.subtract(sellCost);
                    currentHoldingQuantity = currentHoldingQuantity.subtract(quantity);
                }

                log.trace("卖出记录: price={}, quantity={}, amount={}, fee={}",
                        record.getPrice(), quantity, amount, fee);
            }
        }

        BigDecimal currentPosition = totalBuyQuantity.subtract(totalSellQuantity);
        BigDecimal netInvestment = totalBuyAmount.subtract(totalSellAmount).add(totalFee);

        // 成本价
        BigDecimal costPrice = BigDecimal.ZERO;
        if (currentPosition.compareTo(BigDecimal.ZERO) > 0) {
            costPrice = netInvestment.divide(currentPosition, 3, RoundingMode.HALF_UP);
        }

        // 买入均价
        if (avgBuyPrice.compareTo(BigDecimal.ZERO) > 0) {
            avgBuyPrice = avgBuyPrice.setScale(3, RoundingMode.HALF_UP);
        }

        // 持股天数
        int holdingDays = 0;
        if (firstBuyTime != null) {
            holdingDays = (int) ChronoUnit.DAYS.between(firstBuyTime.toLocalDate(), LocalDateTime.now().toLocalDate());
        }

        BigDecimal lastPrice = strategy.getLastPrice() != null ? strategy.getLastPrice() : strategy.getBasePrice();

        // 持仓盈亏计算
        BigDecimal positionProfit = BigDecimal.ZERO;
        BigDecimal positionProfitPercent = BigDecimal.ZERO;
        BigDecimal positionRatio = BigDecimal.ZERO;

        if (lastPrice != null && currentPosition.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal marketValue = lastPrice.multiply(currentPosition);
            positionProfit = marketValue.subtract(netInvestment).setScale(2, RoundingMode.HALF_UP);

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

        log.debug("[PositionCalculator] 计算结果: 买入总金额={}, 持仓数量={}, 成本价={}, 持仓盈亏={}",
                totalBuyAmount, currentPosition, costPrice, positionProfit);

        // 更新策略字段
        strategy.setTotalBuyAmount(totalBuyAmount.setScale(3, RoundingMode.HALF_UP));
        strategy.setTotalBuyQuantity(totalBuyQuantity.setScale(3, RoundingMode.HALF_UP));
        strategy.setTotalSellAmount(totalSellAmount.setScale(3, RoundingMode.HALF_UP));
        strategy.setTotalSellQuantity(totalSellQuantity.setScale(3, RoundingMode.HALF_UP));
        strategy.setTotalFee(totalFee.setScale(3, RoundingMode.HALF_UP));
        strategy.setCostPrice(costPrice);
        strategy.setAvgBuyPrice(avgBuyPrice);
        strategy.setHoldingDays(holdingDays);
        strategy.setFirstBuyTime(firstBuyTime);
        strategy.setPosition(currentPosition.setScale(3, RoundingMode.HALF_UP));
        strategy.setPositionProfit(positionProfit);
        strategy.setPositionProfitPercent(positionProfitPercent);
        strategy.setPositionRatio(positionRatio);
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
            log.debug("[PositionCalculator] updateByLastPrice: 无持仓，跳过计算");
            return;
        }

        BigDecimal marketValue = newLastPrice.multiply(currentPosition);
        BigDecimal positionProfit = marketValue.subtract(netInvestment).setScale(2, RoundingMode.HALF_UP);
        strategy.setPositionProfit(positionProfit);

        BigDecimal costPrice = strategy.getCostPrice() != null ? strategy.getCostPrice() : BigDecimal.ZERO;
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

        log.debug("[PositionCalculator] updateByLastPrice: newLastPrice={}, positionProfit={}, positionProfitPercent={}",
                newLastPrice, strategy.getPositionProfit(), strategy.getPositionProfitPercent());
    }
}