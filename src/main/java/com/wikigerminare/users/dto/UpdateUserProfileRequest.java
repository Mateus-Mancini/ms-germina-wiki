package com.wikigerminare.users.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashSet;
import java.util.Set;

public class UpdateUserProfileRequest {

    @Size(max = 150, message = "name must have at most 150 characters")
    private String name;
    private boolean nameProvided;

    private String avatarUrl;
    private boolean avatarUrlProvided;

    private String bio;
    private boolean bioProvided;

    private final Set<String> unknownProperties = new LinkedHashSet<>();

    public UpdateUserProfileRequest() {
    }

    public String getName() {
        return name;
    }

    @JsonSetter("name")
    public void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    public boolean isNameProvided() {
        return nameProvided;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    @JsonSetter(value = "avatarUrl", nulls = Nulls.SET)
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        this.avatarUrlProvided = true;
    }

    public boolean isAvatarUrlProvided() {
        return avatarUrlProvided;
    }

    public String getBio() {
        return bio;
    }

    @JsonSetter(value = "bio", nulls = Nulls.SET)
    public void setBio(String bio) {
        this.bio = bio;
        this.bioProvided = true;
    }

    public boolean isBioProvided() {
        return bioProvided;
    }

    @JsonAnySetter
    public void setUnknownProperty(String property, Object ignoredValue) {
        unknownProperties.add(property);
    }

    @JsonIgnore
    public Set<String> getUnknownProperties() {
        return Set.copyOf(unknownProperties);
    }

    public boolean isAnyFieldProvided() {
        return nameProvided || avatarUrlProvided || bioProvided;
    }

    @AssertTrue(message = "name must not be blank")
    public boolean isNameValid() {
        return !nameProvided || (name != null && !name.isBlank());
    }
}
