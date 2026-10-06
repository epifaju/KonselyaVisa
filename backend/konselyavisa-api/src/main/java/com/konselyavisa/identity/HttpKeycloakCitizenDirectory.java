package com.konselyavisa.identity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.konselyavisa.common.exception.BusinessException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class HttpKeycloakCitizenDirectory implements KeycloakCitizenDirectory {

    private static final Logger log = LoggerFactory.getLogger(HttpKeycloakCitizenDirectory.class);
    private static final String ORGANIZATION_ATTRIBUTE = "organization_id";
    private static final String CITIZEN_ROLE = "CITIZEN";

    private final KeycloakAdminProperties properties;
    private final RestClient restClient;
    private final Object tokenLock = new Object();
    private String cachedToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public HttpKeycloakCitizenDirectory(KeycloakAdminProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(trimSlash(properties.getBaseUrl())).build();
    }

    @Override
    public KeycloakCitizenCreated createCitizen(String email, String password, String displayName, UUID organizationId) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        String token = accessToken();
        ensureOrganizationAttributeDeclared(token);
        String userId = createUser(token, normalized, password, displayName, organizationId);
        bindOrganization(token, userId, organizationId);
        assignCitizenRole(token, userId);
        return new KeycloakCitizenCreated(userId, normalized);
    }

    private String createUser(
            String token, String email, String password, String displayName, UUID organizationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", email);
        body.put("email", email);
        body.put("enabled", true);
        body.put("emailVerified", true);
        applyName(body, displayName);
        body.put("credentials", List.of(Map.of("type", "password", "value", password, "temporary", false)));
        body.put("attributes", Map.of("organization_id", List.of(organizationId.toString())));
        try {
            ResponseEntity<Void> response = restClient
                    .post()
                    .uri("/admin/realms/{realm}/users", properties.getRealm())
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.value() == 409, (request, res) -> {
                        throw BusinessException.conflict("error.account.email_exists");
                    })
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        log.warn("Keycloak user create failed with status {}", res.getStatusCode().value());
                        throw BusinessException.serviceUnavailable("error.account.unavailable");
                    })
                    .toBodilessEntity();
            String userId = userIdFromLocation(response);
            if (userId != null) {
                return userId;
            }
            return findUserId(token, email);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            if (ex.getCause() instanceof BusinessException business) {
                throw business;
            }
            log.warn("Keycloak user create unavailable");
            throw BusinessException.serviceUnavailable("error.account.unavailable");
        }
    }

    private void ensureOrganizationAttributeDeclared(String token) {
        JsonNode profile = restClient
                .get()
                .uri("/admin/realms/{realm}/users/profile", properties.getRealm())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    log.warn("Keycloak user profile lookup failed with status {}", res.getStatusCode().value());
                    throw BusinessException.serviceUnavailable("error.account.unavailable");
                })
                .body(JsonNode.class);
        if (!(profile instanceof ObjectNode objectProfile)) {
            throw BusinessException.serviceUnavailable("error.account.unavailable");
        }
        ArrayNode attributes = objectProfile.has("attributes") && objectProfile.get("attributes").isArray()
                ? (ArrayNode) objectProfile.get("attributes")
                : objectProfile.putArray("attributes");
        boolean declared = false;
        for (JsonNode attribute : attributes) {
            if (ORGANIZATION_ATTRIBUTE.equals(attribute.path("name").asText())) {
                declared = true;
                break;
            }
        }
        if (!declared) {
            ObjectNode organization = attributes.addObject();
            organization.put("name", ORGANIZATION_ATTRIBUTE);
            organization.put("displayName", ORGANIZATION_ATTRIBUTE);
            organization.put("multivalued", false);
            ObjectNode permissions = organization.putObject("permissions");
            permissions.putArray("view").add("admin").add("user");
            permissions.putArray("edit").add("admin");
        }
        objectProfile.put("unmanagedAttributePolicy", "ADMIN_EDIT");
        restClient
                .put()
                .uri("/admin/realms/{realm}/users/profile", properties.getRealm())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectProfile)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    log.warn("Keycloak user profile update failed with status {}", res.getStatusCode().value());
                    throw BusinessException.serviceUnavailable("error.account.unavailable");
                })
                .toBodilessEntity();
    }

    private void bindOrganization(String token, String userId, UUID organizationId) {
        JsonNode user = restClient
                .get()
                .uri("/admin/realms/{realm}/users/{id}", properties.getRealm(), userId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    log.warn("Keycloak user lookup failed with status {}", res.getStatusCode().value());
                    throw BusinessException.serviceUnavailable("error.account.unavailable");
                })
                .body(JsonNode.class);
        if (!(user instanceof ObjectNode objectUser)) {
            throw BusinessException.serviceUnavailable("error.account.unavailable");
        }
        ObjectNode attributes = objectUser.has("attributes") && objectUser.get("attributes").isObject()
                ? (ObjectNode) objectUser.get("attributes")
                : objectUser.putObject("attributes");
        ArrayNode values = attributes.putArray(ORGANIZATION_ATTRIBUTE);
        values.add(organizationId.toString());
        restClient
                .put()
                .uri("/admin/realms/{realm}/users/{id}", properties.getRealm(), userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectUser)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    log.warn("Keycloak organization bind failed with status {}", res.getStatusCode().value());
                    throw BusinessException.serviceUnavailable("error.account.unavailable");
                })
                .toBodilessEntity();
    }

    private void assignCitizenRole(String token, String userId) {
        KeycloakRole role = restClient
                .get()
                .uri("/admin/realms/{realm}/roles/{role}", properties.getRealm(), CITIZEN_ROLE)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    log.warn("Keycloak role lookup failed with status {}", res.getStatusCode().value());
                    throw BusinessException.serviceUnavailable("error.account.unavailable");
                })
                .body(KeycloakRole.class);
        if (role == null || role.id() == null) {
            throw BusinessException.serviceUnavailable("error.account.unavailable");
        }
        restClient
                .post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", properties.getRealm(), userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    log.warn("Keycloak role assign failed with status {}", res.getStatusCode().value());
                    throw BusinessException.serviceUnavailable("error.account.unavailable");
                })
                .toBodilessEntity();
    }

    private String findUserId(String token, String email) {
        KeycloakUser[] users = restClient
                .get()
                .uri(uri -> uri.path("/admin/realms/{realm}/users")
                        .queryParam("email", email)
                        .queryParam("exact", "true")
                        .build(properties.getRealm()))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    throw BusinessException.serviceUnavailable("error.account.unavailable");
                })
                .body(KeycloakUser[].class);
        if (users == null || users.length == 0 || users[0].id() == null) {
            throw BusinessException.serviceUnavailable("error.account.unavailable");
        }
        return users[0].id();
    }

    private String accessToken() {
        synchronized (tokenLock) {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
                return cachedToken;
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "password");
            form.add("client_id", properties.getClientId());
            form.add("username", properties.getUsername());
            form.add("password", properties.getPassword());
            TokenResponse token;
            try {
                token = restClient
                        .post()
                        .uri("/realms/{realm}/protocol/openid-connect/token", properties.getTokenRealm())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (request, res) -> {
                            log.warn("Keycloak admin token failed with status {}", res.getStatusCode().value());
                            throw BusinessException.serviceUnavailable("error.account.unavailable");
                        })
                        .body(TokenResponse.class);
            } catch (BusinessException ex) {
                throw ex;
            } catch (RestClientException ex) {
                log.warn("Keycloak admin token unavailable");
                throw BusinessException.serviceUnavailable("error.account.unavailable");
            }
            if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
                throw BusinessException.serviceUnavailable("error.account.unavailable");
            }
            long expiresIn = token.expiresIn() > 30 ? token.expiresIn() - 30 : 30;
            cachedToken = token.accessToken();
            tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
            return cachedToken;
        }
    }

    private static void applyName(Map<String, Object> body, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return;
        }
        String trimmed = displayName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            body.put("firstName", trimmed);
            return;
        }
        body.put("firstName", trimmed.substring(0, space).trim());
        body.put("lastName", trimmed.substring(space + 1).trim());
    }

    private static String userIdFromLocation(ResponseEntity<Void> response) {
        if (response.getHeaders().getLocation() == null) {
            return null;
        }
        String path = response.getHeaders().getLocation().toString();
        int slash = path.lastIndexOf('/');
        if (slash < 0 || slash == path.length() - 1) {
            return null;
        }
        return path.substring(slash + 1);
    }

    private static String trimSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KeycloakRole(String id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KeycloakUser(String id) {}
}
