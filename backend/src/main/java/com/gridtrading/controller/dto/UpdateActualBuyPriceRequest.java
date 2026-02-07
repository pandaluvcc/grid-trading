package com.gridtrading.controller.dto;

import java.math.BigDecimal;

/**
 * 更新网格实际买入价请求
 */
public class UpdateActualBuyPriceRequest {

    private Long gridLineId;
    private BigDecimal actualBuyPrice;

    public UpdateActualBuyPriceRequest() {
    }

    public Long getGridLineId() {
        return gridLineId;
    }

    public void setGridLineId(Long gridLineId) {
        this.gridLineId = gridLineId;
    }

    public BigDecimal getActualBuyPrice() {
        return actualBuyPrice;
    }

    public void setActualBuyPrice(BigDecimal actualBuyPrice) {
        this.actualBuyPrice = actualBuyPrice;
    }
}
