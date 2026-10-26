package com.petsitting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import com.petsitting.auth.AuthService;
import org.springframework.test.context.ActiveProfiles;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:api;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingApiTest {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    private String token(String username) { return "Bearer " + auth.login(username, "PetDemo123!").accessToken(); }
    @Test void healthAndRequiredIdentity() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/pets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pets").header("X-User-Id", "999")).andExpect(status().isUnauthorized());
    }
    @Test void validationAndRoleErrors() throws Exception {
        mvc.perform(post("/api/pets").header("Authorization", token("owner")).contentType("application/json").content("{\"name\":\"\",\"notes\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(post("/api/pets").header("Authorization", token("sitter")).contentType("application/json").content("{\"name\":\"团子\",\"notes\":\"\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders").header("Authorization", token("owner")).header("Idempotency-Key", " ").contentType("application/json").content("{\"petId\":\"a\",\"slotId\":\"b\"}"))
            .andExpect(status().isBadRequest());
    }
}
