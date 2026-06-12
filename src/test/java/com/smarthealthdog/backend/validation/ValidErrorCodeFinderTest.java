package com.smarthealthdog.backend.validation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

// Use MockitoExtension to enable Mockito annotations for JUnit 5
@SpringBootTest
@ActiveProfiles("test")
class ValidErrorCodeFinderTest {

    @Autowired
    private ValidErrorCodeFinder validErrorCodeFinder;

    // Mock DTOs to represent the objects that failed validation
    // These need to be actual classes/interfaces to allow for reflection (getClass().getSimpleName())
    private static class UserCreateRequest {}
    private static class OtherRequest {}

    @Test
    void findErrorCode_forUserCreateRequest_shouldReturnErrorCodeINVALID_INPUT() {
        // Arrange
        // 1. Mock the target object to be the 'UserCreateRequest' DTO
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getTarget()).thenReturn(new UserCreateRequest());

        // 2. Define the specific error code we expect from the dependency
        List<ErrorCode> expectedCodes = List.of(ErrorCode.INVALID_INPUT);

        // Act
        List<ErrorCode> actualCodes = validErrorCodeFinder.findErrorCode(exception);

        // Assert
        assertEquals(expectedCodes, actualCodes, "The returned error codes should match the expected codes.");
    }

    @Test
    void findErrorCode_forOtherRequest_shouldReturnErrorCodeINVALID_INPUT() {
        // Arrange
        // 1. Mock the target object to be a different DTO
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getTarget()).thenReturn(new OtherRequest());

        // 2. Define the specific error code we expect from the dependency
        List<ErrorCode> expectedCodes = List.of(ErrorCode.INVALID_INPUT);

        // Act
        List<ErrorCode> actualCodes = validErrorCodeFinder.findErrorCode(exception);

        // Assert
        assertEquals(expectedCodes, actualCodes, "The returned error codes should match the expected codes.");
    }

    @Test
    void findErrorCode_forNullException_shouldReturnErrorCodeINVALID_INPUT() {
        // Arrange
        MethodArgumentNotValidException exception = null;

        // Define the expected error code
        List<ErrorCode> expectedCodes = List.of(ErrorCode.INVALID_INPUT);

        // Act
        List<ErrorCode> actualCodes = validErrorCodeFinder.findErrorCode(exception);

        // Assert
        assertEquals(expectedCodes, actualCodes, "The returned error codes should match the expected codes.");
    }

    @Test
    void findErrorCode_forNullBindingResult_shouldReturnErrorCodeINVALID_INPUT() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(null);

        // Define the expected error code
        List<ErrorCode> expectedCodes = List.of(ErrorCode.INVALID_INPUT);

        // Act
        List<ErrorCode> actualCodes = validErrorCodeFinder.findErrorCode(exception);

        // Assert
        assertEquals(expectedCodes, actualCodes, "The returned error codes should match the expected codes.");
    }

    @Test 
    void findErrorCode_forUserCreateRequest_shouldReturnErrorCodeINVALID_EMAIL() {
        // Arrange
        // 1. Mock the target object to be the 'UserCreateRequest' DTO
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(
            List.of(new FieldError("objectName", "email", "message"))
        );
        when(bindingResult.getTarget()).thenReturn(new UserCreateRequest());

        // 2. Define the specific error code we expect from the dependency
        List<ErrorCode> expectedCodes = List.of(ErrorCode.INVALID_EMAIL);

        // Act
        List<ErrorCode> actualCodes = validErrorCodeFinder.findErrorCode(exception);

        // Assert
        assertEquals(expectedCodes, actualCodes, "The returned error codes should match the expected codes.");
    }

    @Test
    void findErrorCode_forUserCreateRequest_shouldReturnErrorCodeINVALID_PASSWORD() {
        // Arrange
        // 1. Mock the target object to be the 'UserCreateRequest' DTO
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(
            List.of(new FieldError("objectName", "password", "message"))
        );
        when(bindingResult.getTarget()).thenReturn(new UserCreateRequest());

        // 2. Define the specific error code we expect from the dependency
        List<ErrorCode> expectedCodes = List.of(ErrorCode.INVALID_PASSWORD);

        // Act
        List<ErrorCode> actualCodes = validErrorCodeFinder.findErrorCode(exception);

        // Assert
        assertEquals(expectedCodes, actualCodes, "The returned error codes should match the expected codes.");
    }

    @Test
    void findErrorCode_forUserCreateRequest_shouldReturnErrorCodeINVALID_NICKNAME() {
        // Arrange
        // 1. Mock the target object to be the 'UserCreateRequest' DTO
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(
            List.of(new FieldError("objectName", "nickname", "message"))
        );
        when(bindingResult.getTarget()).thenReturn(new UserCreateRequest());

        // 2. Define the specific error code we expect from the dependency
        List<ErrorCode> expectedCodes = List.of(ErrorCode.INVALID_NICKNAME);

        // Act
        List<ErrorCode> actualCodes = validErrorCodeFinder.findErrorCode(exception);

        // Assert
        assertEquals(expectedCodes, actualCodes, "The returned error codes should match the expected codes.");
    }

    // Return more than one error code
    @Test
    void findErrorCode_forUserCreateRequest_shouldReturnMultipleErrorCodes() {
        // Arrange
        // 1. Mock the target object to be the 'UserCreateRequest' DTO
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(
            List.of(
                new FieldError("objectName", "email", "message"),
                new FieldError("objectName", "password", "message"),
                new FieldError("objectName", "nickname", "message")
            )
        );
        when(bindingResult.getTarget()).thenReturn(new UserCreateRequest());

        // 2. Define the specific error code we expect from the dependency
        List<ErrorCode> expectedCodes = List.of(
            ErrorCode.INVALID_EMAIL,
            ErrorCode.INVALID_PASSWORD,
            ErrorCode.INVALID_NICKNAME
        );

        // Act
        List<ErrorCode> actualCodes = validErrorCodeFinder.findErrorCode(exception);

        // Assert
        assertEquals(expectedCodes, actualCodes, "The returned error codes should match the expected codes.");
    }
}
