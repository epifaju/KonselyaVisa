package com.konselyavisa.payment.cinetpay;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "konselyavisa.cinetpay")
public class CinetPayProperties {

    private String apiKey = "";
    private String siteId = "";
    private String secretKey = "";
    private String apiBaseUrl = "https://api-checkout.cinetpay.com";
    private String notifyUrl = "http://localhost:18083/api/v1/payments/webhooks/CINETPAY";
    private String returnUrl = "http://localhost:5175/?cinetpay=return";
    private String channels = "ALL";

    public boolean isConfigured() {
        return notBlank(apiKey) && notBlank(siteId) && notBlank(secretKey);
    }

    public boolean isWebhookConfigured() {
        return notBlank(secretKey);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getChannels() {
        return channels;
    }

    public void setChannels(String channels) {
        this.channels = channels;
    }
}
