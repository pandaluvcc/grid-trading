package com.gridtrading.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gridtrading.domain.TradeType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OCR识别的单条交易记录
 */
public class OcrTradeRecord {

    private TradeType type;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime tradeTime;

    private BigDecimal quantity;
    private BigDecimal amount;
    private BigDecimal price;
    private BigDecimal fee;

    private Long matchedGridLineId;
    private Integer matchedLevel;
    private OcrMatchStatus matchStatus;
    private String matchMessage;

    private boolean opening;
    private boolean closing;
    private boolean forcedMatch;
    private boolean outOfRange;

    public TradeType getType() {
        return type;
    }

    public void setType(TradeType type) {
        this.type = type;
    }

    public LocalDateTime getTradeTime() {
        return tradeTime;
    }

    public void setTradeTime(LocalDateTime tradeTime) {
        this.tradeTime = tradeTime;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public Long getMatchedGridLineId() {
        return matchedGridLineId;
    }

    public void setMatchedGridLineId(Long matchedGridLineId) {
        this.matchedGridLineId = matchedGridLineId;
    }

    public Integer getMatchedLevel() {
        return matchedLevel;
    }

    public void setMatchedLevel(Integer matchedLevel) {
        this.matchedLevel = matchedLevel;
    }

    public OcrMatchStatus getMatchStatus() {
        return matchStatus;
    }

    public void setMatchStatus(OcrMatchStatus matchStatus) {
        this.matchStatus = matchStatus;
    }

    public String getMatchMessage() {
        return matchMessage;
    }

    public void setMatchMessage(String matchMessage) {
        this.matchMessage = matchMessage;
    }

    public boolean isOpening() {
        return opening;
    }

    public void setOpening(boolean opening) {
        this.opening = opening;
    }

    public boolean isClosing() {
        return closing;
    }

    public void setClosing(boolean closing) {
        this.closing = closing;
    }

    public boolean isForcedMatch() {
        return forcedMatch;
    }

    public void setForcedMatch(boolean forcedMatch) {
        this.forcedMatch = forcedMatch;
    }

    public boolean isOutOfRange() {
        return outOfRange;
    }

    public void setOutOfRange(boolean outOfRange) {
        this.outOfRange = outOfRange;
    }
}
