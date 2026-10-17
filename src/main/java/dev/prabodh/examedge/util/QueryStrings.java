package dev.prabodh.examedge.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class QueryStrings {

    private QueryStrings() {
    }

    public static String extraParameters(String... keyValuePairs) {
        Map<String, String> parameters = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            String value = keyValuePairs[i + 1];
            if (value != null && !value.isBlank()) {
                parameters.put(keyValuePairs[i], value);
            }
        }
        StringBuilder builder = new StringBuilder();
        parameters.forEach((key, value) -> builder.append('&')
                .append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8)));
        return builder.toString();
    }
}
