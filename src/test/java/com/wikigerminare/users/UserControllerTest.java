package com.wikigerminare.users;

import com.wikigerminare.users.dto.OwnUserProfileResponse;
import com.wikigerminare.users.dto.PublicUserProfileResponse;
import com.wikigerminare.users.dto.UpdateUserProfileRequest;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        when(userService.updateOwnProfile(any())).thenAnswer(invocation -> {
            UpdateUserProfileRequest request = invocation.getArgument(0);
            if (!request.getUnknownProperties().isEmpty() || !request.isAnyFieldProvided()) {
                throw new UserValidationException("Invalid profile update");
            }
            return new OwnUserProfileResponse(
                    UUID.randomUUID(), "Member", "member@example.com", null, null);
        });
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setControllerAdvice(new UserExceptionHandler())
                .build();
    }

    @Test
    void getOwnProfileReturnsOnlyPrivateProfileFields() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getOwnProfile()).thenReturn(new OwnUserProfileResponse(
                userId, "Member", "member@example.com", null, "About me"));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("Member"))
                .andExpect(jsonPath("$.email").value("member@example.com"))
                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.bio").value("About me"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void patchOwnProfilePreservesExplicitNullPresence() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/users/me")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":null}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateUserProfileRequest> request =
                ArgumentCaptor.forClass(UpdateUserProfileRequest.class);
        verify(userService).updateOwnProfile(request.capture());
        org.junit.jupiter.api.Assertions.assertTrue(request.getValue().isAvatarUrlProvided());
        org.junit.jupiter.api.Assertions.assertNull(request.getValue().getAvatarUrl());
        org.junit.jupiter.api.Assertions.assertFalse(request.getValue().isBioProvided());
    }

    @Test
    void patchOwnProfileRejectsEmptyOrUnknownAccountFields() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/users/me")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        for (String prohibitedField : new String[] {
                "\"id\":\"" + UUID.randomUUID() + "\"",
                "\"email\":\"attacker@example.com\"",
                "\"role\":\"admin\"",
                "\"password_hash\":\"secret\"",
                "\"passwordHash\":\"secret\""
        }) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .patch("/api/users/me")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Changed\"," + prohibitedField + "}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void patchOwnProfileRejectsInvalidNameAtRequestBoundary() throws Exception {
        for (String invalidName : new String[] {
                "\"   \"",
                "null",
                "\"" + "x".repeat(151) + "\""
        }) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .patch("/api/users/me")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"name\":" + invalidName + "}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void getPublicProfileReturnsOnlyPublicFieldsIncludingNullOptionals() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getPublicProfile(userId)).thenReturn(
                new PublicUserProfileResponse(userId, "Member", null, null));

        mockMvc.perform(get("/api/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("Member"))
                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.bio").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void getPublicProfileMapsInvalidUuidToBadRequestAndMissingUserToNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getPublicProfile(userId))
                .thenThrow(new UserNotFoundException(userId));

        mockMvc.perform(get("/api/users/not-a-uuid"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/users/{userId}", userId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOwnProfileReturnsNotFoundWhenAuthenticatedAccountIsMissing() throws Exception {
        when(userService.getOwnProfile()).thenThrow(
                new UserNotFoundException(UUID.randomUUID()));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }
}
