package com.aishop.security;

import com.aishop.entity.Role;

/** The authenticated principal, injected into controllers with {@code @AuthenticationPrincipal}. */
public record AuthUser(Long id, String email, Role role) {
}
