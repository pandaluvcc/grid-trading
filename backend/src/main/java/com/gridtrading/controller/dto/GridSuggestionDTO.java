package com.gridtrading.controller.dto;

import com.gridtrading.domain.GridLineState;
import com.gridtrading.domain.GridType;
import com.gridtrading.domain.TradeType;

import java.math.BigDecimal;

/**
 * 网格推荐结果 DTO
 * 用于价格推荐接口的返回值
 */
public class GridSuggestionDTO {

    private Long gridLineId;          // 推荐的网格ID
    private Integer level;            // 网格层级
    private GridType gridType;        // 网格类型（SMALL/MEDIUM/LARGE）
    private TradeType suggestedType;  // 推荐的交易类型（BUY/SELL）
    private BigDecimal buyPrice;      // 网格买入价
    private BigDecimal sellPrice;     // 网格卖出价
    private GridLineState state;      // 网格当前状态
    private BigDecimal inputPrice;    // 用户输入的价格
    private BigDecimal priceDiff;     // 价格差异

    public Long getGridLineId() {
        return gridLineId;
    }

    public void setGridLineId(Long gridLineId) {
        this.gridLineId = gridLineId;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public GridType getGridType() {
        return gridType;
    }

    public void setGridType(GridType gridType) {
        this.gridType = gridType;
    }

    public TradeType getSuggestedType() {
        return suggestedType;
    }

    public void setSuggestedType(TradeType suggestedType) {
        this.suggestedType = suggestedType;
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

    public GridLineState getState() {
        return state;
    }

    public void setState(GridLineState state) {
        this.state = state;
    }

    public BigDecimal getInputPrice() {
        return inputPrice;
    }

    public void setInputPrice(BigDecimal inputPrice) {
        this.inputPrice = inputPrice;
    }

    public BigDecimal getPriceDiff() {
        return priceDiff;
    }

    public void setPriceDiff(BigDecimal priceDiff) {
        this.priceDiff = priceDiff;
    }
}

