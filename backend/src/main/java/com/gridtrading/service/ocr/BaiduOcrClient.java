package com.gridtrading.service.ocr;

import com.baidu.aip.ocr.AipOcr;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Baidu OCR client wrapper.
 */
@Service
public class BaiduOcrClient {

    private final AipOcr client;

    public BaiduOcrClient(
            @Value("${baidu.ocr.app-id:}") String appId,
            @Value("${baidu.ocr.api-key:}") String apiKey,
            @Value("${baidu.ocr.secret-key:}") String secretKey
    ) {
        if (isBlank(appId) || isBlank(apiKey) || isBlank(secretKey)) {
            this.client = null;
        } else {
            this.client = new AipOcr(appId, apiKey, secretKey);
        }
    }

    public String recognize(MultipartFile file) throws IOException {
        if (client == null) {
            throw new IllegalStateException("Baidu OCR credentials are not configured");
        }

        HashMap<String, String> options = new HashMap<>();
        options.put("language_type", "CHN_ENG");
        options.put("detect_direction", "true");

        JSONObject response = client.basicAccurateGeneral(file.getBytes(), options);
        if (response.has("error_code")) {
            throw new IllegalStateException("Baidu OCR error: " + response.optString("error_msg"));
        }

        JSONArray words = response.optJSONArray("words_result");
        if (words == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < words.length(); i++) {
            JSONObject item = words.optJSONObject(i);
            if (item != null) {
                String text = item.optString("words", "").trim();
                if (!text.isEmpty()) {
                    builder.append(text).append('\n');
                }
            }
        }

        return builder.toString().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
