package com.wikigerminare.users;

import com.wikigerminare.integration.AuthenticatedUser;
import com.wikigerminare.integration.AuthenticatedUserProvider;
import com.wikigerminare.service.CommentException;
import com.wikigerminare.users.dto.OwnUserProfileResponse;
import com.wikigerminare.users.dto.PublicUserProfileResponse;
import com.wikigerminare.users.dto.UpdateUserProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public UserService(
            UserRepository userRepository,
            AuthenticatedUserProvider authenticatedUserProvider
    ) {
        this.userRepository = userRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @Transactional(readOnly = true)
    public OwnUserProfileResponse getOwnProfile() {
        UUID userId = currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return toOwnProfile(user);
    }

    @Transactional
    public OwnUserProfileResponse updateOwnProfile(UpdateUserProfileRequest request) {
        UUID userId = currentUserId();
        validateUpdate(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (request.isNameProvided()) {
            user.setName(request.getName());
        }
        if (request.isAvatarUrlProvided()) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.isBioProvided()) {
            user.setBio(request.getBio());
        }

        return toOwnProfile(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public PublicUserProfileResponse getPublicProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return toPublicProfile(user);
    }

    private void validateUpdate(UpdateUserProfileRequest request) {
        if (request == null) {
            throw new UserValidationException("Profile update is required");
        }
        if (!request.getUnknownProperties().isEmpty()) {
            throw new UserValidationException("Unsupported profile field");
        }
        if (!request.isAnyFieldProvided()) {
            throw new UserValidationException("At least one profile field must be provided");
        }
        if (request.isNameProvided()
                && (request.getName() == null
                || request.getName().isBlank()
                || request.getName().length() > 150)) {
            throw new UserValidationException("name must be nonblank and have at most 150 characters");
        }
    }

    private UUID currentUserId() {
        AuthenticatedUser currentUser;
        try {
            currentUser = authenticatedUserProvider.currentUser();
        } catch (CommentException exception) {
            if ("UNAUTHORIZED".equals(exception.code())) {
                throw new UserUnauthorizedException(exception.getMessage());
            }
            throw exception;
        }

        if (currentUser == null || currentUser.id() == null) {
            throw new UserUnauthorizedException("Authentication is required");
        }
        return currentUser.id();
    }

    private OwnUserProfileResponse toOwnProfile(User user) {
        return new OwnUserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getBio());
    }

    private PublicUserProfileResponse toPublicProfile(User user) {
        return new PublicUserProfileResponse(
                user.getId(),
                user.getName(),
                user.getAvatarUrl(),
                user.getBio());
    }
}
