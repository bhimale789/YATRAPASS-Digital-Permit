package com.example.touristpermit.user;

/**
 * System roles. Granted authorities are {@code ROLE_<name>}. New roles can be added here
 * together with a migration updating {@code ck_users_role}.
 */
public enum Role {
    TOURIST,
    ADMIN,
    OFFICER;

    public String authority() {
        return "ROLE_" + name();
    }
}
