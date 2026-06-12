package com.smarthealthdog.backend.utils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@SpringBootTest
@ActiveProfiles("test")
class TokenGeneratorTest {

    @Autowired
    private TokenGenerator tokenGenerator;

    // The set of allowed characters as defined in the method
    private static final String ALLOWED_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int EXPECTED_LENGTH = 6;
    
    // Regular expression to ensure the token only contains the allowed characters
    private static final Pattern VALID_TOKEN_PATTERN = Pattern.compile("^[a-zA-Z0-9]{" + EXPECTED_LENGTH + "}$");

    @Test
    void generateEmailVerificationCode_shouldReturnCodeOfCorrectLength() {
        // ACT
        String code = tokenGenerator.generateEmailVerificationCode();

        // ASSERT
        assertNotNull(code, "The generated code should not be null.");
        assertEquals(EXPECTED_LENGTH, code.length(), "The generated code must be exactly 6 characters long.");
    }

    @Test
    void generateEmailVerificationCode_shouldOnlyContainAllowedCharacters() {
        // ACT
        String code = tokenGenerator.generateEmailVerificationCode();

        // ASSERT
        // 1. Check using the compiled Pattern for efficiency and robustness
        assertTrue(VALID_TOKEN_PATTERN.matcher(code).matches(), 
                   "The code should only contain alphanumeric characters (A-Z, a-z, 0-9). Code: " + code);
        
        // 2. Secondary check to ensure it doesn't contain any other characters
        for (char c : code.toCharArray()) {
            assertTrue(ALLOWED_CHARACTERS.indexOf(c) != -1, 
                       "The character '" + c + "' is not in the allowed set.");
        }
    }
    
    @Test
    void generateEmailVerificationCode_shouldGenerateDifferentCodes() {
        // ARRANGE
        int testRuns = 1000;
        Set<String> generatedCodes = new HashSet<>();
        
        // ACT
        for (int i = 0; i < testRuns; i++) {
            generatedCodes.add(tokenGenerator.generateEmailVerificationCode());
        }

        // ASSERT
        // This is a statistical check. With a 6-character alphanumeric code (62^6 possibilities), 
        // 1000 runs should almost certainly yield 1000 unique codes.
        // We ensure that a significant portion are unique to demonstrate randomness.
        assertTrue(generatedCodes.size() > (testRuns * 0.99), 
                   "The codes should be mostly unique, indicating randomness. Generated unique codes: " + generatedCodes.size());
        assertEquals(testRuns, generatedCodes.size(), 
                     "In 1000 runs, all codes should ideally be unique (or extremely close to it).");
    }
}