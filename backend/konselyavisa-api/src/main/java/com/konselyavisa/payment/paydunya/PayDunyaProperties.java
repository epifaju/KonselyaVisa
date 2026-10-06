package com.konselyavisa.payment.paydunya;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "konselyavisa.paydunya")
public class PayDunyaProperties {

    private String masterKey = "";
    private String privateKey = "";
    private String token = "";
    /** Sandbox: https://app.paydunya.com/sandbox-api — live: https://app.paydunya.com/api */
    private String apiBaseUrl = "https://app.paydunya.com/sandbox-api";
    private String callbackUrl = "http://localhost:18083/api/v1/payments/webhooks/PAYDUNYA";
    private String returnUrl = "http://localhost:5175/?paydunya=return";
    private String cancelUrl = "http://localhost:5175/?paydunya=cancel";
    private String storeName = "KonselyaVisa";

    public boolean isConfigured() {
        return notBlank(masterKey) && notBlank(privateKey) && notBlank(token);
    }

    public boolean isWebhookConfigured() {
        return notBlank(masterKey);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }
}
