package com.gridtrading.controller.dto;

import com.gridtrading.domain.GridLineState;
import com.gridtrading.domain.GridType;

import java.math.BigDecimal;
import java.util.List;

/**
 * 网格计划响应 DTO
 */
public class GridPlanResponse {

    private StrategyInfo strategy;
    private List<GridPlanItem> gridPlans;

    public static class StrategyInfo {
        private String name;
        private String symbol;
        private BigDecimal basePrice;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public BigDecimal getBasePrice() {
            return basePrice;
        }

        public void setBasePrice(BigDecimal basePrice) {
            this.basePrice = basePrice;
        }
    }

    public static class GridPlanItem {
        private Long id;
        private GridType gridType;
        private Integer level;
        private BigDecimal buyPrice;
        private BigDecimal sellPrice;
        private BigDecimal buyTriggerPrice;
        private BigDecimal sellTriggerPrice;
        private BigDecimal quantity;
        private BigDecimal buyAmount;
        private BigDecimal sellAmount;
        private BigDecimal profit;
        private BigDecimal profitRate;
        private GridLineState state;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public GridType getGridType() {
            return gridType;
        }

        public void setGridType(GridType gridType) {
            this.gridType = gridType;
        }

        public Integer getLevel() {
            return level;
        }

        public void setLevel(Integer level) {
            this.level = level;
        }

        public BigDecimal getBuyPrice() {
            return buyPrice;
        }

        public void setBuyPrice(BigDecimal buyPrice) {
            this.buyPrice = buyPrice;
        }

        public BigDecimal getSellPrice() {
            return sellPrice;
        }

        public void setSellPrice(BigDecimal sellPrice) {
            this.sellPrice = sellPrice;
        }

        public BigDecimal getBuyTriggerPrice() {
            return buyTriggerPrice;
        }

        public void setBuyTriggerPrice(BigDecimal buyTriggerPrice) {
            this.buyTriggerPrice = buyTriggerPrice;
        }

        public BigDecimal getSellTriggerPrice() {
            return sellTriggerPrice;
        }

        public void setSellTriggerPrice(BigDecimal sellTriggerPrice) {
            this.sellTriggerPrice = sellTriggerPrice;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getBuyAmount() {
            return buyAmount;
        }

        public void setBuyAmount(BigDecimal buyAmount) {
            this.buyAmount = buyAmount;
        }

        public BigDecimal getSellAmount() {
            return sellAmount;
        }

        public void setSellAmount(BigDecimal sellAmount) {
            this.sellAmount = sellAmount;
        }

        public BigDecimal getProfit() {
            return profit;
        }

        public void setProfit(BigDecimal profit) {
            this.profit = profit;
        }

        public BigDecimal getProfitRate() {
            return profitRate;
        }

        public void setProfitRate(BigDecimal profitRate) {
            this.profitRate = profitRate;
        }

        public GridLineState getState() {
            return state;
        }

        public void setState(GridLineState state) {
            this.state = state;
        }
    }

    public StrategyInfo getStrategy() {
        return strategy;
    }

    public void setStrategy(StrategyInfo strategy) {
        this.strategy = strategy;
    }

    public List<GridPlanItem> getGridPlans() {
        return gridPlans;
    }

    public void setGridPlans(List<GridPlanItem> gridPlans) {
        this.gridPlans = gridPlans;
    }
}
