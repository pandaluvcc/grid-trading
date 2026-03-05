package com.gridtrading.controller.dto;

/**
 * 警告信息
 */
public class SuggestionWarning {
    private String type;
    private String message;

    public SuggestionWarning() {
    }

    public SuggestionWarning(String type, String message) {
        this.type = type;
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

