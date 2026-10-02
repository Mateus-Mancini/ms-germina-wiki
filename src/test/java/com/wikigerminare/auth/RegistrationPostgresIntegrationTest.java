package com.wikigerminare.auth;

import com.jayway.jsonpath.JsonPath;
import com.wikigerminare.TestcontainersConfiguration;
import com.wikigerminare.security.AdminOnlyProbeController;
import com.wikigerminare.users.UserRepository;
import com.wikigerminare.users.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AdminOnlyProbeController.class})
class RegistrationPostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository repository;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void anonymousRegistrationCanLoginReadProfileAndCannotPerformAdminOperation() throws Exception {
        String email = freshEmail();
        String password = " secret password ";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("  Student  ", "  " + email + "  ", password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Student"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        var user = repository.findByEmail(email).orElseThrow();
        assertThat(user.getRole()).isEqualTo(UserRole.MEMBER);
        assertThat(user.getPasswordHash()).isNotEqualTo(password).startsWith("$2");
        assertThat(encoder.matches(password, user.getPasswordHash())).isTrue();
        assertThat(encoder.matches(password.strip(), user.getPasswordHash())).isFalse();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isEqualTo(user.getCreatedAt());
        assertThat(user.getAvatarUrl()).isNull();
        assertThat(user.getBio()).isNull();
        assertThat(JsonPath.<String>read(body, "$.id")).isEqualTo(user.getId().toString());

        String token = login(email, password);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/security-test/admin-only").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateEmailLeavesOriginalCredentialsProfileAndRoleUnchanged() throws Exception {
        String email = freshEmail();
        register(email, "password123");
        var original = repository.findByEmail(email).orElseThrow();

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("Other person", " " + email + " ", "different password")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email already registered"));

        var persisted = repository.findByEmail(email).orElseThrow();
        assertThat(persisted.getId()).isEqualTo(original.getId());
        assertThat(persisted.getName()).isEqualTo(original.getName());
        assertThat(persisted.getPasswordHash()).isEqualTo(original.getPasswordHash());
        assertThat(persisted.getRole()).isEqualTo(original.getRole());
        assertThat(count(email)).isEqualTo(1);
        login(email, "password123");
    }

    @Test
    void preservesExistingCaseSensitiveEmailIdentity() throws Exception {
        String email = freshEmail();
        register(email, "password123");
        String differentCase = "SIGNUP" + email.substring("signup".length());
        register(differentCase, "password456");
        assertThat(repository.findByEmail(email).orElseThrow().getId())
                .isNotEqualTo(repository.findByEmail(differentCase).orElseThrow().getId());
        login(email, "password123");
        login(differentCase, "password456");
    }

    @Test
    void validatesUnicodePasswordByteBoundariesAndDoesNotCreateInvalidAccounts() throws Exception {
        String validEmail = freshEmail();
        String boundary = "é".repeat(36);
        register(validEmail, boundary);
        login(validEmail, boundary);

        String invalidEmail = freshEmail();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("Student", invalidEmail, "é".repeat(37))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request"));
        assertThat(count(invalidEmail)).isZero();
    }

    @Test
    void rejectsInvalidAndPrivilegeInjectionPayloadsWithoutCreatingAccounts() throws Exception {
        String email = freshEmail();
        for (String body : List.of(
                json.writeValueAsString(Map.of("name", " ", "email", email, "password", "password123")),
                json.writeValueAsString(Map.of("name", "Student", "email", email, "password", "short")),
                json.writeValueAsString(Map.of("name", "Student", "email", email, "password", "password123", "role", "admin")),
                json.writeValueAsString(Map.of("name", "Student", "email", email, "password", "password123", "id", UUID.randomUUID())),
                json.writeValueAsString(Map.of("name", "Student", "email", email, "password", "password123", "passwordHash", "injected")))) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Invalid request"));
        }
        assertThat(count(email)).isZero();
        register(email, "password123");
    }

    @Test
    void simultaneousRequestsProduceOneCreatedOneConflictAndOneRow() throws Exception {
        String email = freshEmail();
        String body = registration("Student", email, "password123");
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus();
            });
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(count(email)).isEqualTo(1);
        login(email, "password123");
    }

    private void register(String email, String password) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("Student", email, password)))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String registration(String name, String email, String password) {
        return json.writeValueAsString(Map.of("name", name, "email", email, "password", password));
    }

    private int count(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email);
    }

    private static String freshEmail() {
        return "signup-" + UUID.randomUUID() + "@example.com";
    }
}
