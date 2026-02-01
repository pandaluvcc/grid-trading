package com.gridtrading.controller.dto;

import com.gridtrading.domain.StrategyStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Tick 执行响应 DTO
 */
public class TickResponse {

    private StrategyStatus status;
    private BigDecimal currentPrice;
    private BigDecimal position;
    private BigDecimal availableCash;
    private BigDecimal investedAmount;
    private BigDecimal realizedProfit;
    private List<TradeRecordDto> trades = new ArrayList<>();

    public StrategyStatus getStatus() {
        return status;
    }

    public void setStatus(StrategyStatus status) {
        this.status = status;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getPosition() {
        return position;
    }

    public void setPosition(BigDecimal position) {
        this.position = position;
    }

    public BigDecimal getAvailableCash() {
        return availableCash;
    }

    public void setAvailableCash(BigDecimal availableCash) {
        this.availableCash = availableCash;
    }

    public BigDecimal getInvestedAmount() {
        return investedAmount;
    }

    public void setInvestedAmount(BigDecimal investedAmount) {
        this.investedAmount = investedAmount;
    }

    public BigDecimal getRealizedProfit() {
        return realizedProfit;
    }

    public void setRealizedProfit(BigDecimal realizedProfit) {
        this.realizedProfit = realizedProfit;
    }

    public List<TradeRecordDto> getTrades() {
        return trades;
    }

    public void setTrades(List<TradeRecordDto> trades) {
        this.trades = trades;
    }
}
