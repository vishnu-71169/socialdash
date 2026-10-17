package com.socialdash.security;

/** Principal placed in the security context for an authenticated request. */
public record AuthUser(Long id, String email) {}
