package com.keycode.ilussioness_backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:password-reset;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetApiTest {

    private static final String EMAIL = "admin@ilussiones.com";
    private static final String DEMO_PASSWORD = "TestDemoPass123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Test
    void forgotPasswordAnswersTheSameForKnownAndUnknownEmails() throws Exception {
        String expected = "{\"status\":\"accepted\"}";
        mockMvc.perform(json(post("/api/auth/forgot-password"), "{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().json(expected));
        mockMvc.perform(json(post("/api/auth/forgot-password"), "{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().json(expected));
    }

    @Test
    void resetLinkChangesThePasswordOnceAndCannotBeUsedAsBearerToken() throws Exception {
        String token = passwordResetService.issueToken(accountService.findAccount(EMAIL).orElseThrow());

        mockMvc.perform(get("/api/customer").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(json(post("/api/auth/reset-password"),
                        "{\"token\":\"" + token + "\",\"newPassword\":\"short1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("weak_password"));

        mockMvc.perform(json(post("/api/auth/reset-password"),
                        "{\"token\":\"" + token + "\",\"newPassword\":\"NuevaClave2026\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(login("NuevaClave2026")).andExpect(status().isOk());
        mockMvc.perform(login(DEMO_PASSWORD)).andExpect(status().isUnauthorized());

        mockMvc.perform(json(post("/api/auth/reset-password"),
                        "{\"token\":\"" + token + "\",\"newPassword\":\"OtraClave2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("invalid_reset_token"));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        mockMvc.perform(json(post("/api/auth/reset-password"),
                        "{\"token\":\"not-a-token\",\"newPassword\":\"NuevaClave2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("invalid_reset_token"));
    }

    private MockHttpServletRequestBuilder login(String password) {
        return json(post("/api/auth/login"), "{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}");
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
