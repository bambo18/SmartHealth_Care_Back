package com.smarthealthdog.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.SecureRandom;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
public class TokenGeneratorUT {

    @Autowired
    private TokenGenerator tokenGenerator;

    @MockitoBean
    private SecureRandom mockSecureRandom;

    // Constants to match the TokenGenerator
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 6;

    @Test
    void generateEmailVerificationCode_shouldReturnFixedCodeBasedOnMock() {
        // ARRANGE: Set up the mock to return predictable indices.
        // Index 0 = A, Index 1 = B, Index 2 = C, Index 3 = D, Index 4 = E, Index 5 = F
        Mockito.when(mockSecureRandom.nextInt(CHARACTERS.length()))
               .thenReturn(0)
               .thenReturn(1)
               .thenReturn(2)
               .thenReturn(3)
               .thenReturn(4)
               .thenReturn(5);

        // ACT
        String code = tokenGenerator.generateEmailVerificationCode();

        // ASSERT
        assertEquals("ABCDEF", code, 
                     "The code should exactly match the characters corresponding to the mocked indices.");
        assertEquals(CODE_LENGTH, code.length(), 
                     "The generated code must be the correct length.");
        
        // Verify that the dependency was called the correct number of times
        Mockito.verify(mockSecureRandom, Mockito.times(CODE_LENGTH))
               .nextInt(CHARACTERS.length());
    }

    @Test
    void generateEmailVerificationCode_shouldHandleDifferentIndices() {
        // ARRANGE: Set up the mock for a different sequence (e.g., all digits)
        int digitsStart = CHARACTERS.indexOf('0'); // Should be 52
        
        Mockito.when(mockSecureRandom.nextInt(CHARACTERS.length()))
               .thenReturn(digitsStart)      // '0'
               .thenReturn(digitsStart + 1)  // '1'
               .thenReturn(digitsStart + 2)  // '2'
               .thenReturn(digitsStart + 3)  // '3'
               .thenReturn(digitsStart + 4)  // '4'
               .thenReturn(digitsStart + 5);  // '5'

        // ACT
        String code = tokenGenerator.generateEmailVerificationCode();

        // ASSERT
        assertEquals("012345", code);
    }
    
    @Test
    void generateEmailVerificationCode_shouldHandleCharacterSetLengthCorrectly() {
        // ARRANGE
        // Check boundary condition: the last index in the character set
        int lastIndex = CHARACTERS.length() - 1; // 61 (for character '9')
        
        Mockito.when(mockSecureRandom.nextInt(CHARACTERS.length()))
               .thenReturn(lastIndex); // Return '9' for all 6 characters

        // ACT
        String code = tokenGenerator.generateEmailVerificationCode();

        // ASSERT
        assertEquals("999999", code);
        
        // Verify that the correct bound was passed to nextInt
        Mockito.verify(mockSecureRandom, Mockito.atLeast(1)).nextInt(CHARACTERS.length());
    }
    
}
