package com.google.copybara;

import java.util.Optional;
 
public class BaseUrlConfig {
    private static final String DEFAULT_BASE_URL = "https://github.com/";
    private static String baseUrl;
 
    public static void setBaseUrl(String url) {
        baseUrl = url;
    }
 
    public static String getBaseUrl() {
        return Optional.ofNullable(baseUrl).orElse(DEFAULT_BASE_URL);
    }
}
