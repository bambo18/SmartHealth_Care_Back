package com.smarthealthdog.backend.domain;

public enum UserActionType {
    // Actions related to a user's account being active or inactive
    DEACTIVATED,
    REACTIVATED,

    // Actions related to a user being banned or unbanned
    BANNED,
    UNBANNED,

    // Actions related to a user deleting or restoring their account
    DELETED,
    RESTORED,

    // Actions related to email verification and password changes
    EMAIL_VERIFIED,
    PASSWORD_CHANGED
}