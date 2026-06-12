package com.smarthealthdog.backend.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

// Use MockitoExtension to enable Mockito annotations for JUnit 5
@ExtendWith(MockitoExtension.class)
class ValidErrorCodeFinderUT {

    // 1. Class Under Test (The class we are testing)
    @InjectMocks
    private ValidErrorCodeFinder validErrorCodeFinder;

    // 2. Mocked Dependency
    @Mock
    private UserCreateRequestErrorCode userCreateRequestErrorCode;

    // 3. Mocked Exception/Result components
    @Mock
    private MethodArgumentNotValidException exception;

    @Mock
    private BindingResult bindingResult;

    // Mock DTOs to represent the objects that failed validation
    // These need to be actual classes/interfaces to allow for reflection (getClass().getSimpleName())
    private static class UserCreateRequest {}
    private static class OtherRequest {}


    @BeforeEach
    void setUp() {
        // Set up the exception to return the mocked BindingResult for all tests
        when(exception.getBindingResult()).thenReturn(bindingResult);
    }

    @Test
    void findErrorCode_forUserCreateRequest_shouldReturnSpecificErrorCode() {
        // Arrange
        // 1. Mock the target object to be the 'UserCreateRequest' DTO
        when(bindingResult.getTarget()).thenReturn(new UserCreateRequest());

        // 2. Define the specific error code we expect from the dependency
        List<ErrorCode> expectedCodes = List.of(ErrorCode.INVALID_EMAIL, ErrorCode.INVALID_PASSWORD);
        when(userCreateRequestErrorCode.getErrorCode(exception)).thenReturn(expectedCodes);

        // Act
        List<ErrorCode> actualCodes = validErrorCodeFinder.findErrorCode(exception);

        // Assert
        // 1. Verify that the correct codes were returned
        assertEquals(expectedCodes, actualCodes, "Should return the codes resolved by UserCreateRequestErrorCode.");

        // 2. Verify that the dependency method was actually called
        verify(userCreateRequestErrorCode, times(1)).getErrorCode(exception);
    }

    @Test
    void findErrorCode_forUnknownDto_shouldReturnInvalidInputDefault() {
        // Arrange
        // 1. Mock the target object to be an 'OtherRequest' (which is not handled in the switch case)
        when(bindingResult.getTarget()).thenReturn(new OtherRequest());

        // 2. Define the expected default error code
        List<ErrorCode> expectedCodes = List.of(ErrorCode.INVALID_INPUT);

        // Act
        List<ErrorCode> actualCodes = validErrorCodeFinder.findErrorCode(exception);

        // Assert
        // 1. Verify that the default code was returned
        assertEquals(expectedCodes, actualCodes, "Should return the default INVALID_INPUT code.");

        // 2. Verify that the dependency method was *not* called for the default case
        verify(userCreateRequestErrorCode, never()).getErrorCode(exception);
    }

    @Test
    void findErrorCode_whenBindingResultTargetIsNull_shouldReturnInvalidInputDefault() {
        // Arrange
        // Mock the target to be null (though Spring usually prevents this in a real scenario)
        when(bindingResult.getTarget()).thenReturn(null);

        // Act
        List<ErrorCode> actualCodes = validErrorCodeFinder.findErrorCode(exception);

        List<ErrorCode> expectedCodes = List.of(ErrorCode.INVALID_INPUT);
        assertEquals(expectedCodes, actualCodes, "Should return the default INVALID_INPUT code.");
    }
}
