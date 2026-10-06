package com.konselyavisa.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "konselyavisa.keycloak.admin")
public class KeycloakAdminProperties {

    private boolean enabled = false;
    private String baseUrl = "http://localhost:8081";
    private String realm = "konselyavisa";
    private String tokenRealm = "master";
    private String clientId = "admin-cli";
    private String username = "admin";
    private String password = "admin";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getTokenRealm() {
        return tokenRealm;
    }

    public void setTokenRealm(String tokenRealm) {
        this.tokenRealm = tokenRealm;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
