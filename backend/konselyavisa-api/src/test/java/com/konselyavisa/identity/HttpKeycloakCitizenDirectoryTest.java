package com.konselyavisa.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.konselyavisa.common.exception.BusinessException;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpKeycloakCitizenDirectoryTest {

    private static final String BASE = "https://keycloak.test";
    private static final String REALM = "konselyavisa";

    private MockRestServiceServer server;
    private HttpKeycloakCitizenDirectory directory;

    @BeforeEach
    void setUp() {
        KeycloakAdminProperties properties = new KeycloakAdminProperties();
        properties.setBaseUrl(BASE);
        properties.setRealm(REALM);
        properties.setTokenRealm("master");
        properties.setClientId("admin-cli");
        properties.setUsername("admin");
        properties.setPassword("admin");
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        directory = new HttpKeycloakCitizenDirectory(properties, builder.build());
    }

    @Test
    void createCitizenRunsFullProvisioningFlow() {
        UUID orgId = UUID.randomUUID();
        String userId = "user-abc";
        expectToken();
        expectProfileGetWithoutOrgAttribute();
        expectProfilePut();
        expectUserCreate(userId);
        expectUserGetAndBind(userId, orgId);
        expectCitizenRoleAssign(userId);

        KeycloakCitizenCreated created =
                directory.createCitizen(" Citizen@Example.COM ", "Secret1!", "Ada Lovelace", orgId);

        assertThat(created.subject()).isEqualTo(userId);
        assertThat(created.username()).isEqualTo("citizen@example.com");
        server.verify();
    }

    @Test
    void createCitizenReusesCachedAdminToken() {
        UUID orgId = UUID.randomUUID();
        expectToken();
        expectProfileGetWithOrgAttribute();
        expectProfilePut();
        expectUserCreate("user-1");
        expectUserGetAndBind("user-1", orgId);
        expectCitizenRoleAssign("user-1");
        expectProfileGetWithOrgAttribute();
        expectProfilePut();
        expectUserCreate("user-2");
        expectUserGetAndBind("user-2", orgId);
        expectCitizenRoleAssign("user-2");

        directory.createCitizen("one@example.com", "Secret1!", "One", orgId);
        directory.createCitizen("two@example.com", "Secret1!", "Two", orgId);

        server.verify();
    }

    @Test
    void createCitizenRejectsDuplicateEmail() {
        expectToken();
        expectProfileGetWithOrgAttribute();
        expectProfilePut();
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> directory.createCitizen("dup@example.com", "Secret1!", "Dup", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.email_exists");
    }

    @Test
    void createCitizenFailsWhenAdminTokenUnavailable() {
        server.expect(requestTo(BASE + "/realms/master/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> directory.createCitizen("a@b.c", "Secret1!", "A", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.unavailable");
    }

    @Test
    void createCitizenFallsBackToEmailLookupWhenLocationMissing() {
        UUID orgId = UUID.randomUUID();
        expectToken();
        expectProfileGetWithOrgAttribute();
        expectProfilePut();
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED));
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users?email=lookup@example.com&exact=true"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"looked-up\"}]", MediaType.APPLICATION_JSON));
        expectUserGetAndBind("looked-up", orgId);
        expectCitizenRoleAssign("looked-up");

        KeycloakCitizenCreated created =
                directory.createCitizen("lookup@example.com", "Secret1!", "Look", orgId);

        assertThat(created.subject()).isEqualTo("looked-up");
        server.verify();
    }

    @Test
    void createCitizenFailsWhenUserCreateReturnsServerError() {
        expectToken();
        expectProfileGetWithOrgAttribute();
        expectProfilePut();
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> directory.createCitizen("a@b.c", "Secret1!", "A", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.unavailable");
    }

    @Test
    void createCitizenFailsWhenProfileGetFails() {
        expectToken();
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users/profile"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> directory.createCitizen("a@b.c", "Secret1!", "A", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.unavailable");
    }

    @Test
    void createCitizenFailsWhenProfilePutFails() {
        expectToken();
        expectProfileGetWithOrgAttribute();
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users/profile"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> directory.createCitizen("a@b.c", "Secret1!", "A", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.unavailable");
    }

    @Test
    void createCitizenFailsWhenProfileBodyIsNotObject() {
        expectToken();
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users/profile"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> directory.createCitizen("a@b.c", "Secret1!", "A", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.unavailable");
    }

    @Test
    void createCitizenFailsWhenEmailLookupEmpty() {
        expectToken();
        expectProfileGetWithOrgAttribute();
        expectProfilePut();
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED));
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users?email=missing@example.com&exact=true"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() ->
                        directory.createCitizen("missing@example.com", "Secret1!", "M", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.unavailable");
    }

    @Test
    void createCitizenFailsWhenRoleMissing() {
        UUID orgId = UUID.randomUUID();
        String userId = "user-role";
        expectToken();
        expectProfileGetWithOrgAttribute();
        expectProfilePut();
        expectUserCreate(userId);
        expectUserGetAndBind(userId, orgId);
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/roles/CITIZEN"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"name\":\"CITIZEN\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> directory.createCitizen("role@example.com", "Secret1!", "R", orgId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.unavailable");
    }

    @Test
    void createCitizenFailsWhenRoleAssignFails() {
        UUID orgId = UUID.randomUUID();
        String userId = "user-role-assign";
        expectToken();
        expectProfileGetWithOrgAttribute();
        expectProfilePut();
        expectUserCreate(userId);
        expectUserGetAndBind(userId, orgId);
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/roles/CITIZEN"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"CITIZEN\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users/" + userId + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> directory.createCitizen("role2@example.com", "Secret1!", "R", orgId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.unavailable");
    }

    @Test
    void createCitizenFailsWhenBindUserGetFails() {
        UUID orgId = UUID.randomUUID();
        String userId = "user-bind";
        expectToken();
        expectProfileGetWithOrgAttribute();
        expectProfilePut();
        expectUserCreate(userId);
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users/" + userId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> directory.createCitizen("bind@example.com", "Secret1!", "B", orgId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.unavailable");
    }

    @Test
    void createCitizenFailsWhenTokenResponseEmpty() {
        server.expect(requestTo(BASE + "/realms/master/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"\",\"expires_in\":300}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> directory.createCitizen("a@b.c", "Secret1!", "A", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.unavailable");
    }

    @Test
    void createCitizenUsesShortTokenExpiryFloor() {
        UUID orgId = UUID.randomUUID();
        server.expect(requestTo(BASE + "/realms/master/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"tok-short\",\"expires_in\":10}", MediaType.APPLICATION_JSON));
        expectProfileGetWithOrgAttribute();
        expectProfilePut();
        expectUserCreate("user-short");
        expectUserGetAndBind("user-short", orgId);
        expectCitizenRoleAssign("user-short");

        assertThat(directory.createCitizen("short@example.com", "Secret1!", null, orgId).subject())
                .isEqualTo("user-short");
        server.verify();
    }

    private void expectToken() {
        server.expect(requestTo(BASE + "/realms/master/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"tok-1\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
    }

    private void expectProfileGetWithoutOrgAttribute() {
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users/profile"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"attributes\":[]}", MediaType.APPLICATION_JSON));
    }

    private void expectProfileGetWithOrgAttribute() {
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users/profile"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"attributes\":[{\"name\":\"organization_id\"}]}", MediaType.APPLICATION_JSON));
    }

    private void expectProfilePut() {
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users/profile"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
    }

    private void expectUserCreate(String userId) {
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .location(URI.create(BASE + "/admin/realms/" + REALM + "/users/" + userId)));
    }

    private void expectUserGetAndBind(String userId, UUID orgId) {
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users/" + userId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"" + userId + "\",\"attributes\":{}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users/" + userId))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
    }

    private void expectCitizenRoleAssign(String userId) {
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/roles/CITIZEN"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"CITIZEN\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users/" + userId + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
    }
}
