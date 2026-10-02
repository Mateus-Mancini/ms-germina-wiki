package com.wikigerminare.auth;

import com.wikigerminare.auth.dto.RegisterRequest;
import com.wikigerminare.users.User;
import com.wikigerminare.users.UserRepository;
import com.wikigerminare.users.dto.OwnUserProfileResponse;
import jakarta.validation.Validator;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder, Validator validator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    @Transactional
    public OwnUserProfileResponse register(RegisterRequest request) {
        validate(request);
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = User.registerMember(UUID.randomUUID(), request.getName(), request.getEmail(),
                passwordEncoder.encode(request.getPassword()), Instant.now());
        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Flush exposes concurrent duplicate inserts inside this transaction, before returning success.
            if (isEmailCollision(exception)) {
                throw new EmailAlreadyRegisteredException();
            }
            throw exception;
        }

        return new OwnUserProfileResponse(saved.getId(), saved.getName(), saved.getEmail(),
                saved.getAvatarUrl(), saved.getBio());
    }

    private void validate(RegisterRequest request) {
        if (request == null || request.hasUnknownProperties() || !validator.validate(request).isEmpty()
                || request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new RegistrationValidationException();
        }
    }

    private boolean isEmailCollision(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && "users_email_key".equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
