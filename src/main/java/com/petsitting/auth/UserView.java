package com.petsitting.auth;

public record UserView(long id, String username, String displayName, String role, boolean enabled) {}
