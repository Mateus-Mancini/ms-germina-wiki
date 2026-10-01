package com.wikigerminare.users;

public enum UserRole {
    ADMIN("admin"),
    MEMBER("member");

    private final String databaseValue;

    UserRole(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public String authority() {
        return this == ADMIN ? "ROLE_ADMIN" : "ROLE_MEMBER";
    }

    public static UserRole fromDatabaseValue(String value) {
        for (UserRole role : values()) {
            if (role.databaseValue.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown user role");
    }
}
