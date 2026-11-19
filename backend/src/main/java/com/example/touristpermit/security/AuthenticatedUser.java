package com.example.touristpermit.security;

import com.example.touristpermit.user.Role;

import java.security.Principal;

/**
 * Security principal for an authenticated request. Built from the database on every
 * request (never from client-supplied role data).
 */
public record AuthenticatedUser(Long id, String email, Role role) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(id);
    }

    public boolean hasRole(Role r) {
        return role == r;
    }
}
