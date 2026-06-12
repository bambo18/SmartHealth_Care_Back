package com.smarthealthdog.backend.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NicknameValidatorUT {
        // The class under test
    private NicknameValidator validator;

    @BeforeEach
    void setUp() {
        validator = new NicknameValidator();
    }

    // Valid nicknames
    @Test
    void testValidNicknames() {
        assertTrue(validator.isValid("user123"));
        assertTrue(validator.isValid("User_Name"));
        assertTrue(validator.isValid("user-name"));
        assertTrue(validator.isValid("A".repeat(3))); // Minimum length
        assertTrue(validator.isValid("B".repeat(128))); // Maximum length
        assertTrue(validator.isValid("한글닉네임")); // Non-Latin character
        assertTrue(validator.isValid("ユーザー")); // Non-Latin character
        assertTrue(validator.isValid("😊👍")); // Emoji
    }

    // Invalid nicknames
    @Test
    void testInvalidNicknames() {
        assertFalse(validator.isValid(null)); // Null
        assertFalse(validator.isValid("")); // Empty
        assertFalse(validator.isValid("AB")); // Too short
        assertFalse(validator.isValid("C".repeat(129))); // Too long
    }
}
