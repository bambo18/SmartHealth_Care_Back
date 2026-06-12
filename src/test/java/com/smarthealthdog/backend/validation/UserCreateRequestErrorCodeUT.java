package com.smarthealthdog.backend.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;


public class UserCreateRequestErrorCodeUT {

    private UserCreateRequestErrorCode errorCodeResolver;

    // Mocking the required components
    private MethodArgumentNotValidException mockException;
    private BindingResult mockBindingResult;

    @BeforeEach
    void setUp() {
        // 1. Initialize the class to be tested
        errorCodeResolver = new UserCreateRequestErrorCode();

        // 2. Mock the Spring objects
        mockException = mock(MethodArgumentNotValidException.class);
        mockBindingResult = mock(BindingResult.class);

        // 3. Chain the mock calls
        when(mockException.getBindingResult()).thenReturn(mockBindingResult);
    }

    @Test
    void getErrorCode_ShouldReturnSpecificErrors_ForMultipleFields() {
        // ARRANGE: Create mock FieldErrors for multiple fields
        FieldError nicknameError = new FieldError("objectName", "nickname", "message");
        FieldError passwordError = new FieldError("objectName", "password", "message");
        
        List<FieldError> fieldErrors = Arrays.asList(nicknameError, passwordError);

        // STUB: Tell the mock BindingResult to return these errors
        when(mockBindingResult.getFieldErrors()).thenReturn(fieldErrors);

        // ACT
        List<ErrorCode> result = errorCodeResolver.getErrorCode(mockException);

        // ASSERT
        assertEquals(2, result.size(), "Should return an error for each unique field.");
        assertTrue(result.contains(ErrorCode.INVALID_NICKNAME));
        assertTrue(result.contains(ErrorCode.INVALID_PASSWORD));
        
        // Ensure no duplicates and no irrelevant errors
        assertTrue(result.stream().distinct().count() == result.size());
    }

    @Test
    void getErrorCode_ShouldReturnUniqueErrors_WhenFieldHasMultipleViolations() {
        // ARRANGE: Create mock FieldErrors where one field has two errors (e.g., @NotNull and @Size)
        FieldError emailError1 = new FieldError("objectName", "email", "message1");
        FieldError emailError2 = new FieldError("objectName", "email", "message2");
        
        List<FieldError> fieldErrors = Arrays.asList(emailError1, emailError2);

        // STUB: Tell the mock BindingResult to return these errors
        when(mockBindingResult.getFieldErrors()).thenReturn(fieldErrors);

        // ACT
        List<ErrorCode> result = errorCodeResolver.getErrorCode(mockException);

        // ASSERT
        assertEquals(1, result.size(), "Should return only one unique error for the 'email' field.");
        assertEquals(ErrorCode.INVALID_EMAIL, result.get(0));
    }

    @Test
    void getErrorCode_ShouldReturnINVALID_INPUT_ForUnknownField() {
        // ARRANGE: Create a mock FieldError for a field not handled in the switch statement
        FieldError unknownError = new FieldError("objectName", "someOtherField", "message");
        
        List<FieldError> fieldErrors = Collections.singletonList(unknownError);

        // STUB
        when(mockBindingResult.getFieldErrors()).thenReturn(fieldErrors);

        // ACT
        List<ErrorCode> result = errorCodeResolver.getErrorCode(mockException);

        // ASSERT
        assertEquals(1, result.size(), "Should return exactly one error.");
        assertEquals(ErrorCode.INVALID_INPUT, result.get(0), "Should default to INVALID_INPUT.");
    }

    @Test
    void getErrorCode_ShouldReturnINVALID_INPUT_WhenNoFieldErrorsExist() {
        // ARRANGE: Mock that there are no FieldErrors (though this is rare for MAVNE)
        when(mockBindingResult.getFieldErrors()).thenReturn(Collections.emptyList());

        // ACT
        List<ErrorCode> result = errorCodeResolver.getErrorCode(mockException);

        // ASSERT
        assertEquals(1, result.size(), "Should return exactly one error.");
        assertEquals(
            ErrorCode.INVALID_INPUT, 
            result.get(0), 
            "Should return INVALID_INPUT when the field error list is empty."
        );
    }

    @Test
    void getErrorCode_ShouldReturnINVALID_INPUT_WhenExceptionIsNull() {
        // ACT
        List<ErrorCode> result = errorCodeResolver.getErrorCode(null);

        // ASSERT
        assertEquals(1, result.size(), "Should return exactly one error.");
        assertEquals(
            ErrorCode.INVALID_INPUT, 
            result.get(0), 
            "Should return INVALID_INPUT when exception is null."
        );
    }

    @Test
    void getErrorCode_ShouldReturnINVALID_INPUT_WhenBindingResultIsNull() {
        // ARRANGE: Mock that the BindingResult is null
        when(mockException.getBindingResult()).thenReturn(null);

        // ACT
        List<ErrorCode> result = errorCodeResolver.getErrorCode(mockException);

        // ASSERT
        assertEquals(1, result.size(), "Should return exactly one error.");
        assertEquals(
            ErrorCode.INVALID_INPUT, 
            result.get(0), 
            "Should return INVALID_INPUT when BindingResult is null."
        );
    }
}