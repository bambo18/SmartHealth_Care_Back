package com.smarthealthdog.backend.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PasswordValidatorUT {
    // The class under test
    private PasswordValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordValidator();
    }

    // Valid passwords
    @Test
    void testValidPasswords() {
        assertTrue(validator.isValid("StrongPass1!"));
        assertTrue(validator.isValid("A1b2C3d4$"));
        assertTrue(validator.isValid("P@ssw0rd2024"));
    }

    // Invalid passwords
    @Test
    void testInvalidPasswords() {
        assertFalse(validator.isValid(null)); // Null
        assertFalse(validator.isValid("")); // Empty
        assertFalse(validator.isValid("short1!")); // Too short
        assertFalse(validator.isValid("alllowercase1!")); // No uppercase
        assertFalse(validator.isValid("ALLUPPERCASE1!")); // No lowercase
        assertFalse(validator.isValid("NoNumbers!")); // No digit
        assertFalse(validator.isValid("NoSpecialChar1")); // No special character
        assertFalse(validator.isValid("X".repeat(257) + "1a!")); // Too long
        assertFalse(validator.isValid("InvalidChar1! ")); // Space character
    }
}
