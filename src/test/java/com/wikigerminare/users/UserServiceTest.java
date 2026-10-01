package com.wikigerminare.users;

import com.wikigerminare.integration.AuthenticatedUser;
import com.wikigerminare.integration.AuthenticatedUserProvider;
import com.wikigerminare.users.dto.OwnUserProfileResponse;
import com.wikigerminare.users.dto.PublicUserProfileResponse;
import com.wikigerminare.users.dto.UpdateUserProfileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticatedUserProvider authenticatedUserProvider;

    private UserService userService;
    private UUID authenticatedId;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, authenticatedUserProvider);
        authenticatedId = UUID.randomUUID();
    }

    @Test
    void getOwnProfileUsesAuthenticatedIdentityAndMapsPrivateAllowlist() {
        User user = user(authenticatedId);
        when(authenticatedUserProvider.currentUser())
                .thenReturn(new AuthenticatedUser(authenticatedId, false));
        when(userRepository.findById(authenticatedId)).thenReturn(Optional.of(user));

        OwnUserProfileResponse response = userService.getOwnProfile();

        assertEquals(authenticatedId, response.id());
        assertEquals("Member", response.name());
        assertEquals("member@example.com", response.email());
        assertEquals("https://example.com/avatar.png", response.avatarUrl());
        assertEquals("About me", response.bio());
        verify(userRepository).findById(authenticatedId);
    }

    @Test
    void getOwnProfileDoesNotFallBackWhenAuthenticatedAccountIsMissing() {
        when(authenticatedUserProvider.currentUser())
                .thenReturn(new AuthenticatedUser(authenticatedId, false));
        when(userRepository.findById(authenticatedId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, userService::getOwnProfile);
        verify(userRepository).findById(authenticatedId);
    }

    @Test
    void updateOwnProfileChangesOnlyAuthenticatedAccountAndPreservesOmittedFields() {
        User user = user(authenticatedId);
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setName("Updated name");
        when(authenticatedUserProvider.currentUser())
                .thenReturn(new AuthenticatedUser(authenticatedId, false));
        when(userRepository.findById(authenticatedId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        OwnUserProfileResponse response = userService.updateOwnProfile(request);

        verify(user).setName("Updated name");
        verify(user, never()).setAvatarUrl(org.mockito.ArgumentMatchers.any());
        verify(user, never()).setBio(org.mockito.ArgumentMatchers.any());
        verify(userRepository).findById(authenticatedId);
        verify(userRepository).save(user);
        assertEquals(authenticatedId, response.id());
    }

    @Test
    void updateOwnProfileClearsOptionalFieldsOnlyWhenExplicitlyProvidedAsNull() {
        User user = user(authenticatedId);
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setAvatarUrl(null);
        request.setBio(null);
        when(authenticatedUserProvider.currentUser())
                .thenReturn(new AuthenticatedUser(authenticatedId, false));
        when(userRepository.findById(authenticatedId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.updateOwnProfile(request);

        verify(user).setAvatarUrl(null);
        verify(user).setBio(null);
        verify(userRepository).save(user);
    }

    @Test
    void updateOwnProfileRejectsEmptyPatchBeforeSaving() {
        when(authenticatedUserProvider.currentUser())
                .thenReturn(new AuthenticatedUser(authenticatedId, false));

        assertThrows(UserValidationException.class,
                () -> userService.updateOwnProfile(new UpdateUserProfileRequest()));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateOwnProfileRejectsInvalidNameWithoutSaving() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setName(" ");
        when(authenticatedUserProvider.currentUser())
                .thenReturn(new AuthenticatedUser(authenticatedId, false));

        assertThrows(UserValidationException.class, () -> userService.updateOwnProfile(request));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateOwnProfileRejectsNullAndOverlongNamesWithoutSaving() {
        UpdateUserProfileRequest nullName = new UpdateUserProfileRequest();
        nullName.setName(null);
        UpdateUserProfileRequest longName = new UpdateUserProfileRequest();
        longName.setName("x".repeat(151));
        when(authenticatedUserProvider.currentUser())
                .thenReturn(new AuthenticatedUser(authenticatedId, false));

        assertThrows(UserValidationException.class, () -> userService.updateOwnProfile(nullName));
        assertThrows(UserValidationException.class, () -> userService.updateOwnProfile(longName));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateOwnProfileRejectsUnknownAccountFieldsWithoutSaving() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setName("Updated name");
        request.setUnknownProperty("role", "admin");
        when(authenticatedUserProvider.currentUser())
                .thenReturn(new AuthenticatedUser(authenticatedId, false));

        assertThrows(UserValidationException.class, () -> userService.updateOwnProfile(request));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateOwnProfileRejectsClientSuppliedIdentityWithoutReadingOrSavingAnyAccount() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setName("Attacker");
        request.setUnknownProperty("id", UUID.randomUUID().toString());
        when(authenticatedUserProvider.currentUser())
                .thenReturn(new AuthenticatedUser(authenticatedId, false));

        assertThrows(UserValidationException.class, () -> userService.updateOwnProfile(request));

        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getPublicProfileMapsOnlyPublicProfileFields() {
        UUID targetId = UUID.randomUUID();
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(targetId);
        when(user.getName()).thenReturn("Member");
        when(user.getAvatarUrl()).thenReturn("https://example.com/avatar.png");
        when(user.getBio()).thenReturn("About me");
        when(userRepository.findById(targetId)).thenReturn(Optional.of(user));

        PublicUserProfileResponse response = userService.getPublicProfile(targetId);

        assertEquals(targetId, response.id());
        assertEquals("Member", response.name());
        assertEquals("https://example.com/avatar.png", response.avatarUrl());
        assertEquals("About me", response.bio());
        verify(userRepository).findById(targetId);
    }

    @Test
    void getPublicProfileReturnsNotFoundForMissingUser() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getPublicProfile(targetId));
    }

    private User user(UUID id) {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getName()).thenReturn("Member");
        when(user.getEmail()).thenReturn("member@example.com");
        when(user.getAvatarUrl()).thenReturn("https://example.com/avatar.png");
        when(user.getBio()).thenReturn("About me");
        return user;
    }
}
