package com.wikigerminare.auth;

import com.wikigerminare.auth.security.JwtConfiguration;
import com.wikigerminare.config.SecurityConfig;
import com.wikigerminare.users.dto.OwnUserProfileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RegistrationController.class)
@Import({AuthExceptionHandler.class, SecurityConfig.class, JwtConfiguration.class,
        RegistrationControllerTest.WebSecurityTestConfiguration.class})
class RegistrationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RegistrationService service;

    @Test
    void anonymousRegistrationReturnsCreatedLocationAndSafeProfile() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.register(any())).thenReturn(new OwnUserProfileResponse(
                id, "Student", "student@example.com", null, null));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/users/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Student"))
                .andExpect(jsonPath("$.email").value("student@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void malformedRequestsDoNotCallService() throws Exception {
        for (String body : new String[] {"{", "", "null", "[]"}) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Invalid request"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void serviceValidationReturnsGeneric400AndDuplicateReturns409() throws Exception {
        doThrow(new RegistrationValidationException()).when(service).register(any());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"Invalid request\"}"));

        doThrow(new EmailAlreadyRegisteredException()).when(service).register(any());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"error\":\"Email already registered\"}"));
    }

    @Test
    void unsupportedMethodsAndOtherRoutesRemainProtected() throws Exception {
        mvc.perform(get("/api/auth/register")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void dtoSerializationDoesNotExposePasswordAndUnknownFieldsAreDetected() {
        JsonMapper mapper = JsonMapper.builder().build();
        var request = mapper.readValue(validBody(), com.wikigerminare.auth.dto.RegisterRequest.class);
        assertThat(mapper.writeValueAsString(request)).doesNotContain("password123", "password");
        assertThat(request.toString()).doesNotContain("password123");

        var unknown = mapper.readValue("""
                {"name":"Student","email":"student@example.com","password":"password123","role":"admin"}
                """, com.wikigerminare.auth.dto.RegisterRequest.class);
        assertThat(unknown.hasUnknownProperties()).isTrue();
    }

    private static String validBody() {
        return "{\"name\":\"Student\",\"email\":\"student@example.com\",\"password\":\"password123\"}";
    }

    @TestConfiguration
    @EnableWebSecurity
    static class WebSecurityTestConfiguration {
    }
}
