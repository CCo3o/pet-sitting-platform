package com.petsitting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petsitting.auth.AuthService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:auth;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired AuthService auth;
    @Autowired JwtEncoder encoder;
    String bearer(String username) { return "Bearer " + auth.login(username, "PetDemo123!").accessToken(); }
    String unique() { return "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20); }
    @Test void registrationReturnsCurrentUserAndStoresOnlyHash() throws Exception {
        String username = unique();
        String body = json.writeValueAsString(Map.of("username", username, "password", "Correct123!", "displayName", "小张", "role", "OWNER"));
        var response = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("OWNER"))
            .andExpect(jsonPath("$.user.password").doesNotExist()).andReturn();
        var node = json.readTree(response.getResponse().getContentAsString());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + node.get("accessToken").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(username)).andExpect(jsonPath("$.displayName").value("小张"));
        String hash = db.queryForObject("SELECT password_hash FROM credentials WHERE username=?", String.class, username);
        assertThat(hash).startsWith("$2a$10$").isNotEqualTo("Correct123!");
        mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("username", username.toUpperCase(), "password", "Correct123!"))))
            .andExpect(status().isOk());
    }
    @Test void duplicateUsernameRollsBackAccountAndAdminRegistrationRejected() throws Exception {
        String username = unique();
        auth.register(username, "Correct123!", "小张", "OWNER");
        int before = db.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class);
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("username", username.toUpperCase(), "password", "Correct123!", "displayName", "小李", "role", "SITTER"))))
            .andExpect(status().isConflict());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class)).isEqualTo(before);
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("username", unique(), "password", "Correct123!", "displayName", "管理员", "role", "ADMIN"))))
            .andExpect(status().isBadRequest());
    }
    @Test void wrongPasswordAndUnknownUserHaveSameError() throws Exception {
        for (String username : new String[]{"owner", "unknown_user"}) {
            mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("username", username, "password", "Wrong123!"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("用户名或密码错误"));
        }
    }
    @Test void spoofedUserHeaderCannotOverrideSignedIdentity() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", bearer("owner")).header("X-User-Id", "4"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1)).andExpect(jsonPath("$.role").value("OWNER"));
        mvc.perform(post("/api/pets").header("Authorization", bearer("owner")).header("X-User-Id", "3").contentType("application/json").content("{\"name\":\"团子\",\"notes\":\"换水\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.OWNER_ID").value(1));
        mvc.perform(get("/api/auth/me").header("X-User-Id", "4")).andExpect(status().isUnauthorized());
    }
    @Test void adminRouteIsCheckedByBackend() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", bearer("owner"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", bearer("sitter"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", bearer("admin")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mvc.perform(post("/api/slots").header("Authorization", bearer("owner")).contentType("application/json").content(json.writeValueAsString(Map.of("start", OffsetDateTime.now().plusDays(1).toString(), "end", OffsetDateTime.now().plusDays(1).plusHours(1).toString(), "priceCents", 5000))))
            .andExpect(status().isForbidden());
    }
    @Test void logoutRevokesOnlyCurrentSession() throws Exception {
        String first = bearer("owner"), other = bearer("owner");
        mvc.perform(post("/api/auth/logout").header("Authorization", first)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", other)).andExpect(status().isOk());
    }
    @Test void tamperedExpiredAndUnsignedTokensRejected() throws Exception {
        String original = auth.login("owner", "PetDemo123!").accessToken();
        String[] parts = original.split("\\.");
        String changed = parts[0] + "." + parts[1] + "." + (parts[2].charAt(0) == 'A' ? 'B' : 'A') + parts[2].substring(1);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + changed)).andExpect(status().isUnauthorized());
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO login_sessions VALUES (?,?,?)", id, 1, OffsetDateTime.now().plusHours(1));
        var claims = JwtClaimsSet.builder().issuer("pet-sitting-platform").subject("1").id(id).issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600)).build();
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    }
    @Test void concurrentDuplicateRegistrationCreatesOneAccount() throws Exception {
        String username = unique();
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> call = () -> {
                gate.await();
                try { auth.register(username, "Correct123!", "小张", "OWNER"); return 1; }
                catch (ResponseStatusException ex) { assertThat(ex.getStatusCode().value()).isEqualTo(409); return 0; }
            };
            var first = pool.submit(call); var second = pool.submit(call); gate.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM credentials WHERE username=?", Integer.class, username)).isEqualTo(1);
    }
    @Test void passwordValidationRejectsWeakAndOverlongUtf8Values() throws Exception {
        for (String password : new String[]{"123", "密".repeat(25)})
            mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("username", unique(), "password", password, "displayName", "小张", "role", "OWNER"))))
                .andExpect(status().isBadRequest());
    }
}
