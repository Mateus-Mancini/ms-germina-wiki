package com.wikigerminare.auth;

import com.wikigerminare.auth.dto.RegisterRequest;
import com.wikigerminare.users.User;
import com.wikigerminare.users.UserRepository;
import com.wikigerminare.users.UserRole;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistrationServiceTest {

    private UserRepository repository;
    private PasswordEncoder encoder;
    private ValidatorFactory validators;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        validators = Validation.buildDefaultValidatorFactory();
        service = new RegistrationService(repository, encoder, validators.getValidator());
    }

    @AfterEach
    void closeValidators() {
        validators.close();
    }

    @Test
    void createsMemberWithEncodedUnchangedPasswordAndSafeProfile() {
        RegisterRequest request = request("  Student  ", "  Student@example.com  ", " secret pass ");
        when(encoder.encode(" secret pass ")).thenReturn("encoded-hash");
        when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Instant before = Instant.now();

        var response = service.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(repository).saveAndFlush(userCaptor.capture());
        User user = userCaptor.getValue();
        assertThat(user.getId()).isNotNull().isEqualTo(response.id());
        assertThat(user.getName()).isEqualTo("Student");
        assertThat(user.getEmail()).isEqualTo("Student@example.com");
        assertThat(user.getRole()).isEqualTo(UserRole.MEMBER);
        assertThat(user.getPasswordHash()).isEqualTo("encoded-hash");
        assertThat(user.getCreatedAt()).isBetween(before, Instant.now());
        assertThat(user.getUpdatedAt()).isEqualTo(user.getCreatedAt());
        assertThat(response.avatarUrl()).isNull();
        assertThat(response.bio()).isNull();
        assertThat(request.toString()).doesNotContain(" secret pass ");
        assertThat(response.toString()).doesNotContain("encoded-hash", " secret pass ");
        verify(repository).findByEmail("Student@example.com");
        verify(encoder).encode(" secret pass ");
    }

    @Test
    void existingEmailIsConflictWithoutEncodingOrWriting() {
        when(repository.findByEmail("student@example.com")).thenReturn(Optional.of(mock(User.class)));

        assertThatThrownBy(() -> service.register(request("Student", "student@example.com", "password123")))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("Email already registered");
        verifyNoInteractions(encoder);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void translatesConcurrentEmailConstraintCollision() {
        when(encoder.encode(any())).thenReturn("hash");
        when(repository.saveAndFlush(any())).thenThrow(integrityFailure("23505", "users_email_key"));

        assertThatThrownBy(() -> service.register(request("Student", "student@example.com", "password123")))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("Email already registered");
        verify(repository, times(1)).findByEmail(any());
    }

    @Test
    void doesNotDisguiseOtherStorageFailuresAsDuplicateEmail() {
        when(encoder.encode(any())).thenReturn("hash");
        for (DataIntegrityViolationException failure : new DataIntegrityViolationException[] {
                integrityFailure("23505", "users_pkey"),
                integrityFailure("23502", "users_email_key"),
                new DataIntegrityViolationException("Internal persistence detail")}) {
            doThrow(failure).when(repository).saveAndFlush(any());
            assertThatThrownBy(() -> service.register(request("Student", "student@example.com", "password123")))
                    .isSameAs(failure);
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "       ", "short", "1234567"})
    void rejectsInvalidPasswordBeforePersistence(String password) {
        assertInvalid(request("Student", "student@example.com", password));
    }

    @Test
    void enforcesUtf8ByteLimitWithoutTruncation() {
        assertInvalid(request("Student", "student@example.com", "é".repeat(37)));
        assertInvalid(request("Student", "student@example.com", "a".repeat(73)));

        String boundary = "é".repeat(36);
        when(encoder.encode(boundary)).thenReturn("hash");
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.register(request("Student", "student@example.com", boundary));
        verify(encoder).encode(boundary);
    }

    @Test
    void rejectsMissingBlankMalformedOrOversizedProfileAndUnknownFields() {
        assertInvalid(null);
        assertInvalid(request(null, "student@example.com", "password123"));
        assertInvalid(request(" \t ", "student@example.com", "password123"));
        assertInvalid(request("a".repeat(151), "student@example.com", "password123"));
        assertInvalid(request("Student", null, "password123"));
        assertInvalid(request("Student", "not-an-email", "password123"));
        assertInvalid(request("Student", " ", "password123"));
        assertInvalid(request("Student", "a".repeat(244) + "@example.com", "password123"));
        RegisterRequest unknown = request("Student", "student@example.com", "password123");
        unknown.rejectUnknownProperty("role", "admin");
        assertInvalid(unknown);
    }

    private void assertInvalid(RegisterRequest request) {
        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(RegistrationValidationException.class)
                .hasMessage("Invalid request");
        verifyNoInteractions(repository, encoder);
    }

    private static DataIntegrityViolationException integrityFailure(String state, String constraint) {
        return new DataIntegrityViolationException("Internal SQL details",
                new ConstraintViolationException("Internal SQL details", new SQLException("private", state), constraint));
    }

    static RegisterRequest request(String name, String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setName(name);
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
